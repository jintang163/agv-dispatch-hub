package com.agv.dispatch.core.service;

import com.agv.dispatch.common.dto.AgvRegisterDTO;
import com.agv.dispatch.common.dto.AgvStatusReportDTO;
import com.agv.dispatch.common.dto.AgvUpdateDTO;
import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.enums.AgvStatus;
import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.common.enums.TaskType;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.repository.TaskRepository;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.agv.dispatch.common.constant.RedisKeyConstant.AGV_STATUS_PREFIX;
import static com.agv.dispatch.common.constant.RedisKeyConstant.TASK_QUEUE_KEY;

/**
 * AGV状态管理服务
 * 负责AGV的注册、维护、实时状态上报、低电量管理、心跳检测等功能
 *
 * 主要功能：
 * - AGV注册与维护：车辆编号、型号、载重能力、允许的任务类型
 * - 实时状态获取：位置（地图坐标/站点）、速度、方向、电量、运行状态
 * - 电量管理：低电量告警、自动触发充电任务或返回充电桩
 * - 心跳检测：超时未上报心跳自动标记为离线
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgvStatusService {

    private final AgvRepository agvRepository;
    private final TaskRepository taskRepository;
    private final TaskDispatchService taskDispatchService;
    private final StringRedisTemplate redisTemplate;

    private static final double LOW_BATTERY_THRESHOLD = 20.0;
    private static final double CRITICAL_BATTERY_THRESHOLD = 10.0;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 60;

    private final Map<String, LocalDateTime> lastHeartbeatMap = new ConcurrentHashMap<>();

    /**
     * 注册新的AGV
     * 校验AGV编号唯一性，初始化AGV状态为空闲，电量默认100%
     *
     * @param dto AGV注册信息（编号、名称、型号、载重、IP、初始坐标、允许的任务类型）
     * @return 注册后的AGV实体
     * @throws IllegalArgumentException AGV编号已存在时抛出
     */
    @Transactional
    public Agv registerAgv(AgvRegisterDTO dto) {
        if (agvRepository.existsByAgvNo(dto.getAgvNo())) {
            throw new IllegalArgumentException("AGV编号已存在: " + dto.getAgvNo());
        }

        Agv agv = new Agv();
        agv.setId(UUID.randomUUID().toString().replace("-", ""));
        agv.setAgvNo(dto.getAgvNo());
        agv.setName(dto.getName() != null ? dto.getName() : dto.getAgvNo());
        agv.setModel(dto.getModel());
        agv.setMaxLoad(dto.getMaxLoad());
        agv.setIpAddress(dto.getIpAddress());
        agv.setStatus(AgvStatus.IDLE);
        agv.setBatteryLevel(100.0);
        agv.setXCoord(dto.getXCoord() != null ? dto.getXCoord() : 0.0);
        agv.setYCoord(dto.getYCoord() != null ? dto.getYCoord() : 0.0);
        agv.setAngle(0.0);
        agv.setSpeed(0.0);
        agv.setAllowedTaskTypes(dto.getAllowedTaskTypes());
        agv.setLastHeartbeat(LocalDateTime.now());

        Agv saved = agvRepository.save(agv);
        cacheAgvStatus(saved);
        log.info("AGV注册成功: {}, 型号: {}, 载重: {}kg", saved.getAgvNo(), saved.getModel(), saved.getMaxLoad());
        return saved;
    }

    /**
     * 更新AGV信息
     * 支持更新名称、型号、载重、IP、状态、电量、坐标、角度、速度、位置、允许的任务类型、故障信息等
     *
     * @param agvId AGV ID
     * @param dto AGV更新信息
     * @return 更新后的AGV实体
     * @throws IllegalArgumentException AGV不存在时抛出
     */
    @Transactional
    public Agv updateAgv(String agvId, AgvUpdateDTO dto) {
        Agv agv = agvRepository.findById(agvId)
                .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));

        if (dto.getName() != null) {
            agv.setName(dto.getName());
        }
        if (dto.getModel() != null) {
            agv.setModel(dto.getModel());
        }
        if (dto.getMaxLoad() != null) {
            agv.setMaxLoad(dto.getMaxLoad());
        }
        if (dto.getIpAddress() != null) {
            agv.setIpAddress(dto.getIpAddress());
        }
        if (dto.getStatus() != null) {
            agv.setStatus(dto.getStatus());
        }
        if (dto.getBatteryLevel() != null) {
            agv.setBatteryLevel(dto.getBatteryLevel());
        }
        if (dto.getXCoord() != null) {
            agv.setXCoord(dto.getXCoord());
        }
        if (dto.getYCoord() != null) {
            agv.setYCoord(dto.getYCoord());
        }
        if (dto.getAngle() != null) {
            agv.setAngle(dto.getAngle());
        }
        if (dto.getSpeed() != null) {
            agv.setSpeed(dto.getSpeed());
        }
        if (dto.getCurrentPosition() != null) {
            agv.setCurrentPosition(dto.getCurrentPosition());
        }
        if (dto.getAllowedTaskTypes() != null) {
            agv.setAllowedTaskTypes(dto.getAllowedTaskTypes());
        }
        if (dto.getFaultCode() != null) {
            agv.setFaultCode(dto.getFaultCode());
        }
        if (dto.getFaultMessage() != null) {
            agv.setFaultMessage(dto.getFaultMessage());
        }

        Agv saved = agvRepository.save(agv);
        cacheAgvStatus(saved);
        log.info("AGV信息已更新: {}", saved.getAgvNo());
        return saved;
    }

    /**
     * 删除AGV
     * 如果AGV正在执行任务，会先将任务重新分配，然后删除AGV记录和缓存
     *
     * @param agvId AGV ID
     * @throws IllegalArgumentException AGV不存在时抛出
     */
    @Transactional
    public void deleteAgv(String agvId) {
        Agv agv = agvRepository.findById(agvId)
                .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));

        if (agv.getStatus() == AgvStatus.WORKING || agv.getStatus() == AgvStatus.PAUSED) {
            if (agv.getCurrentTaskId() != null) {
                taskDispatchService.reassignTask(agv.getCurrentTaskId(), null, "AGV被删除", "system");
            }
        }

        redisTemplate.delete(AGV_STATUS_PREFIX + agvId);
        lastHeartbeatMap.remove(agvId);
        agvRepository.delete(agv);
        log.info("AGV已删除: {}", agv.getAgvNo());
    }

    /**
     * AGV状态上报
     * 接收AGV实时上报的状态信息，包括位置、电量、速度、角度、状态等
     * 上报后自动检查低电量并触发充电，同时检查心跳超时
     *
     * @param dto AGV状态上报信息
     * @return 更新后的AGV实体
     * @throws IllegalArgumentException AGV未注册时抛出
     */
    @Transactional
    public Agv reportStatus(AgvStatusReportDTO dto) {
        Agv agv = agvRepository.findByAgvNo(dto.getAgvNo())
                .orElseThrow(() -> new IllegalArgumentException("AGV未注册: " + dto.getAgvNo()));

        if (dto.getBatteryLevel() != null) {
            agv.setBatteryLevel(dto.getBatteryLevel());
        }
        if (dto.getXCoord() != null) {
            agv.setXCoord(dto.getXCoord());
        }
        if (dto.getYCoord() != null) {
            agv.setYCoord(dto.getYCoord());
        }
        if (dto.getAngle() != null) {
            agv.setAngle(dto.getAngle());
        }
        if (dto.getSpeed() != null) {
            agv.setSpeed(dto.getSpeed());
        }
        if (dto.getCurrentPosition() != null) {
            agv.setCurrentPosition(dto.getCurrentPosition());
        }
        if (dto.getStatus() != null) {
            try {
                agv.setStatus(AgvStatus.valueOf(dto.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("无效的AGV状态: {}, 使用当前状态", dto.getStatus());
            }
        }
        if (dto.getFaultCode() != null) {
            agv.setFaultCode(dto.getFaultCode());
        }
        if (dto.getFaultMessage() != null) {
            agv.setFaultMessage(dto.getFaultMessage());
        }

        agv.setLastHeartbeat(LocalDateTime.now());
        lastHeartbeatMap.put(agv.getId(), agv.getLastHeartbeat());

        Agv saved = agvRepository.save(agv);
        cacheAgvStatus(saved);

        checkLowBatteryAndHandle(saved);
        checkHeartbeatTimeout();

        return saved;
    }

    /**
     * 获取所有AGV列表
     *
     * @return AGV列表
     */
    public List<Agv> getAllAgvs() {
        return agvRepository.findAll();
    }

    /**
     * 根据状态获取AGV列表
     *
     * @param status AGV状态
     * @return 对应状态的AGV列表
     */
    public List<Agv> getAgvsByStatus(AgvStatus status) {
        return agvRepository.findByStatus(status);
    }

    /**
     * 根据ID获取AGV详情
     * 优先从Redis缓存获取，缓存不存在时查询数据库并更新缓存
     *
     * @param agvId AGV ID
     * @return AGV实体
     * @throws IllegalArgumentException AGV不存在时抛出
     */
    public Agv getAgvById(String agvId) {
        String cacheKey = AGV_STATUS_PREFIX + agvId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            try {
                return JSON.parseObject(cached, Agv.class);
            } catch (Exception e) {
                log.warn("解析AGV缓存失败: {}", e.getMessage());
            }
        }

        return agvRepository.findById(agvId)
                .map(agv -> {
                    cacheAgvStatus(agv);
                    return agv;
                })
                .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));
    }

    /**
     * 检查低电量并处理
     * 电量低于20%且空闲时自动触发充电
     * 电量低于10%时紧急触发充电（无论是否在执行任务）
     *
     * @param agv AGV实体
     */
    private void checkLowBatteryAndHandle(Agv agv) {
        if (agv.getStatus() == AgvStatus.CHARGING || agv.getStatus() == AgvStatus.FAULT) {
            return;
        }

        if (agv.getBatteryLevel() == null) {
            return;
        }

        if (agv.getBatteryLevel() <= CRITICAL_BATTERY_THRESHOLD) {
            log.warn("AGV电量严重不足: {}, 电量: {}%, 立即触发充电", agv.getAgvNo(), agv.getBatteryLevel());
            triggerAutoCharging(agv, true);
        } else if (agv.getBatteryLevel() <= LOW_BATTERY_THRESHOLD && agv.getStatus() == AgvStatus.IDLE) {
            log.info("AGV电量低: {}, 电量: {}%, 自动返回充电", agv.getAgvNo(), agv.getBatteryLevel());
            triggerAutoCharging(agv, false);
        }
    }

    /**
     * 触发自动充电任务
     * 1. 如果AGV正在执行任务，先将任务重新分配
     * 2. 创建充电任务并加入调度队列
     * 3. 更新AGV状态为充电中
     *
     * @param agv 需要充电的AGV
     * @param isEmergency 是否为紧急充电（电量严重不足时）
     */
    @Transactional
    public void triggerAutoCharging(Agv agv, boolean isEmergency) {
        if (agv.getStatus() == AgvStatus.CHARGING) {
            return;
        }

        if (agv.getCurrentTaskId() != null) {
            Task currentTask = taskRepository.findById(agv.getCurrentTaskId()).orElse(null);
            if (currentTask != null && currentTask.getStatus() != TaskStatus.COMPLETED
                    && currentTask.getStatus() != TaskStatus.CANCELLED) {
                String reason = isEmergency ? "AGV电量严重不足，紧急充电" : "AGV电量低，自动充电";
                taskDispatchService.reassignTask(agv.getCurrentTaskId(), null, reason, "system");
            }
        }

        Task chargingTask = new Task();
        chargingTask.setId(UUID.randomUUID().toString().replace("-", ""));
        chargingTask.setTaskNo("CHG-" + System.currentTimeMillis());
        chargingTask.setTaskType(TaskType.CHARGING);
        chargingTask.setPriority(isEmergency ? TaskPriority.HIGH : TaskPriority.MEDIUM);
        chargingTask.setStatus(TaskStatus.PENDING);
        chargingTask.setAgvId(agv.getId());
        chargingTask.setStartPoint(agv.getCurrentPosition() != null ? agv.getCurrentPosition() : "START");
        chargingTask.setEndPoint("CHARGING_STATION_01");
        chargingTask.setDeadline(LocalDateTime.now().plusHours(1));
        chargingTask.setRemark(isEmergency ? "紧急充电任务" : "自动充电任务");

        Task savedTask = taskRepository.save(chargingTask);

        agv.setStatus(AgvStatus.CHARGING);
        agv.setCurrentTaskId(savedTask.getId());
        agvRepository.save(agv);
        cacheAgvStatus(agv);

        double score = isEmergency ? 1000 : 500;
        redisTemplate.opsForZSet().add(TASK_QUEUE_KEY, savedTask.getId(), score);

        log.info("已创建自动充电任务: {}, AGV: {}, 紧急: {}", savedTask.getTaskNo(), agv.getAgvNo(), isEmergency);
    }

    /**
     * 检查心跳超时
     * 遍历所有在线状态的AGV，检查最后心跳时间
     * 超过60秒未上报心跳的AGV标记为离线，并重新分配其任务
     */
    @Transactional
    public void checkHeartbeatTimeout() {
        LocalDateTime now = LocalDateTime.now();
        List<Agv> onlineAgvs = agvRepository.findByStatusIn(List.of(
                AgvStatus.IDLE, AgvStatus.WORKING, AgvStatus.PAUSED, AgvStatus.CHARGING
        ));

        for (Agv agv : onlineAgvs) {
            LocalDateTime lastHeartbeat = lastHeartbeatMap.get(agv.getId());
            if (lastHeartbeat == null) {
                lastHeartbeat = agv.getLastHeartbeat();
            }

            if (lastHeartbeat == null ||
                    java.time.Duration.between(lastHeartbeat, now).getSeconds() > HEARTBEAT_TIMEOUT_SECONDS) {
                log.warn("AGV心跳超时，设置为离线: {}", agv.getAgvNo());
                agv.setStatus(AgvStatus.OFFLINE);
                agv.setFaultCode("HEARTBEAT_TIMEOUT");
                agv.setFaultMessage("心跳超时超过" + HEARTBEAT_TIMEOUT_SECONDS + "秒");

                if (agv.getCurrentTaskId() != null) {
                    taskDispatchService.reassignTask(agv.getCurrentTaskId(), null, "AGV离线", "system");
                }

                agvRepository.save(agv);
                cacheAgvStatus(agv);
            }
        }
    }

    /**
     * 缓存AGV状态到Redis
     * 供前端实时查询和其他服务调用
     *
     * @param agv AGV实体
     */
    private void cacheAgvStatus(Agv agv) {
        String cacheKey = AGV_STATUS_PREFIX + agv.getId();
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(agv));
    }

    /**
     * 清除AGV故障状态
     * 将AGV状态重置为空闲，清除故障代码和故障信息
     *
     * @param agvId AGV ID
     * @throws IllegalArgumentException AGV不存在时抛出
     */
    @Transactional
    public void clearFault(String agvId) {
        Agv agv = agvRepository.findById(agvId)
                .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));

        agv.setStatus(AgvStatus.IDLE);
        agv.setFaultCode(null);
        agv.setFaultMessage(null);
        agvRepository.save(agv);
        cacheAgvStatus(agv);
        log.info("AGV故障已清除: {}", agv.getAgvNo());
    }

    /**
     * 获取AGV统计信息
     * 包括各状态AGV数量、低电量AGV数量等
     *
     * @return 统计信息Map（状态 -> 数量）
     */
    public Map<String, Object> getAgvStatistics() {
        return Map.of(
                "total", agvRepository.count(),
                "idle", agvRepository.countByStatus(AgvStatus.IDLE),
                "working", agvRepository.countByStatus(AgvStatus.WORKING),
                "charging", agvRepository.countByStatus(AgvStatus.CHARGING),
                "fault", agvRepository.countByStatus(AgvStatus.FAULT),
                "offline", agvRepository.countByStatus(AgvStatus.OFFLINE),
                "paused", agvRepository.countByStatus(AgvStatus.PAUSED),
                "lowBatteryCount", agvRepository.findAll().stream()
                        .filter(a -> a.getBatteryLevel() != null && a.getBatteryLevel() <= LOW_BATTERY_THRESHOLD)
                        .count()
        );
    }
}

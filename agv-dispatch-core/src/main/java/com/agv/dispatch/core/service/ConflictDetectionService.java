package com.agv.dispatch.core.service;

import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.ConflictRecord;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.enums.ConflictType;
import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.repository.ConflictRecordRepository;
import com.agv.dispatch.core.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.agv.dispatch.common.constant.RedisKeyConstant.CONFLICT_KEY;

/**
 * 冲突检测与解决服务
 * 实时检测多AGV运行中的各种冲突，并自动应用解决策略
 *
 * 检测的冲突类型：
 * - 对向冲突：两AGV相向行驶在同一段路径
 * - 交叉冲突：两AGV路径在某节点交叉
 * - 跟车冲突：后车速度快于前车，距离过近
 * - 资源冲突：多AGV抢占同一节点或资源
 *
 * 解决策略（按优先级）：
 * 1. 任务优先级比较：高优先级任务优先
 * 2. 截止时间比较：截止时间早的任务优先
 * 3. 任务进度比较：进度快的任务优先
 * 低优先级AGV暂停让行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionService {

    private final AgvRepository agvRepository;
    private final TaskRepository taskRepository;
    private final ConflictRecordRepository conflictRecordRepository;
    private final PathPlanningService pathPlanningService;
    private final StringRedisTemplate redisTemplate;

    private static final double SAFE_DISTANCE = 2.0;

    /**
     * 检测所有类型的冲突
     * 依次检测路径冲突、位置冲突、资源冲突
     * 检测结果会缓存到Redis供实时查询
     *
     * @return 检测到的冲突记录列表
     */
    public List<ConflictRecord> detectConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();

        conflicts.addAll(detectPathConflicts());
        conflicts.addAll(detectPositionConflicts());
        conflicts.addAll(detectResourceConflicts());

        cacheConflicts(conflicts);
        return conflicts;
    }

    /**
     * 检测路径冲突
     * 检查所有工作中AGV的前瞻路径（当前位置+3步）
     * 如果多个AGV的前瞻路径包含同一节点，则判定为冲突
     *
     * @return 路径冲突记录列表
     */
    private List<ConflictRecord> detectPathConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();
        List<Agv> workingAgvs = agvRepository.findByStatusIn(
                Arrays.asList(com.agv.dispatch.common.enums.AgvStatus.WORKING,
                        com.agv.dispatch.common.enums.AgvStatus.PAUSED));

        Map<String, List<Agv>> nodeOccupancy = new HashMap<>();

        for (Agv agv : workingAgvs) {
            Task task = taskRepository.findById(agv.getCurrentTaskId()).orElse(null);
            if (task == null || task.getPath() == null) {
                continue;
            }

            List<String> path = pathPlanningService.decodePath(task.getPath());
            Integer currentStep = task.getCurrentStep();
            if (currentStep == null) {
                currentStep = 0;
            }

            int lookAhead = Math.min(currentStep + 3, path.size());
            for (int i = currentStep; i < lookAhead; i++) {
                if (i >= 0 && i < path.size()) {
                    String node = path.get(i);
                    nodeOccupancy.computeIfAbsent(node, k -> new ArrayList<>()).add(agv);
                }
            }
        }

        for (Map.Entry<String, List<Agv>> entry : nodeOccupancy.entrySet()) {
            String node = entry.getKey();
            List<Agv> agvsAtNode = entry.getValue();
            if (agvsAtNode.size() >= 2) {
                for (int i = 0; i < agvsAtNode.size(); i++) {
                    for (int j = i + 1; j < agvsAtNode.size(); j++) {
                        Agv agv1 = agvsAtNode.get(i);
                        Agv agv2 = agvsAtNode.get(j);
                        ConflictType conflictType = determineConflictType(agv1, agv2, node);
                        conflicts.add(createConflictRecord(agv1, agv2, conflictType, node));
                    }
                }
            }
        }

        return conflicts;
    }

    /**
     * 判断冲突类型
     * 根据两AGV的路径前后节点关系，判断具体的冲突类型
     *
     * @param agv1 发生冲突的AGV1
     * @param agv2 发生冲突的AGV2
     * @param node 冲突节点
     * @return 冲突类型枚举
     */
    private ConflictType determineConflictType(Agv agv1, Agv agv2, String node) {
        Task task1 = taskRepository.findById(agv1.getCurrentTaskId()).orElse(null);
        Task task2 = taskRepository.findById(agv2.getCurrentTaskId()).orElse(null);

        if (task1 == null || task2 == null) {
            return ConflictType.RESOURCE;
        }

        List<String> path1 = pathPlanningService.decodePath(task1.getPath());
        List<String> path2 = pathPlanningService.decodePath(task2.getPath());

        Integer step1 = task1.getCurrentStep() != null ? task1.getCurrentStep() : 0;
        Integer step2 = task2.getCurrentStep() != null ? task2.getCurrentStep() : 0;

        int idx1 = path1.indexOf(node);
        int idx2 = path2.indexOf(node);

        if (idx1 > 0 && idx2 > 0 && idx1 < path1.size() - 1 && idx2 < path2.size() - 1) {
            String prev1 = path1.get(idx1 - 1);
            String prev2 = path2.get(idx2 - 1);
            String next1 = path1.get(idx1 + 1);
            String next2 = path2.get(idx2 + 1);

            if (prev1.equals(next2) && prev2.equals(next1)) {
                return ConflictType.HEAD_ON;
            }

            if (prev1.equals(prev2)) {
                return ConflictType.FOLLOW;
            }

            if (!prev1.equals(prev2) && !next1.equals(next2)) {
                return ConflictType.CROSS;
            }
        }

        return ConflictType.RESOURCE;
    }

    /**
     * 检测位置冲突
     * 检查所有工作中AGV的实时位置，如果两AGV距离小于安全距离，则判定为冲突
     * 安全距离默认为2米
     *
     * @return 位置冲突记录列表
     */
    private List<ConflictRecord> detectPositionConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();
        List<Agv> workingAgvs = agvRepository.findByStatusIn(
                Arrays.asList(com.agv.dispatch.common.enums.AgvStatus.WORKING,
                        com.agv.dispatch.common.enums.AgvStatus.PAUSED));

        for (int i = 0; i < workingAgvs.size(); i++) {
            for (int j = i + 1; j < workingAgvs.size(); j++) {
                Agv agv1 = workingAgvs.get(i);
                Agv agv2 = workingAgvs.get(j);

                if (agv1.getXCoord() == null || agv1.getYCoord() == null ||
                    agv2.getXCoord() == null || agv2.getYCoord() == null) {
                    continue;
                }

                double distance = calculateDistance(agv1, agv2);
                if (distance < SAFE_DISTANCE) {
                    log.warn("AGV距离过近: {} 和 {}, 距离: {}m", agv1.getAgvNo(), agv2.getAgvNo(), distance);
                    conflicts.add(createConflictRecord(agv1, agv2, ConflictType.RESOURCE,
                            agv1.getCurrentPosition()));
                }
            }
        }

        return conflicts;
    }

    /**
     * 检测资源冲突
     * 预留接口，用于检测充电站、换乘站等共享资源的抢占冲突
     *
     * @return 资源冲突记录列表
     */
    private List<ConflictRecord> detectResourceConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();
        return conflicts;
    }

    /**
     * 创建冲突记录
     * 记录冲突的AGV、类型、位置、关联任务等信息
     *
     * @param agv1 发生冲突的AGV1
     * @param agv2 发生冲突的AGV2
     * @param conflictType 冲突类型
     * @param location 冲突位置（节点编号）
     * @return 保存后的冲突记录
     */
    private ConflictRecord createConflictRecord(Agv agv1, Agv agv2,
                                                ConflictType conflictType, String location) {
        ConflictRecord record = new ConflictRecord();
        record.setAgvId1(agv1.getId());
        record.setAgvId2(agv2.getId());
        record.setConflictType(conflictType);
        record.setLocation(location);
        record.setTaskId1(agv1.getCurrentTaskId());
        record.setTaskId2(agv2.getCurrentTaskId());
        record.setResolved(false);

        ConflictRecord saved = conflictRecordRepository.save(record);
        log.warn("检测到冲突: type={}, agv1={}, agv2={}, location={}",
                conflictType, agv1.getAgvNo(), agv2.getAgvNo(), location);
        return saved;
    }

    /**
     * 解决单个冲突
     * 应用三级解决策略确定让行方，然后暂停让行AGV
     *
     * @param conflictId 冲突记录ID
     * @return 解决策略说明
     */
    public String resolveConflict(Long conflictId) {
        ConflictRecord conflict = conflictRecordRepository.findById(conflictId)
                .orElseThrow(() -> new IllegalArgumentException("冲突记录不存在: " + conflictId));

        if (Boolean.TRUE.equals(conflict.getResolved())) {
            return "冲突已解决";
        }

        String resolution = applyResolutionStrategy(conflict);

        conflict.setResolved(true);
        conflict.setResolvedTime(LocalDateTime.now());
        conflict.setResolution(resolution);
        conflictRecordRepository.save(conflict);

        log.info("冲突已解决: {}, 策略: {}", conflictId, resolution);
        return resolution;
    }

    /**
     * 应用冲突解决策略
     * 三级判定策略：
     * 1. 任务优先级比较：高优先级任务优先
     * 2. 截止时间比较：截止时间早的任务优先
     * 3. 任务进度比较：进度快的任务优先
     * 低优先级AGV暂停让行
     *
     * @param conflict 冲突记录
     * @return 解决策略说明
     */
    private String applyResolutionStrategy(ConflictRecord conflict) {
        Task task1 = taskRepository.findById(conflict.getTaskId1()).orElse(null);
        Task task2 = taskRepository.findById(conflict.getTaskId2()).orElse(null);

        if (task1 == null && task2 == null) {
            return "无任务关联，冲突自动解除";
        }

        Agv agv1 = agvRepository.findById(conflict.getAgvId1()).orElse(null);
        Agv agv2 = agvRepository.findById(conflict.getAgvId2()).orElse(null);

        if (task1 == null) {
            pauseAgv(agv2);
            return "AGV2任务暂停，等待AGV1通过";
        }
        if (task2 == null) {
            pauseAgv(agv1);
            return "AGV1任务暂停，等待AGV2通过";
        }

        int priorityCompare = task1.getPriority().getCode() - task2.getPriority().getCode();
        if (priorityCompare > 0) {
            pauseAgv(agv2);
            return String.format("任务1优先级(%s)高于任务2(%s)，AGV2暂停让行",
                    task1.getPriority(), task2.getPriority());
        } else if (priorityCompare < 0) {
            pauseAgv(agv1);
            return String.format("任务2优先级(%s)高于任务1(%s)，AGV1暂停让行",
                    task2.getPriority(), task1.getPriority());
        }

        if (task1.getDeadline() != null && task2.getDeadline() != null) {
            if (task1.getDeadline().isBefore(task2.getDeadline())) {
                pauseAgv(agv2);
                return "任务1截止时间更早，AGV2暂停让行";
            } else {
                pauseAgv(agv1);
                return "任务2截止时间更早，AGV1暂停让行";
            }
        }

        int step1 = task1.getCurrentStep() != null ? task1.getCurrentStep() : 0;
        int step2 = task2.getCurrentStep() != null ? task2.getCurrentStep() : 0;
        if (step1 > step2) {
            pauseAgv(agv2);
            return "AGV1进度更快，AGV2暂停让行";
        } else {
            pauseAgv(agv1);
            return "AGV2进度更快，AGV1暂停让行";
        }
    }

    /**
     * 暂停AGV（冲突解决时调用）
     * 将AGV状态设置为暂停，等待冲突解除
     *
     * @param agv 需要暂停的AGV
     */
    private void pauseAgv(Agv agv) {
        if (agv == null) {
            return;
        }
        agv.setStatus(com.agv.dispatch.common.enums.AgvStatus.PAUSED);
        agvRepository.save(agv);
        log.info("AGV已暂停: {}", agv.getAgvNo());
    }

    /**
     * 自动解决所有未解决的冲突
     * 按时间倒序处理所有未解决的冲突记录
     * 处理失败的冲突会记录日志但不抛出异常
     */
    public void resolveAllConflicts() {
        List<ConflictRecord> unresolved = conflictRecordRepository.findByResolvedFalseOrderByCreateTimeDesc();
        for (ConflictRecord conflict : unresolved) {
            try {
                resolveConflict(conflict.getId());
            } catch (Exception e) {
                log.error("解决冲突失败: {}", conflict.getId(), e);
            }
        }
    }

    private double calculateDistance(Agv agv1, Agv agv2) {
        double dx = agv1.getXCoord() - agv2.getXCoord();
        double dy = agv1.getYCoord() - agv2.getYCoord();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 将冲突检测结果缓存到Redis
     * 供前端实时查询和WebSocket推送
     *
     * @param conflicts 冲突记录列表
     */
    private void cacheConflicts(List<ConflictRecord> conflicts) {
        if (conflicts.isEmpty()) {
            redisTemplate.delete(CONFLICT_KEY);
            return;
        }
        Map<String, String> conflictData = conflicts.stream()
                .collect(Collectors.toMap(
                        c -> c.getId().toString(),
                        c -> c.getConflictType().getDesc() + ":" + c.getLocation()
                ));
        redisTemplate.opsForHash().putAll(CONFLICT_KEY, conflictData);
    }

    /**
     * 获取所有未解决的冲突记录
     * 按创建时间倒序排列
     *
     * @return 未解决的冲突记录列表
     */
    public List<ConflictRecord> getUnresolvedConflicts() {
        return conflictRecordRepository.findByResolvedFalseOrderByCreateTimeDesc();
    }

    /**
     * 检查AGV是否涉及高优先级任务的冲突
     * 用于调度决策时判断是否需要特殊处理
     *
     * @param agvId AGV ID
     * @return true表示该AGV涉及高优先级任务的冲突
     */
    public boolean hasHighPriorityConflict(String agvId) {
        List<ConflictRecord> unresolved = getUnresolvedConflicts();
        for (ConflictRecord conflict : unresolved) {
            if (agvId.equals(conflict.getAgvId1()) || agvId.equals(conflict.getAgvId2())) {
                Task task1 = taskRepository.findById(conflict.getTaskId1()).orElse(null);
                Task task2 = taskRepository.findById(conflict.getTaskId2()).orElse(null);
                if ((task1 != null && task1.getPriority() == TaskPriority.HIGH) ||
                    (task2 != null && task2.getPriority() == TaskPriority.HIGH)) {
                    return true;
                }
            }
        }
        return false;
    }
}

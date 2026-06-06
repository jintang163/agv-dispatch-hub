package com.agv.dispatch.core.service;

import com.agv.dispatch.common.dto.*;
import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.MapNode;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.entity.TaskLog;
import com.agv.dispatch.common.enums.AgvStatus;
import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.common.enums.TaskType;
import com.agv.dispatch.common.util.IdGenerator;
import com.agv.dispatch.common.util.TaskQueueComparator;
import com.agv.dispatch.core.repository.*;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.agv.dispatch.common.constant.RedisKeyConstant.*;

/**
 * 任务调度核心服务
 * 负责任务的全生命周期管理，包括：
 * - 任务创建与入队
 * - 自动调度与分配
 * - 优先级调整与插队
 * - 状态流转与进度更新
 * - 任务取消与重分配
 * - AGV故障处理
 *
 * 使用Redis ZSet作为优先级队列，通过分布式锁保证调度不并发
 * 所有任务修改操作都会更新缓存，保证缓存一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDispatchService {

    private final TaskRepository taskRepository;
    private final AgvRepository agvRepository;
    private final TaskLogRepository taskLogRepository;
    private final MapNodeRepository mapNodeRepository;
    private final ConflictRecordRepository conflictRecordRepository;
    private final PathPlanningService pathPlanningService;
    private final ConflictDetectionService conflictDetectionService;
    @org.springframework.context.annotation.Lazy
    private final DeadlockDetectionService deadlockDetectionService;
    private final StringRedisTemplate redisTemplate;

    private final TaskQueueComparator taskQueueComparator = new TaskQueueComparator();

    /**
     * 创建任务
     * 接收WMS下发的搬运、拣选等任务，生成任务编号并加入优先级队列
     *
     * @param dto 任务创建参数，包含任务类型、优先级、起止点、截止时间等
     * @return 创建后的任务实体
     */
    @Transactional
    public Task createTask(TaskCreateDTO dto) {
        Task task = new Task();
        task.setId(IdGenerator.generateId());
        task.setTaskNo(IdGenerator.generateTaskNo());
        task.setTaskType(dto.getTaskType());
        task.setPriority(dto.getPriority() != null ? dto.getPriority() : TaskPriority.MEDIUM);
        task.setStatus(TaskStatus.PENDING);
        task.setStartPoint(dto.getStartPoint());
        task.setEndPoint(dto.getEndPoint());
        task.setLoadWeight(dto.getLoadWeight());
        task.setCargoInfo(dto.getCargoInfo());
        task.setDeadline(dto.getDeadline());
        task.setWmsOrderNo(dto.getWmsOrderNo());
        task.setRemark(dto.getRemark());
        task.setCurrentStep(0);

        Task saved = taskRepository.save(task);

        addToTaskQueue(saved);

        recordTaskLog(saved.getId(), null, "创建任务",
                null, TaskStatus.PENDING.getDesc(),
                "WMS创建任务", null);

        log.info("任务创建成功: taskNo={}, priority={}", saved.getTaskNo(), saved.getPriority());
        return saved;
    }

    /**
     * 将任务添加到Redis优先级队列
     * 使用统一的打分算法 TaskQueueComparator.calculateQueueScore()
     * 确保入队顺序、调度顺序、页面展示顺序三者一致
     *
     * @param task 待入队的任务
     */
    private void addToTaskQueue(Task task) {
        // 使用统一的打分算法，确保全系统排序逻辑一致
        double score = TaskQueueComparator.calculateQueueScore(task);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(TASK_QUEUE_KEY, task.getId(), score);

        // 同时更新任务缓存
        cacheTask(task);
    }

    public void removeFromTaskQueue(String taskId) {
        redisTemplate.opsForZSet().remove(TASK_QUEUE_KEY, taskId);
    }

    /**
     * 更新任务优先级
     * 支持高优先级任务插队，更新后重新计算队列分数
     * 无论任务状态如何，都会更新缓存以保证缓存一致性
     *
     * @param taskId 任务ID
     * @param newPriority 新的优先级
     * @param operator 操作人
     */
    public void updateTaskPriority(String taskId, TaskPriority newPriority, String operator) {
        Task task = getTaskById(taskId);
        TaskPriority oldPriority = task.getPriority();
        task.setPriority(newPriority);
        taskRepository.save(task);

        // 如果是待分配状态，重新入队更新分数（支持高优先级插队）
        if (task.getStatus() == TaskStatus.PENDING) {
            addToTaskQueue(task); // addToTaskQueue 内部会调用 cacheTask 更新缓存
        } else {
            // 非待分配状态也需要更新缓存，保证缓存一致性
            cacheTask(task);
        }

        recordTaskLog(taskId, task.getAgvId(), "更新优先级",
                oldPriority.getDesc(), newPriority.getDesc(),
                "优先级由 " + oldPriority.getDesc() + " 调整为 " + newPriority.getDesc(),
                operator);

        log.info("任务优先级更新: taskId={}, {} -> {}", taskId, oldPriority, newPriority);
    }

    /**
     * 手动分配任务给指定AGV
     * 包含路径规划、路径占用、AGV状态更新、缓存更新等完整流程
     *
     * @param taskId 任务ID
     * @param agvId AGV ID
     * @param path 预规划的路径（可选，为null时自动规划）
     * @param operator 操作人
     * @return 更新后的任务实体
     */
    @Transactional
    public Task assignTask(String taskId, String agvId, String path, String operator) {
        // 从缓存获取任务，优先走缓存
        Task task = getTaskById(taskId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new IllegalStateException("任务状态不允许分配: " + task.getStatus());
        }

        // 校验AGV状态
        Agv agv = agvRepository.findById(agvId)
                .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));
        if (!agv.getStatus().isAvailable()) {
            throw new IllegalStateException("AGV状态不可用: " + agv.getStatus());
        }

        // 自动规划路径：AGV当前位置 -> 任务起点 -> 任务终点
        if (path == null || path.isEmpty()) {
            // AGV当前位置到任务起点
            List<String> plannedPath = pathPlanningService.planPath(
                    agv.getCurrentPosition(), task.getStartPoint());
            // 任务起点到终点
            List<String> taskPath = pathPlanningService.planPath(
                    task.getStartPoint(), task.getEndPoint());

            // 合并路径，去除重复的起点
            List<String> fullPath = new ArrayList<>(plannedPath);
            if (!taskPath.isEmpty()) {
                fullPath.addAll(taskPath.subList(1, taskPath.size()));
            }
            path = pathPlanningService.encodePath(fullPath);
        }

        // 更新任务状态
        task.setAgvId(agvId);
        task.setStatus(TaskStatus.ASSIGNED);
        task.setPath(path);
        task.setAssignedTime(LocalDateTime.now());
        task.setCurrentStep(0);
        Task saved = taskRepository.save(task);

        // 更新AGV状态为工作中
        agv.setStatus(AgvStatus.WORKING);
        agv.setCurrentTaskId(taskId);
        agvRepository.save(agv);

        // 从待分配队列中移除
        removeFromTaskQueue(taskId);

        // 占用路径（前瞻3步）
        List<String> pathList = pathPlanningService.decodePath(path);
        pathPlanningService.occupyPath(agvId, pathList, 0);

        // 记录操作日志
        recordTaskLog(taskId, agvId, "分配任务",
                TaskStatus.PENDING.getDesc(), TaskStatus.ASSIGNED.getDesc(),
                "分配给AGV: " + agv.getAgvNo(), operator);

        // 更新任务缓存，保证缓存一致性
        cacheTask(saved);

        log.info("任务分配成功: taskId={}, agvId={}", taskId, agvId);
        return saved;
    }

    /**
     * 自动分配任务，系统自动选择最优AGV
     * 选择策略：优先选择路径可用的AGV，其次选择距离最近的AGV
     *
     * @param taskId 任务ID
     * @param operator 操作人
     * @return 更新后的任务实体
     */
    @Transactional
    public Task autoAssignTask(String taskId, String operator) {
        Task task = getTaskById(taskId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new IllegalStateException("任务状态不允许分配: " + task.getStatus());
        }

        // 获取任务起点的坐标信息
        MapNode startNode = mapNodeRepository.findByNodeCode(task.getStartPoint())
                .orElseThrow(() -> new IllegalArgumentException("起点不存在: " + task.getStartPoint()));

        // 查询满足载重要求且距离最近的AGV列表
        List<Agv> suitableAgvs = agvRepository.findSuitableAgvs(
                task.getLoadWeight(), startNode.getXCoord(), startNode.getYCoord());

        if (suitableAgvs.isEmpty()) {
            throw new IllegalStateException("没有可用的AGV");
        }

        Agv selectedAgv = suitableAgvs.get(0);

        // 优先选择路径可用（无冲突）的AGV
        for (Agv agv : suitableAgvs) {
            List<String> path = pathPlanningService.planPath(
                    agv.getCurrentPosition(), task.getStartPoint());
            if (!path.isEmpty() && pathPlanningService.isPathAvailable(path)) {
                selectedAgv = agv;
                break;
            }
        }

        // 复用手动分配逻辑
        return assignTask(taskId, selectedAgv.getId(), null, operator);
    }

    /**
     * 自动调度任务，每5秒执行一次
     * 使用Redis分布式锁确保同一时间只有一个调度线程执行，避免并发调度问题
     * 调度顺序：按照Redis ZSet中的分数从高到低（与页面展示顺序一致）
     *
     * 调度流程：
     * 1. 获取分布式锁
     * 2. 从Redis队列按优先级取出所有待分配任务
     * 3. 查询可用AGV列表
     * 4. 为每个任务选择最优AGV并分配
     * 5. 释放分布式锁
     */
    @Scheduled(fixedDelay = 5000)
    public void autoDispatch() {
        try {
            // 使用Redis分布式锁，防止多实例并发调度
            String lockValue = UUID.randomUUID().toString();
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(DISPATCH_LOCK_KEY, lockValue, DISPATCH_LOCK_SECONDS, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(locked)) {
                // 其他实例正在调度，直接返回
                return;
            }

            try {
                ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
                // 从Redis ZSet按分数从高到低取出任务，与getPendingQueue使用相同的顺序
                // 确保实际调度顺序与页面展示顺序一致
                Set<String> taskIds = zSetOps.reverseRange(TASK_QUEUE_KEY, 0, -1);

                if (taskIds == null || taskIds.isEmpty()) {
                    return;
                }

                // 查询所有可用AGV（按空闲时间排序）
                List<Agv> availableAgvs = agvRepository.findAvailableAgvsOrdered();
                if (availableAgvs.isEmpty()) {
                    return;
                }

                List<String> processedTasks = new ArrayList<>();

                // 按优先级顺序为每个任务分配AGV
                for (String taskId : taskIds) {
                    if (availableAgvs.isEmpty()) {
                        break; // 没有可用AGV了，结束本轮调度
                    }

                    // 优先从缓存获取任务
                    Task task = getTaskById(taskId);
                    if (task.getStatus() != TaskStatus.PENDING) {
                        // 任务状态已变化，从队列中移除
                        processedTasks.add(taskId);
                        continue;
                    }

                    try {
                        // 为任务选择最优AGV
                        Agv selectedAgv = selectBestAgv(task, availableAgvs);
                        if (selectedAgv != null) {
                            assignTask(taskId, selectedAgv.getId(), null, "system");
                            availableAgvs.remove(selectedAgv); // 一个AGV只能分配一个任务
                            processedTasks.add(taskId);
                        }
                    } catch (Exception e) {
                        log.warn("自动分配任务失败: taskId={}, error={}", taskId, e.getMessage());
                    }
                }

                // 批量从队列中移除已处理的任务
                for (String processedId : processedTasks) {
                    zSetOps.remove(TASK_QUEUE_KEY, processedId);
                }

            } finally {
                // 释放分布式锁（只释放自己加的锁）
                String currentLock = redisTemplate.opsForValue().get(DISPATCH_LOCK_KEY);
                if (lockValue.equals(currentLock)) {
                    redisTemplate.delete(DISPATCH_LOCK_KEY);
                }
            }
        } catch (Exception e) {
            log.error("自动调度异常", e);
        }
    }

    /**
     * 为任务选择最优的AGV
     * 选择策略：
     * 1. 优先选择路径完全可用（无冲突）的AGV
     * 2. 如果没有路径完全可用的AGV，选择距离任务起点最近的AGV
     * 3. 如果都没有坐标信息，选择列表中的第一个AGV
     *
     * @param task 待分配的任务
     * @param availableAgvs 可用AGV列表
     * @return 最优的AGV，可能为null
     */
    private Agv selectBestAgv(Task task, List<Agv> availableAgvs) {
        // 第一轮：选择路径完全可用的AGV
        for (Agv agv : availableAgvs) {
            List<String> path = pathPlanningService.planPath(
                    agv.getCurrentPosition(), task.getStartPoint());
            if (!path.isEmpty() && pathPlanningService.isPathAvailable(path)) {
                return agv;
            }
        }

        // 第二轮：选择距离最近的AGV
        MapNode startNode = mapNodeRepository.findByNodeCode(task.getStartPoint()).orElse(null);
        if (startNode == null) {
            return availableAgvs.isEmpty() ? null : availableAgvs.get(0);
        }

        Agv nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (Agv agv : availableAgvs) {
            if (agv.getXCoord() != null && agv.getYCoord() != null) {
                // 计算欧几里得距离
                double distance = Math.sqrt(
                        Math.pow(agv.getXCoord() - startNode.getXCoord(), 2) +
                        Math.pow(agv.getYCoord() - startNode.getYCoord(), 2));
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = agv;
                }
            }
        }

        return nearest != null ? nearest :
                (availableAgvs.isEmpty() ? null : availableAgvs.get(0));
    }

    /**
     * 定时检测并解决冲突，每3秒执行一次
     * 先检测所有冲突，然后自动解决所有未解决的冲突
     */
    @Scheduled(fixedDelay = 3000)
    public void detectAndResolveConflicts() {
        conflictDetectionService.detectConflicts();
        conflictDetectionService.resolveAllConflicts();
    }

    /**
     * 更新任务状态，带状态转换校验
     * 根据不同的目标状态，执行相应的业务逻辑（释放AGV、释放路径、更新缓存等）
     *
     * @param taskId 任务ID
     * @param newStatus 目标状态
     * @param remark 备注信息
     * @param operator 操作人
     * @throws IllegalStateException 如果状态转换不合法
     */
    @Transactional
    public void updateTaskStatus(String taskId, TaskStatus newStatus, String remark, String operator) {
        // 优先从缓存获取任务
        Task task = getTaskById(taskId);
        TaskStatus oldStatus = task.getStatus();

        // 状态机校验，确保状态转换合法
        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("状态不允许转换: %s -> %s", oldStatus, newStatus));
        }

        task.setStatus(newStatus);

        // 根据不同状态执行相应业务逻辑
        if (newStatus == TaskStatus.EXECUTING) {
            // 开始执行，记录开始时间
            task.setStartTime(LocalDateTime.now());
        } else if (newStatus == TaskStatus.COMPLETED) {
            // 任务完成，记录完成时间并释放AGV
            task.setCompletedTime(LocalDateTime.now());
            if (task.getAgvId() != null) {
                releaseAgv(task.getAgvId());
            }
        } else if (newStatus == TaskStatus.CANCELLED) {
            // 任务取消，释放AGV和占用的路径
            if (task.getAgvId() != null) {
                releaseAgv(task.getAgvId());
                pathPlanningService.releaseAllPath(task.getAgvId());
            }
        } else if (newStatus == TaskStatus.ABNORMAL) {
            // 任务异常，标记AGV故障并释放路径
            if (task.getAgvId() != null) {
                Agv agv = agvRepository.findById(task.getAgvId()).orElse(null);
                if (agv != null) {
                    agv.setStatus(AgvStatus.FAULT);
                    agvRepository.save(agv);
                }
                pathPlanningService.releaseAllPath(task.getAgvId());
            }
        } else if (newStatus == TaskStatus.PENDING && task.getAgvId() != null) {
            // 回到待分配状态（如重分配时），释放AGV和路径
            releaseAgv(task.getAgvId());
            pathPlanningService.releaseAllPath(task.getAgvId());
            task.setAgvId(null);
        }

        taskRepository.save(task);

        // 记录操作日志
        recordTaskLog(taskId, task.getAgvId(), "状态更新",
                oldStatus.getDesc(), newStatus.getDesc(), remark, operator);

        // 根据状态更新队列
        if (newStatus == TaskStatus.PENDING) {
            addToTaskQueue(task); // 重新入队，会自动更新缓存
        } else {
            removeFromTaskQueue(taskId);
            cacheTask(task); // 非待分配状态需要手动更新缓存
        }

        log.info("任务状态更新: taskId={}, {} -> {}", taskId, oldStatus, newStatus);
    }

    /**
     * 释放AGV，将AGV状态设置为空闲并清空当前任务
     * @param agvId AGV ID
     */
    private void releaseAgv(String agvId) {
        Agv agv = agvRepository.findById(agvId).orElse(null);
        if (agv != null) {
            agv.setStatus(AgvStatus.IDLE);
            agv.setCurrentTaskId(null);
            agvRepository.save(agv);
        }
    }

    /**
     * 取消任务
     * 只有未完成的任务可以取消，取消后释放AGV和占用的路径
     *
     * @param taskId 任务ID
     * @param reason 取消原因
     * @param operator 操作人
     * @throws IllegalStateException 如果任务已完成或已取消
     */
    @Transactional
    public void cancelTask(String taskId, String reason, String operator) {
        Task task = getTaskById(taskId);
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("任务已完成或已取消");
        }

        // 更新任务状态为已取消（内部会处理AGV和路径释放）
        updateTaskStatus(taskId, TaskStatus.CANCELLED, reason, operator);
        removeFromTaskQueue(taskId);
    }

    /**
     * 任务重分配
     * 当AGV故障或路径阻塞时，将任务返回待分配队列，可指定目标AGV
     *
     * @param taskId 任务ID
     * @param targetAgvId 目标AGV ID（可选，为null时仅返回队列）
     * @param reason 重分配原因
     * @param operator 操作人
     * @return 更新后的任务实体
     */
    @Transactional
    public Task reassignTask(String taskId, String targetAgvId, String reason, String operator) {
        Task task = getTaskById(taskId);
        String oldAgvId = task.getAgvId();

        // 校验状态，只有已分配、执行中、异常状态的任务可以重分配
        if (task.getStatus() != TaskStatus.ASSIGNED &&
            task.getStatus() != TaskStatus.EXECUTING &&
            task.getStatus() != TaskStatus.ABNORMAL) {
            throw new IllegalStateException("任务状态不允许重分配: " + task.getStatus());
        }

        // 释放原AGV和占用的路径
        if (oldAgvId != null) {
            releaseAgv(oldAgvId);
            pathPlanningService.releaseAllPath(oldAgvId);
        }

        // 重置任务状态为待分配
        task.setStatus(TaskStatus.PENDING);
        task.setAgvId(null);
        task.setAssignedTime(null);
        task.setCurrentStep(0);
        task.setPath(null);
        taskRepository.save(task);

        // 重新加入待分配队列（会自动更新缓存）
        addToTaskQueue(task);

        // 记录操作日志
        recordTaskLog(taskId, oldAgvId, "任务重分配",
                task.getStatus().getDesc(), TaskStatus.PENDING.getDesc(),
                reason != null ? reason : "AGV故障或路径阻塞，返回待分配队列", operator);

        // 如果指定了目标AGV，直接分配
        if (targetAgvId != null && !targetAgvId.isEmpty()) {
            return assignTask(taskId, targetAgvId, null, operator);
        }

        log.info("任务已返回待分配队列: taskId={}", taskId);
        return task;
    }

    /**
     * 更新任务执行进度
     * AGV每到达一个节点时调用，更新当前步骤和位置
     * 自动处理路径占用的释放和重新占用
     *
     * @param taskId 任务ID
     * @param step 当前步骤索引
     * @param currentPosition 当前位置节点编号
     */
    public void updateTaskProgress(String taskId, int step, String currentPosition) {
        Task task = getTaskById(taskId);
        // 只有已分配和执行中的任务可以更新进度
        if (task.getStatus() != TaskStatus.EXECUTING && task.getStatus() != TaskStatus.ASSIGNED) {
            return;
        }

        // 如果是已分配状态，先更新为执行中
        if (task.getStatus() == TaskStatus.ASSIGNED) {
            updateTaskStatus(taskId, TaskStatus.EXECUTING, "开始执行", "system");
        }

        task.setCurrentStep(step);

        // 更新路径占用：释放已走过的节点，占用前瞻3步的节点
        List<String> path = pathPlanningService.decodePath(task.getPath());
        if (!path.isEmpty()) {
            pathPlanningService.releasePath(task.getAgvId(), path, step);
            pathPlanningService.occupyPath(task.getAgvId(), path, step);
        }

        // 更新AGV当前位置坐标
        if (task.getAgvId() != null) {
            Agv agv = agvRepository.findById(task.getAgvId()).orElse(null);
            if (agv != null) {
                agv.setCurrentPosition(currentPosition);
                // 根据节点编号更新坐标
                mapNodeRepository.findByNodeCode(currentPosition).ifPresent(node -> {
                    agv.setXCoord(node.getXCoord());
                    agv.setYCoord(node.getYCoord());
                });
                agvRepository.save(agv);
            }
        }

        // 检查是否到达终点
        if (!path.isEmpty() && step >= path.size() - 1) {
            updateTaskStatus(taskId, TaskStatus.COMPLETED, "到达终点", "system");
        } else {
            taskRepository.save(task);
        }

        // 更新缓存，保证缓存一致性
        cacheTask(task);
    }

    /**
     * 根据ID获取任务，优先从缓存获取
     * 缓存未命中时从数据库查询并更新缓存
     *
     * @param taskId 任务ID
     * @return 任务实体
     * @throws IllegalArgumentException 如果任务不存在
     */
    public Task getTaskById(String taskId) {
        String cacheKey = TASK_PREFIX + taskId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            try {
                return JSON.parseObject(cached, Task.class);
            } catch (Exception e) {
                log.warn("解析缓存任务失败", e);
            }
        }

        // 缓存未命中，从数据库查询
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        // 更新缓存
        cacheTask(task);
        return task;
    }

    /**
     * 将任务写入Redis缓存
     * 所有修改任务的方法都应调用此方法保证缓存一致性
     *
     * @param task 任务实体
     */
    private void cacheTask(Task task) {
        String cacheKey = TASK_PREFIX + task.getId();
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(task),
                TASK_CACHE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 分页查询任务列表
     * 支持多条件组合查询：任务编号、WMS单号、状态、类型、优先级、AGV编号、创建时间范围
     *
     * @param dto 查询条件DTO
     * @return 分页结果
     */
    public Page<Task> queryTasks(TaskQueryDTO dto) {
        Pageable pageable = PageRequest.of(
                dto.getPageNum() - 1, dto.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        return taskRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (dto.getTaskNo() != null && !dto.getTaskNo().isEmpty()) {
                predicates.add(cb.like(root.get("taskNo"), "%" + dto.getTaskNo() + "%"));
            }
            if (dto.getWmsOrderNo() != null && !dto.getWmsOrderNo().isEmpty()) {
                predicates.add(cb.equal(root.get("wmsOrderNo"), dto.getWmsOrderNo()));
            }
            if (dto.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), dto.getStatus()));
            }
            if (dto.getTaskType() != null) {
                predicates.add(cb.equal(root.get("taskType"), dto.getTaskType()));
            }
            if (dto.getPriority() != null) {
                predicates.add(cb.equal(root.get("priority"), dto.getPriority()));
            }
            if (dto.getAgvId() != null && !dto.getAgvId().isEmpty()) {
                predicates.add(cb.equal(root.get("agvId"), dto.getAgvId()));
            }
            if (dto.getCreateTimeStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), dto.getCreateTimeStart()));
            }
            if (dto.getCreateTimeEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), dto.getCreateTimeEnd()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
    }

    /**
     * 获取任务统计数据
     * 包括：各状态任务数量、今日完成数、今日异常数、高优先级待分配数、平均完成时间
     *
     * @return 统计数据VO
     */
    public TaskStatisticsVO getStatistics() {
        TaskStatisticsVO vo = new TaskStatisticsVO();
        vo.setPendingCount(taskRepository.countByStatus(TaskStatus.PENDING));
        vo.setAssignedCount(taskRepository.countByStatus(TaskStatus.ASSIGNED));
        vo.setExecutingCount(taskRepository.countByStatus(TaskStatus.EXECUTING));

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        vo.setTodayCompletedCount(taskRepository.countByStatusAndCreateTimeAfter(
                TaskStatus.COMPLETED, todayStart));
        vo.setTodayAbnormalCount(taskRepository.countByStatusAndCreateTimeAfter(
                TaskStatus.ABNORMAL, todayStart));
        vo.setHighPriorityCount(taskRepository.countByStatusAndPriority(
                TaskStatus.PENDING, TaskPriority.HIGH));

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        Double avgTime = taskRepository.calculateAvgCompletionTime(weekAgo);
        vo.setAvgCompletionTime(avgTime != null ? avgTime : 0.0);

        return vo;
    }

    /**
     * 获取任务的操作日志
     * @param taskId 任务ID
     * @return 按时间倒序排列的操作日志列表
     */
    public List<TaskLog> getTaskLogs(String taskId) {
        return taskLogRepository.findByTaskIdOrderByCreateTimeDesc(taskId);
    }

    /**
     * 记录任务操作日志
     * 所有对任务的修改操作都应调用此方法记录操作历史
     *
     * @param taskId 任务ID
     * @param agvId AGV ID
     * @param operation 操作类型
     * @param oldStatus 原状态
     * @param newStatus 新状态
     * @param remark 备注
     * @param operator 操作人
     */
    private void recordTaskLog(String taskId, String agvId, String operation,
                               String oldStatus, String newStatus, String remark, String operator) {
        TaskLog log = new TaskLog();
        log.setTaskId(taskId);
        log.setAgvId(agvId);
        log.setOperation(operation);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setRemark(remark);
        log.setOperator(operator != null ? operator : "system");
        taskLogRepository.save(log);
    }

    /**
     * 获取待分配任务队列
     * 直接按照Redis ZSet中的顺序返回，与autoDispatch调度顺序保持一致
     * 不再进行二次排序，避免页面展示与实际调度顺序不一致
     *
     * @return 按优先级排序的待分配任务列表
     */
    public List<Task> getPendingQueue() {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        // 从Redis ZSet按分数从高到低取出，与autoDispatch使用相同的顺序
        Set<String> taskIds = zSetOps.reverseRange(TASK_QUEUE_KEY, 0, -1);

        List<Task> tasks = new ArrayList<>();
        if (taskIds != null) {
            for (String taskId : taskIds) {
                try {
                    // 优先从缓存获取任务详情
                    Task task = getTaskById(taskId);
                    if (task.getStatus() == TaskStatus.PENDING) {
                        tasks.add(task);
                    } else {
                        // 状态不一致的任务从队列中移除
                        zSetOps.remove(TASK_QUEUE_KEY, taskId);
                    }
                } catch (Exception e) {
                    // 任务不存在或获取失败，从队列中移除
                    zSetOps.remove(TASK_QUEUE_KEY, taskId);
                    log.warn("从队列移除无效任务: taskId={}, error={}", taskId, e.getMessage());
                }
            }
        }

        // 注意：此处不再进行二次排序，保持与Redis队列顺序一致
        // 因为Redis入队时已经使用 TaskQueueComparator.calculateQueueScore() 计算分数
        // 这样确保页面展示顺序与 autoDispatch 实际调度顺序完全一致
        return tasks;
    }

    /**
     * 刷新任务队列
     * 清空Redis队列后重新从数据库加载所有待分配任务并入队
     * 用于队列数据不一致时的手动修复
     */
    public void refreshQueue() {
        List<Task> pendingTasks = taskRepository.findPendingTasksOrdered(TaskStatus.PENDING);
        redisTemplate.delete(TASK_QUEUE_KEY);
        for (Task task : pendingTasks) {
            addToTaskQueue(task);
        }
        log.info("任务队列已刷新，共{}个任务", pendingTasks.size());
    }

    /**
     * 获取待分配队列的任务数量
     * @return 任务数量
     */
    public long getQueueSize() {
        Long size = redisTemplate.opsForZSet().size(TASK_QUEUE_KEY);
        return size != null ? size : 0;
    }

    /**
     * 处理AGV故障
     * 将AGV状态设为故障，释放占用的路径，并将当前任务标记为异常
     *
     * @param agvId AGV ID
     * @param faultCode 故障代码
     * @param faultMessage 故障信息
     */
    @Transactional
    public void handleAgvFault(String agvId, String faultCode, String faultMessage) {
        Agv agv = agvRepository.findById(agvId).orElse(null);
        if (agv == null) {
            return;
        }

        agv.setStatus(AgvStatus.FAULT);
        agv.setFaultCode(faultCode);
        agv.setFaultMessage(faultMessage);
        agvRepository.save(agv);

        pathPlanningService.releaseAllPath(agvId);

        if (agv.getCurrentTaskId() != null) {
            try {
                Task task = getTaskById(agv.getCurrentTaskId());
                if (task.getStatus() == TaskStatus.EXECUTING ||
                    task.getStatus() == TaskStatus.ASSIGNED) {
                    updateTaskStatus(agv.getCurrentTaskId(), TaskStatus.ABNORMAL,
                            "AGV故障: " + faultMessage, "system");
                }
            } catch (Exception e) {
                log.error("处理AGV故障任务异常", e);
            }
        }

        log.warn("AGV故障: agvId={}, faultCode={}, faultMessage={}", agvId, faultCode, faultMessage);
    }

    /**
     * 恢复AGV运行
     * 暂停状态 -> 工作中
     * 故障状态 -> 空闲（清除故障信息）
     *
     * @param agvId AGV ID
     * @param operator 操作人
     */
    @Transactional
    public void resumeAgv(String agvId, String operator) {
        Agv agv = agvRepository.findById(agvId)
                .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));

        if (agv.getStatus() == AgvStatus.PAUSED) {
            agv.setStatus(AgvStatus.WORKING);
        } else if (agv.getStatus() == AgvStatus.FAULT) {
            agv.setStatus(AgvStatus.IDLE);
            agv.setFaultCode(null);
            agv.setFaultMessage(null);
        }

        agvRepository.save(agv);
        log.info("AGV恢复: agvId={}, newStatus={}", agvId, agv.getStatus());
    }

    /**
     * 暂停AGV
     * 只能暂停工作中的AGV
     *
     * @param agvId AGV ID
     * @param operator 操作人
     */
    @Transactional
    public void pauseAgv(String agvId, String operator) {
        Agv agv = agvRepository.findById(agvId)
                .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));

        if (agv.getStatus() == AgvStatus.WORKING) {
            agv.setStatus(AgvStatus.PAUSED);
            agvRepository.save(agv);
            log.info("AGV暂停: agvId={}", agvId);
        }
    }

    @Transactional
    public PathPlanningResult dynamicReplanTask(String taskId, String blockedNode, String reason, String operator) {
        Task task = getTaskById(taskId);

        if (task.getStatus() != TaskStatus.EXECUTING && task.getStatus() != TaskStatus.ASSIGNED) {
            return PathPlanningResult.failure("任务状态不支持重规划: " + task.getStatus());
        }

        if (blockedNode != null && !blockedNode.isEmpty()) {
            pathPlanningService.markPathBlocked(blockedNode, reason);
        }

        PathPlanningResult replanResult = pathPlanningService.dynamicReplan(task, blockedNode);

        if (replanResult.isSuccess()) {
            String oldPath = task.getPath();
            task.setPath(pathPlanningService.encodePath(replanResult.getPath()));
            taskRepository.save(task);

            if (task.getAgvId() != null) {
                pathPlanningService.releaseAllPath(task.getAgvId());
                pathPlanningService.occupyPath(task.getAgvId(), replanResult.getPath(),
                        task.getCurrentStep() != null ? task.getCurrentStep() : 0);
            }

            recordTaskLog(taskId, task.getAgvId(), "动态重规划",
                    oldPath, task.getPath(),
                    reason != null ? reason : "路径阻塞，自动重规划", operator);

            cacheTask(task);

            log.info("任务动态重规划成功: taskId={}, 原路径长度={}, 新路径长度={}",
                    taskId,
                    pathPlanningService.decodePath(oldPath).size(),
                    replanResult.getPath().size());
        }

        return replanResult;
    }

    @Transactional
    public PathPlanningResult replanTaskFromCurrent(String taskId, String operator) {
        Task task = getTaskById(taskId);

        if (task.getStatus() != TaskStatus.EXECUTING && task.getStatus() != TaskStatus.ASSIGNED) {
            return PathPlanningResult.failure("任务状态不支持重规划: " + task.getStatus());
        }

        PathPlanningResult replanResult = pathPlanningService.replanFromCurrentPosition(task);

        if (replanResult.isSuccess()) {
            String oldPath = task.getPath();
            task.setPath(pathPlanningService.encodePath(replanResult.getPath()));
            taskRepository.save(task);

            if (task.getAgvId() != null) {
                pathPlanningService.releaseAllPath(task.getAgvId());
                pathPlanningService.occupyPath(task.getAgvId(), replanResult.getPath(),
                        task.getCurrentStep() != null ? task.getCurrentStep() : 0);
            }

            recordTaskLog(taskId, task.getAgvId(), "当前位置重规划",
                    oldPath, task.getPath(),
                    replanResult.isHasDetour() ? "检测到更优路径，自动重规划" : "路径无变化",
                    operator);

            cacheTask(task);

            log.info("任务从当前位置重规划: taskId={}, 是否绕行={}", taskId, replanResult.isHasDetour());
        }

        return replanResult;
    }

    @Transactional
    public void handlePathBlocked(String agvId, String blockedNode, String reason) {
        Agv agv = agvRepository.findById(agvId).orElse(null);
        if (agv == null) {
            return;
        }

        String taskId = agv.getCurrentTaskId();
        if (taskId == null) {
            return;
        }

        log.warn("检测到路径阻塞: agvId={}, blockedNode={}, reason={}", agvId, blockedNode, reason);

        agv.setStatus(AgvStatus.PAUSED);
        agvRepository.save(agv);

        PathPlanningResult result = dynamicReplanTask(taskId, blockedNode, reason, "system");

        if (result.isSuccess()) {
            agv.setStatus(AgvStatus.WORKING);
            agvRepository.save(agv);
            log.info("路径阻塞已处理，重规划成功: agvId={}", agvId);
        } else {
            log.error("路径阻塞处理失败，重规划失败: agvId={}, message={}", agvId, result.getMessage());
        }
    }

    public void clearPathBlocked(String nodeCode) {
        pathPlanningService.clearPathBlocked(nodeCode);
    }

    public Map<String, String> getAllBlockedPaths() {
        return pathPlanningService.getBlockedPaths();
    }

    public Map<String, String> getAllOccupiedPaths() {
        return pathPlanningService.getOccupiedPaths();
    }

    public Map<String, String> getAllLockedIntersections() {
        return pathPlanningService.getLockedIntersections();
    }

    public List<com.agv.dispatch.common.entity.DeadlockRecord> getCurrentDeadlocks() {
        return deadlockDetectionService.getUnresolvedDeadlocks();
    }

    @Transactional
    public String resolveDeadlockManually(Long deadlockId) {
        return deadlockDetectionService.resolveDeadlock(deadlockId);
    }

    public void forceResolveAllDeadlocks() {
        deadlockDetectionService.resolveAllDeadlocks();
    }
}

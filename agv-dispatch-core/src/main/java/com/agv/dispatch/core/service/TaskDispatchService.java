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
    private final StringRedisTemplate redisTemplate;

    private final TaskQueueComparator taskQueueComparator = new TaskQueueComparator();

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

    private void addToTaskQueue(Task task) {
        double score = calculateQueueScore(task);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(TASK_QUEUE_KEY, task.getId(), score);

        cacheTask(task);
    }

    private double calculateQueueScore(Task task) {
        double priorityScore = task.getPriority().getCode() * 100;
        double deadlineScore = 0;

        if (task.getDeadline() != null) {
            LocalDateTime now = LocalDateTime.now();
            long minutesUntilDeadline = java.time.Duration.between(now, task.getDeadline()).toMinutes();
            if (minutesUntilDeadline <= 0) {
                deadlineScore = 1000;
            } else if (minutesUntilDeadline < 30) {
                deadlineScore = 500 - minutesUntilDeadline * 10;
            } else if (minutesUntilDeadline < 60) {
                deadlineScore = 200 - (minutesUntilDeadline - 30) * 5;
            }
        }

        double timeScore = System.currentTimeMillis() / 1000000000.0;

        return priorityScore + deadlineScore + (100 - timeScore);
    }

    public void removeFromTaskQueue(String taskId) {
        redisTemplate.opsForZSet().remove(TASK_QUEUE_KEY, taskId);
    }

    public void updateTaskPriority(String taskId, TaskPriority newPriority, String operator) {
        Task task = getTaskById(taskId);
        TaskPriority oldPriority = task.getPriority();
        task.setPriority(newPriority);
        taskRepository.save(task);

        if (task.getStatus() == TaskStatus.PENDING) {
            addToTaskQueue(task);
        }

        recordTaskLog(taskId, task.getAgvId(), "更新优先级",
                oldPriority.getDesc(), newPriority.getDesc(),
                "优先级由 " + oldPriority.getDesc() + " 调整为 " + newPriority.getDesc(),
                operator);

        log.info("任务优先级更新: taskId={}, {} -> {}", taskId, oldPriority, newPriority);
    }

    @Transactional
    public Task assignTask(String taskId, String agvId, String path, String operator) {
        Task task = getTaskById(taskId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new IllegalStateException("任务状态不允许分配: " + task.getStatus());
        }

        Agv agv = agvRepository.findById(agvId)
                .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));
        if (!agv.getStatus().isAvailable()) {
            throw new IllegalStateException("AGV状态不可用: " + agv.getStatus());
        }

        if (path == null || path.isEmpty()) {
            List<String> plannedPath = pathPlanningService.planPath(
                    agv.getCurrentPosition(), task.getStartPoint());
            List<String> taskPath = pathPlanningService.planPath(
                    task.getStartPoint(), task.getEndPoint());

            List<String> fullPath = new ArrayList<>(plannedPath);
            if (!taskPath.isEmpty()) {
                fullPath.addAll(taskPath.subList(1, taskPath.size()));
            }
            path = pathPlanningService.encodePath(fullPath);
        }

        task.setAgvId(agvId);
        task.setStatus(TaskStatus.ASSIGNED);
        task.setPath(path);
        task.setAssignedTime(LocalDateTime.now());
        task.setCurrentStep(0);
        Task saved = taskRepository.save(task);

        agv.setStatus(AgvStatus.WORKING);
        agv.setCurrentTaskId(taskId);
        agvRepository.save(agv);

        removeFromTaskQueue(taskId);

        List<String> pathList = pathPlanningService.decodePath(path);
        pathPlanningService.occupyPath(agvId, pathList, 0);

        recordTaskLog(taskId, agvId, "分配任务",
                TaskStatus.PENDING.getDesc(), TaskStatus.ASSIGNED.getDesc(),
                "分配给AGV: " + agv.getAgvNo(), operator);

        log.info("任务分配成功: taskId={}, agvId={}", taskId, agvId);
        return saved;
    }

    @Transactional
    public Task autoAssignTask(String taskId, String operator) {
        Task task = getTaskById(taskId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new IllegalStateException("任务状态不允许分配: " + task.getStatus());
        }

        MapNode startNode = mapNodeRepository.findByNodeCode(task.getStartPoint())
                .orElseThrow(() -> new IllegalArgumentException("起点不存在: " + task.getStartPoint()));

        List<Agv> suitableAgvs = agvRepository.findSuitableAgvs(
                task.getLoadWeight(), startNode.getXCoord(), startNode.getYCoord());

        if (suitableAgvs.isEmpty()) {
            throw new IllegalStateException("没有可用的AGV");
        }

        Agv selectedAgv = suitableAgvs.get(0);

        for (Agv agv : suitableAgvs) {
            List<String> path = pathPlanningService.planPath(
                    agv.getCurrentPosition(), task.getStartPoint());
            if (!path.isEmpty() && pathPlanningService.isPathAvailable(path)) {
                selectedAgv = agv;
                break;
            }
        }

        return assignTask(taskId, selectedAgv.getId(), null, operator);
    }

    @Scheduled(fixedDelay = 5000)
    public void autoDispatch() {
        try {
            String lockValue = UUID.randomUUID().toString();
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(DISPATCH_LOCK_KEY, lockValue, DISPATCH_LOCK_SECONDS, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(locked)) {
                return;
            }

            try {
                ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
                Set<String> taskIds = zSetOps.reverseRange(TASK_QUEUE_KEY, 0, -1);

                if (taskIds == null || taskIds.isEmpty()) {
                    return;
                }

                List<Agv> availableAgvs = agvRepository.findAvailableAgvsOrdered();
                if (availableAgvs.isEmpty()) {
                    return;
                }

                List<String> processedTasks = new ArrayList<>();

                for (String taskId : taskIds) {
                    if (availableAgvs.isEmpty()) {
                        break;
                    }

                    Task task = getTaskById(taskId);
                    if (task.getStatus() != TaskStatus.PENDING) {
                        processedTasks.add(taskId);
                        continue;
                    }

                    try {
                        Agv selectedAgv = selectBestAgv(task, availableAgvs);
                        if (selectedAgv != null) {
                            assignTask(taskId, selectedAgv.getId(), null, "system");
                            availableAgvs.remove(selectedAgv);
                            processedTasks.add(taskId);
                        }
                    } catch (Exception e) {
                        log.warn("自动分配任务失败: taskId={}, error={}", taskId, e.getMessage());
                    }
                }

                for (String processedId : processedTasks) {
                    zSetOps.remove(TASK_QUEUE_KEY, processedId);
                }

            } finally {
                String currentLock = redisTemplate.opsForValue().get(DISPATCH_LOCK_KEY);
                if (lockValue.equals(currentLock)) {
                    redisTemplate.delete(DISPATCH_LOCK_KEY);
                }
            }
        } catch (Exception e) {
            log.error("自动调度异常", e);
        }
    }

    private Agv selectBestAgv(Task task, List<Agv> availableAgvs) {
        for (Agv agv : availableAgvs) {
            List<String> path = pathPlanningService.planPath(
                    agv.getCurrentPosition(), task.getStartPoint());
            if (!path.isEmpty() && pathPlanningService.isPathAvailable(path)) {
                return agv;
            }
        }

        MapNode startNode = mapNodeRepository.findByNodeCode(task.getStartPoint()).orElse(null);
        if (startNode == null) {
            return availableAgvs.isEmpty() ? null : availableAgvs.get(0);
        }

        Agv nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (Agv agv : availableAgvs) {
            if (agv.getXCoord() != null && agv.getYCoord() != null) {
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

    @Scheduled(fixedDelay = 3000)
    public void detectAndResolveConflicts() {
        conflictDetectionService.detectConflicts();
        conflictDetectionService.resolveAllConflicts();
    }

    @Transactional
    public void updateTaskStatus(String taskId, TaskStatus newStatus, String remark, String operator) {
        Task task = getTaskById(taskId);
        TaskStatus oldStatus = task.getStatus();

        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("状态不允许转换: %s -> %s", oldStatus, newStatus));
        }

        task.setStatus(newStatus);

        if (newStatus == TaskStatus.EXECUTING) {
            task.setStartTime(LocalDateTime.now());
        } else if (newStatus == TaskStatus.COMPLETED) {
            task.setCompletedTime(LocalDateTime.now());
            if (task.getAgvId() != null) {
                releaseAgv(task.getAgvId());
            }
        } else if (newStatus == TaskStatus.CANCELLED) {
            if (task.getAgvId() != null) {
                releaseAgv(task.getAgvId());
                pathPlanningService.releaseAllPath(task.getAgvId());
            }
        } else if (newStatus == TaskStatus.ABNORMAL) {
            if (task.getAgvId() != null) {
                Agv agv = agvRepository.findById(task.getAgvId()).orElse(null);
                if (agv != null) {
                    agv.setStatus(AgvStatus.FAULT);
                    agvRepository.save(agv);
                }
                pathPlanningService.releaseAllPath(task.getAgvId());
            }
        } else if (newStatus == TaskStatus.PENDING && task.getAgvId() != null) {
            releaseAgv(task.getAgvId());
            pathPlanningService.releaseAllPath(task.getAgvId());
            task.setAgvId(null);
        }

        taskRepository.save(task);

        recordTaskLog(taskId, task.getAgvId(), "状态更新",
                oldStatus.getDesc(), newStatus.getDesc(), remark, operator);

        if (newStatus == TaskStatus.PENDING) {
            addToTaskQueue(task);
        } else {
            removeFromTaskQueue(taskId);
        }

        cacheTask(task);

        log.info("任务状态更新: taskId={}, {} -> {}", taskId, oldStatus, newStatus);
    }

    private void releaseAgv(String agvId) {
        Agv agv = agvRepository.findById(agvId).orElse(null);
        if (agv != null) {
            agv.setStatus(AgvStatus.IDLE);
            agv.setCurrentTaskId(null);
            agvRepository.save(agv);
        }
    }

    @Transactional
    public void cancelTask(String taskId, String reason, String operator) {
        Task task = getTaskById(taskId);
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("任务已完成或已取消");
        }

        updateTaskStatus(taskId, TaskStatus.CANCELLED, reason, operator);
        removeFromTaskQueue(taskId);
    }

    @Transactional
    public Task reassignTask(String taskId, String targetAgvId, String reason, String operator) {
        Task task = getTaskById(taskId);
        String oldAgvId = task.getAgvId();

        if (task.getStatus() != TaskStatus.ASSIGNED &&
            task.getStatus() != TaskStatus.EXECUTING &&
            task.getStatus() != TaskStatus.ABNORMAL) {
            throw new IllegalStateException("任务状态不允许重分配: " + task.getStatus());
        }

        if (oldAgvId != null) {
            releaseAgv(oldAgvId);
            pathPlanningService.releaseAllPath(oldAgvId);
        }

        task.setStatus(TaskStatus.PENDING);
        task.setAgvId(null);
        task.setAssignedTime(null);
        task.setCurrentStep(0);
        task.setPath(null);
        taskRepository.save(task);

        addToTaskQueue(task);

        recordTaskLog(taskId, oldAgvId, "任务重分配",
                task.getStatus().getDesc(), TaskStatus.PENDING.getDesc(),
                reason != null ? reason : "AGV故障或路径阻塞，返回待分配队列", operator);

        if (targetAgvId != null && !targetAgvId.isEmpty()) {
            return assignTask(taskId, targetAgvId, null, operator);
        }

        log.info("任务已返回待分配队列: taskId={}", taskId);
        return task;
    }

    public void updateTaskProgress(String taskId, int step, String currentPosition) {
        Task task = getTaskById(taskId);
        if (task.getStatus() != TaskStatus.EXECUTING && task.getStatus() != TaskStatus.ASSIGNED) {
            return;
        }

        if (task.getStatus() == TaskStatus.ASSIGNED) {
            updateTaskStatus(taskId, TaskStatus.EXECUTING, "开始执行", "system");
        }

        task.setCurrentStep(step);

        List<String> path = pathPlanningService.decodePath(task.getPath());
        if (!path.isEmpty()) {
            pathPlanningService.releasePath(task.getAgvId(), path, step);
            pathPlanningService.occupyPath(task.getAgvId(), path, step);
        }

        if (task.getAgvId() != null) {
            Agv agv = agvRepository.findById(task.getAgvId()).orElse(null);
            if (agv != null) {
                agv.setCurrentPosition(currentPosition);
                mapNodeRepository.findByNodeCode(currentPosition).ifPresent(node -> {
                    agv.setXCoord(node.getXCoord());
                    agv.setYCoord(node.getYCoord());
                });
                agvRepository.save(agv);
            }
        }

        if (!path.isEmpty() && step >= path.size() - 1) {
            updateTaskStatus(taskId, TaskStatus.COMPLETED, "到达终点", "system");
        } else {
            taskRepository.save(task);
        }

        cacheTask(task);
    }

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

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        cacheTask(task);
        return task;
    }

    private void cacheTask(Task task) {
        String cacheKey = TASK_PREFIX + task.getId();
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(task),
                TASK_CACHE_SECONDS, TimeUnit.SECONDS);
    }

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

    public List<TaskLog> getTaskLogs(String taskId) {
        return taskLogRepository.findByTaskIdOrderByCreateTimeDesc(taskId);
    }

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

    public List<Task> getPendingQueue() {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Set<String> taskIds = zSetOps.reverseRange(TASK_QUEUE_KEY, 0, -1);

        List<Task> tasks = new ArrayList<>();
        if (taskIds != null) {
            for (String taskId : taskIds) {
                try {
                    Task task = getTaskById(taskId);
                    if (task.getStatus() == TaskStatus.PENDING) {
                        tasks.add(task);
                    }
                } catch (Exception e) {
                    zSetOps.remove(TASK_QUEUE_KEY, taskId);
                }
            }
        }

        tasks.sort(taskQueueComparator);
        return tasks;
    }

    public void refreshQueue() {
        List<Task> pendingTasks = taskRepository.findPendingTasksOrdered(TaskStatus.PENDING);
        redisTemplate.delete(TASK_QUEUE_KEY);
        for (Task task : pendingTasks) {
            addToTaskQueue(task);
        }
        log.info("任务队列已刷新，共{}个任务", pendingTasks.size());
    }

    public long getQueueSize() {
        Long size = redisTemplate.opsForZSet().size(TASK_QUEUE_KEY);
        return size != null ? size : 0;
    }

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
}

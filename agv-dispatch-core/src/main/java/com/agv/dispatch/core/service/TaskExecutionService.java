package com.agv.dispatch.core.service;

import com.agv.dispatch.common.constant.RedisKeyConstant;
import com.agv.dispatch.common.dto.AgvRemoteControlDTO;
import com.agv.dispatch.common.dto.PathPlanningResult;
import com.agv.dispatch.common.dto.TaskDispatchDTO;
import com.agv.dispatch.common.dto.TaskExecutionFeedbackDTO;
import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.AlarmRecord;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.enums.AlarmType;
import com.agv.dispatch.common.enums.TaskExecutionStatus;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.common.util.JsonUtil;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.repository.AlarmRecordRepository;
import com.agv.dispatch.core.repository.TaskRepository;
import com.agv.dispatch.mqtt.service.MqttMessageSender;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.agv.dispatch.common.constant.RedisKeyConstant.*;

/**
 * 任务执行服务
 * 负责任务的下发、执行跟踪、超时监控和状态管理
 *
 * 主要功能：
 * 1. 任务下发 - 将目标点与路径点序列下发给AGV
 * 2. 执行反馈 - 接收AGV的执行反馈，更新任务状态和进度
 * 3. 超时监控 - 任务执行超时或节点占用超时触发告警与重新调度
 * 4. 手动干预 - 支持暂停、恢复、取消任务，远程控制AGV
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    /**
     * 任务数据访问层
     */
    private final TaskRepository taskRepository;

    /**
     * AGV数据访问层
     */
    private final AgvRepository agvRepository;

    /**
     * 告警记录数据访问层
     */
    private final AlarmRecordRepository alarmRecordRepository;

    /**
     * 路径规划服务
     */
    private final PathPlanningService pathPlanningService;

    /**
     * 任务调度服务
     */
    private final TaskDispatchService taskDispatchService;

    /**
     * MQTT消息发送服务
     */
    private final MqttMessageSender mqttMessageSender;

    /**
     * Redis模板
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 默认任务超时时间（秒）
     */
    private static final int DEFAULT_TASK_TIMEOUT_SECONDS = 1800;

    /**
     * 默认节点占用超时时间（秒）
     */
    private static final int DEFAULT_NODE_TIMEOUT_SECONDS = 120;

    /**
     * 心跳超时时间（秒）
     */
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 60;

    // ==================== 任务下发 ====================

    /**
     * 下发任务给AGV
     * 将任务信息和路径点序列通过MQTT下发给指定AGV
     *
     * 业务流程：
     * 1. 校验任务状态和AGV状态
     * 2. 构建TaskDispatchDTO，包含路径点序列
     * 3. 通过MQTT发送任务下发消息
     * 4. 更新任务状态为已下发，记录下发时间
     * 5. 设置Redis缓存，记录AGV当前任务
     *
     * @param task 任务实体
     * @return 下发是否成功
     */
    @Transactional
    public boolean dispatchTask(Task task) {
        try {
            if (task == null) {
                throw new IllegalArgumentException("任务不能为空");
            }
            if (task.getStatus() != TaskStatus.ASSIGNED) {
                throw new IllegalStateException("任务状态不正确，无法下发: " + task.getStatus());
            }
            if (task.getAgvId() == null) {
                throw new IllegalStateException("任务未分配AGV，无法下发");
            }

            String taskId = task.getId();
            String agvId = task.getAgvId();
            String lockKey = TASK_DISPATCH_LOCK_PREFIX + taskId;

            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", TASK_DISPATCH_LOCK_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(locked)) {
                log.warn("任务下发锁已存在，跳过重复下发: taskId={}", taskId);
                return false;
            }

            try {
                Agv agv = agvRepository.findById(agvId)
                        .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvId));

                List<String> pathPoints = decodePath(task.getPath());
                if (pathPoints.isEmpty()) {
                    PathPlanningResult planResult = pathPlanningService.planPathWithAlgorithm(
                            task.getStartPoint(), task.getEndPoint(), "A*");
                    if (!planResult.isSuccess()) {
                        throw new IllegalStateException("无法规划任务路径: " + planResult.getMessage());
                    }
                    pathPoints = planResult.getPath();
                    task.setPath(JSON.toJSONString(pathPoints));
                    task.setTotalDistance(planResult.getTotalDistance());
                    task.setEstimatedTime(planResult.getEstimatedTime());
                }

                TaskDispatchDTO dispatchDTO = buildTaskDispatchDTO(task, agv, pathPoints);
                mqttMessageSender.dispatchTask(dispatchDTO);

                task.setExecutionStatus(TaskExecutionStatus.DISPATCHED);
                task.setDispatchedTime(LocalDateTime.now());
                task.setTotalSteps(pathPoints.size());
                if (task.getTimeoutSeconds() == null) {
                    task.setTimeoutSeconds(DEFAULT_TASK_TIMEOUT_SECONDS);
                }
                if (task.getNodeTimeoutSeconds() == null) {
                    task.setNodeTimeoutSeconds(DEFAULT_NODE_TIMEOUT_SECONDS);
                }
                if (task.getEstimatedCompleteTime() == null && task.getEstimatedTime() != null) {
                    task.setEstimatedCompleteTime(LocalDateTime.now()
                            .plusSeconds(task.getEstimatedTime().longValue()));
                }
                taskRepository.save(task);

                String agvCurrentTaskKey = AGV_CURRENT_TASK_PREFIX + agv.getAgvNo();
                redisTemplate.opsForValue().set(agvCurrentTaskKey, taskId,
                        AGV_CURRENT_TASK_SECONDS, TimeUnit.SECONDS);

                String taskExecutionKey = TASK_EXECUTION_PREFIX + taskId;
                redisTemplate.opsForValue().set(taskExecutionKey, JSON.toJSONString(task),
                        TASK_EXECUTION_SECONDS, TimeUnit.SECONDS);

                taskDispatchService.recordTaskLog(taskId, agvId, "下发任务",
                        TaskStatus.ASSIGNED.getDesc(), TaskExecutionStatus.DISPATCHED.getDesc(),
                        "任务已下发给AGV: " + agv.getAgvNo(), "system");

                log.info("任务下发成功: taskId={}, taskNo={}, agvNo={}, pathPoints={}",
                        taskId, task.getTaskNo(), agv.getAgvNo(), pathPoints.size());
                return true;

            } finally {
                redisTemplate.delete(lockKey);
            }

        } catch (Exception e) {
            log.error("任务下发失败: taskId={}", task != null ? task.getId() : null, e);
            createAlarm(AlarmType.TASK_FAILED, task != null ? task.getId() : null,
                    task != null ? task.getAgvId() : null, null,
                    "任务下发失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 构建任务下发DTO
     *
     * @param task 任务实体
     * @param agv AGV实体
     * @param pathPoints 路径点序列
     * @return 任务下发DTO
     */
    private TaskDispatchDTO buildTaskDispatchDTO(Task task, Agv agv, List<String> pathPoints) {
        TaskDispatchDTO dto = new TaskDispatchDTO();
        dto.setTaskId(task.getId());
        dto.setTaskNo(task.getTaskNo());
        dto.setTaskType(task.getTaskType() != null ? task.getTaskType().name() : null);
        dto.setAgvNo(agv.getAgvNo());
        dto.setStartPoint(task.getStartPoint());
        dto.setEndPoint(task.getEndPoint());
        dto.setPathPoints(pathPoints);
        dto.setEstimatedTime(task.getEstimatedTime());
        dto.setTotalDistance(task.getTotalDistance());
        dto.setCargoInfo(task.getCargoInfo());
        dto.setLoadWeight(task.getLoadWeight());
        dto.setDeadline(task.getDeadline() != null ? task.getDeadline().toString() : null);
        dto.setDispatchTime(LocalDateTime.now().toString());
        dto.setPriority(task.getPriority() != null ? task.getPriority().name() : null);
        return dto;
    }

    // ==================== 执行反馈处理 ====================

    /**
     * 处理AGV执行反馈
     * 接收AGV上报的执行状态和进度，更新任务状态
     *
     * 业务流程：
     * 1. 根据action类型进行不同处理
     * 2. START - 任务开始执行
     * 3. PROGRESS - 进度更新
     * 4. ARRIVED - 到达节点，更新当前位置
     * 5. WORKING - 作业中（装卸货等）
     * 6. COMPLETE - 任务完成
     * 7. ABNORMAL - 执行异常，触发告警
     * 8. PAUSE/RESUME - 暂停/恢复
     *
     * @param feedbackDTO 执行反馈DTO
     */
    @Transactional
    public void handleExecutionFeedback(TaskExecutionFeedbackDTO feedbackDTO) {
        try {
            if (feedbackDTO == null || feedbackDTO.getTaskId() == null) {
                log.warn("执行反馈参数为空，跳过处理");
                return;
            }

            String taskId = feedbackDTO.getTaskId();
            String action = feedbackDTO.getAction();
            String agvNo = feedbackDTO.getAgvNo();

            Task task = taskRepository.findById(taskId).orElse(null);
            if (task == null) {
                log.warn("收到未知任务的执行反馈: taskId={}, action={}", taskId, action);
                return;
            }

            String agvId = task.getAgvId();

            switch (action.toUpperCase()) {
                case "START":
                    handleTaskStart(task, feedbackDTO);
                    break;
                case "PROGRESS":
                    handleTaskProgress(task, feedbackDTO);
                    break;
                case "ARRIVED":
                    handleNodeArrived(task, feedbackDTO);
                    break;
                case "WORKING":
                    handleTaskWorking(task, feedbackDTO);
                    break;
                case "COMPLETE":
                    handleTaskComplete(task, feedbackDTO);
                    break;
                case "ABNORMAL":
                case "FAILED":
                    handleTaskAbnormal(task, feedbackDTO);
                    break;
                case "PAUSE":
                    handleTaskPause(task, feedbackDTO);
                    break;
                case "RESUME":
                    handleTaskResume(task, feedbackDTO);
                    break;
                default:
                    log.debug("未处理的执行反馈动作: taskId={}, action={}", taskId, action);
            }

            updateTaskExecutionCache(task);
            updateAgvCurrentTaskCache(agvNo, taskId);

        } catch (Exception e) {
            log.error("处理执行反馈异常: taskId={}, action={}",
                    feedbackDTO != null ? feedbackDTO.getTaskId() : null,
                    feedbackDTO != null ? feedbackDTO.getAction() : null, e);
        }
    }

    /**
     * 处理任务开始执行
     */
    private void handleTaskStart(Task task, TaskExecutionFeedbackDTO feedback) {
        task.setExecutionStatus(TaskExecutionStatus.EXECUTING);
        task.setStatus(TaskStatus.EXECUTING);
        task.setStartTime(LocalDateTime.now());
        task.setCurrentStep(0);
        task.setCurrentNode(task.getStartPoint());
        taskRepository.save(task);

        if (feedback.getCurrentNode() != null) {
            recordNodeOccupancyTime(feedback.getCurrentNode());
        }

        taskDispatchService.recordTaskLog(task.getId(), task.getAgvId(), "开始执行",
                TaskExecutionStatus.DISPATCHED.getDesc(), TaskExecutionStatus.EXECUTING.getDesc(),
                "AGV开始执行任务", "mqtt:" + feedback.getAgvNo());

        log.info("任务开始执行: taskId={}, agvNo={}", task.getId(), feedback.getAgvNo());
    }

    /**
     * 处理任务进度更新
     */
    private void handleTaskProgress(Task task, TaskExecutionFeedbackDTO feedback) {
        if (feedback.getCurrentStep() != null) {
            task.setCurrentStep(feedback.getCurrentStep());
        }
        if (feedback.getCurrentNode() != null) {
            task.setCurrentNode(feedback.getCurrentNode());
        }
        task.setExecutionStatus(TaskExecutionStatus.EXECUTING);
        taskRepository.save(task);

        log.debug("任务进度更新: taskId={}, step={}, node={}",
                task.getId(), feedback.getCurrentStep(), feedback.getCurrentNode());
    }

    /**
     * 处理到达节点
     */
    private void handleNodeArrived(Task task, TaskExecutionFeedbackDTO feedback) {
        String arrivedNode = feedback.getArrivedNode();
        if (arrivedNode == null) {
            arrivedNode = feedback.getCurrentNode();
        }

        if (arrivedNode != null) {
            String previousNode = task.getCurrentNode();
            if (previousNode != null) {
                clearNodeOccupancyTime(previousNode);
                pathPlanningService.unlockNode(previousNode, task.getAgvId());
            }

            task.setCurrentNode(arrivedNode);
            task.setLastNodeArrivalTime(LocalDateTime.now());
            recordNodeOccupancyTime(arrivedNode);
            pathPlanningService.tryLockNode(arrivedNode, task.getAgvId());

            if (feedback.getCurrentStep() != null) {
                task.setCurrentStep(feedback.getCurrentStep());
            } else if (task.getCurrentStep() != null) {
                task.setCurrentStep(task.getCurrentStep() + 1);
            }

            task.setExecutionStatus(TaskExecutionStatus.ARRIVED_NODE);
            taskRepository.save(task);

            taskDispatchService.recordTaskLog(task.getId(), task.getAgvId(), "到达节点",
                    previousNode, arrivedNode,
                    "AGV到达节点: " + arrivedNode, "mqtt:" + feedback.getAgvNo());

            log.info("AGV到达节点: taskId={}, agvNo={}, node={}, step={}",
                    task.getId(), feedback.getAgvNo(), arrivedNode, task.getCurrentStep());
        }
    }

    /**
     * 处理任务作业中
     */
    private void handleTaskWorking(Task task, TaskExecutionFeedbackDTO feedback) {
        task.setExecutionStatus(TaskExecutionStatus.WORKING);
        taskRepository.save(task);

        log.debug("任务作业中: taskId={}, agvNo={}", task.getId(), feedback.getAgvNo());
    }

    /**
     * 处理任务完成
     */
    private void handleTaskComplete(Task task, TaskExecutionFeedbackDTO feedback) {
        String agvId = task.getAgvId();
        String currentNode = task.getCurrentNode();

        if (currentNode != null) {
            clearNodeOccupancyTime(currentNode);
            pathPlanningService.unlockNode(currentNode, agvId);
        }

        task.setExecutionStatus(TaskExecutionStatus.COMPLETED);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedTime(LocalDateTime.now());
        if (task.getTotalSteps() != null) {
            task.setCurrentStep(task.getTotalSteps());
        }
        taskRepository.save(task);

        String agvNo = feedback.getAgvNo();
        clearAgvCurrentTaskCache(agvNo);
        clearTaskExecutionCache(task.getId());

        taskDispatchService.recordTaskLog(task.getId(), agvId, "任务完成",
                TaskExecutionStatus.EXECUTING.getDesc(), TaskExecutionStatus.COMPLETED.getDesc(),
                "AGV完成任务执行", "mqtt:" + agvNo);

        log.info("任务执行完成: taskId={}, taskNo={}, agvNo={}, duration={}s",
                task.getId(), task.getTaskNo(), agvNo,
                task.getStartTime() != null && task.getCompletedTime() != null
                        ? Duration.between(task.getStartTime(), task.getCompletedTime()).getSeconds()
                        : 0);
    }

    /**
     * 处理任务执行异常
     */
    private void handleTaskAbnormal(Task task, TaskExecutionFeedbackDTO feedback) {
        task.setExecutionStatus(TaskExecutionStatus.FAILED);
        task.setStatus(TaskStatus.ABNORMAL);
        taskRepository.save(task);

        String errorMsg = feedback.getErrorMessage() != null
                ? feedback.getErrorMessage()
                : "AGV上报执行异常";
        String errorCode = feedback.getErrorCode();

        createAlarm(AlarmType.TASK_FAILED, task.getId(), task.getAgvId(),
                task.getCurrentNode(), errorMsg + (errorCode != null ? " (" + errorCode + ")" : ""));

        taskDispatchService.recordTaskLog(task.getId(), task.getAgvId(), "执行异常",
                task.getExecutionStatus() != null ? task.getExecutionStatus().getDesc() : null,
                TaskExecutionStatus.FAILED.getDesc(),
                errorMsg, "mqtt:" + feedback.getAgvNo());

        log.error("任务执行异常: taskId={}, agvNo={}, errorCode={}, errorMsg={}",
                task.getId(), feedback.getAgvNo(), errorCode, errorMsg);
    }

    /**
     * 处理任务暂停
     */
    private void handleTaskPause(Task task, TaskExecutionFeedbackDTO feedback) {
        task.setExecutionStatus(TaskExecutionStatus.PAUSED);
        task.setPausedTime(LocalDateTime.now());
        taskRepository.save(task);

        taskDispatchService.recordTaskLog(task.getId(), task.getAgvId(), "任务暂停",
                TaskExecutionStatus.EXECUTING.getDesc(), TaskExecutionStatus.PAUSED.getDesc(),
                "AGV暂停任务执行", "mqtt:" + feedback.getAgvNo());

        log.info("任务已暂停: taskId={}, agvNo={}", task.getId(), feedback.getAgvNo());
    }

    /**
     * 处理任务恢复
     */
    private void handleTaskResume(Task task, TaskExecutionFeedbackDTO feedback) {
        task.setExecutionStatus(TaskExecutionStatus.EXECUTING);
        taskRepository.save(task);

        if (task.getEstimatedCompleteTime() != null && task.getPausedTime() != null) {
            Duration pauseDuration = Duration.between(task.getPausedTime(), LocalDateTime.now());
            task.setEstimatedCompleteTime(task.getEstimatedCompleteTime().plus(pauseDuration));
            taskRepository.save(task);
        }
        task.setPausedTime(null);

        taskDispatchService.recordTaskLog(task.getId(), task.getAgvId(), "任务恢复",
                TaskExecutionStatus.PAUSED.getDesc(), TaskExecutionStatus.EXECUTING.getDesc(),
                "AGV恢复任务执行", "mqtt:" + feedback.getAgvNo());

        log.info("任务已恢复: taskId={}, agvNo={}", task.getId(), feedback.getAgvNo());
    }

    // ==================== 手动干预 ====================

    /**
     * 暂停任务
     * 发送暂停命令给AGV，更新任务状态
     *
     * @param taskId 任务ID
     * @param operator 操作人
     * @param reason 暂停原因
     * @return 操作是否成功
     */
    @Transactional
    public boolean pauseTask(String taskId, String operator, String reason) {
        try {
            Task task = getTaskById(taskId);
            if (task.getStatus() != TaskStatus.EXECUTING) {
                throw new IllegalStateException("只有执行中的任务才能暂停，当前状态: " + task.getStatus());
            }

            Agv agv = agvRepository.findById(task.getAgvId())
                    .orElseThrow(() -> new IllegalArgumentException("AGV不存在"));

            mqttMessageSender.pauseTask(agv.getAgvNo(), taskId);

            task.setExecutionStatus(TaskExecutionStatus.PAUSED);
            task.setPausedTime(LocalDateTime.now());
            taskRepository.save(task);

            taskDispatchService.recordTaskLog(taskId, task.getAgvId(), "手动暂停",
                    TaskExecutionStatus.EXECUTING.getDesc(), TaskExecutionStatus.PAUSED.getDesc(),
                    reason != null ? reason : "手动暂停任务", operator);

            log.info("手动暂停任务成功: taskId={}, operator={}, reason={}", taskId, operator, reason);
            return true;

        } catch (Exception e) {
            log.error("暂停任务失败: taskId={}", taskId, e);
            return false;
        }
    }

    /**
     * 恢复任务
     * 发送恢复命令给AGV，更新任务状态
     *
     * @param taskId 任务ID
     * @param operator 操作人
     * @return 操作是否成功
     */
    @Transactional
    public boolean resumeTask(String taskId, String operator) {
        try {
            Task task = getTaskById(taskId);
            if (task.getExecutionStatus() != TaskExecutionStatus.PAUSED) {
                throw new IllegalStateException("只有暂停的任务才能恢复，当前执行状态: " + task.getExecutionStatus());
            }

            Agv agv = agvRepository.findById(task.getAgvId())
                    .orElseThrow(() -> new IllegalArgumentException("AGV不存在"));

            mqttMessageSender.resumeTask(agv.getAgvNo(), taskId);

            task.setExecutionStatus(TaskExecutionStatus.EXECUTING);
            task.setPausedTime(null);
            taskRepository.save(task);

            taskDispatchService.recordTaskLog(taskId, task.getAgvId(), "手动恢复",
                    TaskExecutionStatus.PAUSED.getDesc(), TaskExecutionStatus.EXECUTING.getDesc(),
                    "手动恢复任务执行", operator);

            log.info("手动恢复任务成功: taskId={}, operator={}", taskId, operator);
            return true;

        } catch (Exception e) {
            log.error("恢复任务失败: taskId={}", taskId, e);
            return false;
        }
    }

    /**
     * 取消任务
     * 发送取消命令给AGV，更新任务状态，释放资源
     *
     * @param taskId 任务ID
     * @param operator 操作人
     * @param reason 取消原因
     * @return 操作是否成功
     */
    @Transactional
    public boolean cancelTask(String taskId, String operator, String reason) {
        try {
            Task task = getTaskById(taskId);
            if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
                throw new IllegalStateException("任务已完成或已取消，无法重复取消");
            }

            String agvId = task.getAgvId();
            String currentNode = task.getCurrentNode();

            if (agvId != null) {
                Agv agv = agvRepository.findById(agvId).orElse(null);
                if (agv != null) {
                    mqttMessageSender.cancelTask(agv.getAgvNo(), taskId, reason);
                    clearAgvCurrentTaskCache(agv.getAgvNo());
                }
            }

            if (currentNode != null) {
                clearNodeOccupancyTime(currentNode);
                pathPlanningService.unlockNode(currentNode, agvId);
            }

            task.setExecutionStatus(TaskExecutionStatus.CANCELLED);
            task.setStatus(TaskStatus.CANCELLED);
            taskRepository.save(task);

            clearTaskExecutionCache(taskId);

            taskDispatchService.recordTaskLog(taskId, agvId, "手动取消",
                    task.getStatus() != null ? task.getStatus().getDesc() : null,
                    TaskStatus.CANCELLED.getDesc(),
                    reason != null ? reason : "手动取消任务", operator);

            log.info("手动取消任务成功: taskId={}, operator={}, reason={}", taskId, operator, reason);
            return true;

        } catch (Exception e) {
            log.error("取消任务失败: taskId={}", taskId, e);
            return false;
        }
    }

    /**
     * 远程控制AGV
     * 发送控制命令给指定AGV
     *
     * @param controlDTO 控制参数DTO
     * @return 操作是否成功
     */
    public boolean remoteControlAgv(AgvRemoteControlDTO controlDTO) {
        try {
            if (controlDTO == null || controlDTO.getAgvNo() == null) {
                throw new IllegalArgumentException("控制参数不能为空");
            }

            String agvNo = controlDTO.getAgvNo();
            Agv agv = agvRepository.findByAgvNo(agvNo)
                    .orElseThrow(() -> new IllegalArgumentException("AGV不存在: " + agvNo));

            mqttMessageSender.sendControlCommand(agvNo, controlDTO);

            log.info("远程控制AGV成功: agvNo={}, command={}, operator={}",
                    agvNo, controlDTO.getCommand(), controlDTO.getOperator());
            return true;

        } catch (Exception e) {
            log.error("远程控制AGV失败: agvNo={}, command={}",
                    controlDTO != null ? controlDTO.getAgvNo() : null,
                    controlDTO != null ? controlDTO.getCommand() : null, e);
            return false;
        }
    }

    // ==================== 超时监控 ====================

    /**
     * 定时超时监控
     * 每10秒检测一次任务执行超时和节点占用超时
     *
     * 检测内容：
     * 1. 任务执行超时 - 执行时间超过预计完成时间
     * 2. 节点占用超时 - AGV在某个节点停留时间过长
     * 3. AGV心跳超时 - AGV超过指定时间未上报心跳
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void scheduledTimeoutMonitor() {
        try {
            checkTaskTimeout();
            checkNodeOccupancyTimeout();
            checkHeartbeatTimeout();
        } catch (Exception e) {
            log.error("定时超时监控异常", e);
        }
    }

    /**
     * 检测任务执行超时
     */
    private void checkTaskTimeout() {
        List<TaskStatus> executingStatuses = Arrays.asList(TaskStatus.EXECUTING, TaskStatus.ASSIGNED);
        List<Task> executingTasks = taskRepository.findByStatusInAndAgvIdIsNotNull(executingStatuses);

        for (Task task : executingTasks) {
            if (task.isTimeout()) {
                if (task.getExecutionStatus() != TaskExecutionStatus.TIMEOUT) {
                    handleTaskTimeout(task);
                }
            }
        }
    }

    /**
     * 处理任务超时
     */
    private void handleTaskTimeout(Task task) {
        task.setExecutionStatus(TaskExecutionStatus.TIMEOUT);
        taskRepository.save(task);

        String timeoutDuration = task.getStartTime() != null && task.getEstimatedCompleteTime() != null
                ? Duration.between(task.getEstimatedCompleteTime(), LocalDateTime.now()).getSeconds() + "s"
                : "未知";

        createAlarm(AlarmType.TASK_TIMEOUT, task.getId(), task.getAgvId(),
                task.getCurrentNode(),
                "任务执行超时，预计完成时间: " + task.getEstimatedCompleteTime()
                        + ", 已超时: " + timeoutDuration);

        taskDispatchService.recordTaskLog(task.getId(), task.getAgvId(), "执行超时",
                task.getExecutionStatus() != null ? task.getExecutionStatus().getDesc() : null,
                TaskExecutionStatus.TIMEOUT.getDesc(),
                "任务执行超时，已超时: " + timeoutDuration, "system");

        log.warn("任务执行超时: taskId={}, agvId={}, estimatedCompleteTime={}",
                task.getId(), task.getAgvId(), task.getEstimatedCompleteTime());
    }

    /**
     * 检测节点占用超时
     */
    private void checkNodeOccupancyTimeout() {
        List<TaskStatus> executingStatuses = Arrays.asList(TaskStatus.EXECUTING);
        List<Task> executingTasks = taskRepository.findByStatusInAndAgvIdIsNotNull(executingStatuses);

        for (Task task : executingTasks) {
            String currentNode = task.getCurrentNode();
            if (currentNode == null) {
                continue;
            }

            LocalDateTime occupancyTime = getNodeOccupancyTime(currentNode);
            if (occupancyTime == null) {
                continue;
            }

            Integer nodeTimeout = task.getNodeTimeoutSeconds();
            if (nodeTimeout == null) {
                nodeTimeout = DEFAULT_NODE_TIMEOUT_SECONDS;
            }

            long occupiedSeconds = Duration.between(occupancyTime, LocalDateTime.now()).getSeconds();
            if (occupiedSeconds > nodeTimeout) {
                handleNodeOccupancyTimeout(task, currentNode, occupiedSeconds);
            }
        }
    }

    /**
     * 处理节点占用超时
     */
    private void handleNodeOccupancyTimeout(Task task, String node, long occupiedSeconds) {
        createAlarm(AlarmType.NODE_OCCUPANCY_TIMEOUT, task.getId(), task.getAgvId(), node,
                "AGV在节点 " + node + " 停留时间过长，已停留: " + occupiedSeconds + "s");

        log.warn("节点占用超时: taskId={}, agvId={}, node={}, occupiedSeconds={}",
                task.getId(), task.getAgvId(), node, occupiedSeconds);
    }

    /**
     * 检测AGV心跳超时
     */
    private void checkHeartbeatTimeout() {
        List<Agv> onlineAgvs = agvRepository.findByStatusIn(Arrays.asList(
                com.agv.dispatch.common.enums.AgvStatus.IDLE,
                com.agv.dispatch.common.enums.AgvStatus.EXECUTING,
                com.agv.dispatch.common.enums.AgvStatus.PAUSED));

        for (Agv agv : onlineAgvs) {
            if (agv.getLastHeartbeat() == null) {
                continue;
            }

            long secondsSinceHeartbeat = Duration.between(agv.getLastHeartbeat(), LocalDateTime.now()).getSeconds();
            if (secondsSinceHeartbeat > HEARTBEAT_TIMEOUT_SECONDS) {
                handleHeartbeatTimeout(agv, secondsSinceHeartbeat);
            }
        }
    }

    /**
     * 处理AGV心跳超时
     */
    private void handleHeartbeatTimeout(Agv agv, long timeoutSeconds) {
        agv.setStatus(com.agv.dispatch.common.enums.AgvStatus.OFFLINE);
        agvRepository.save(agv);

        List<Task> agvTasks = taskRepository.findByAgvIdAndStatusIn(agv.getId(),
                Arrays.asList(TaskStatus.EXECUTING, TaskStatus.ASSIGNED));
        for (Task task : agvTasks) {
            task.setStatus(TaskStatus.ABNORMAL);
            task.setExecutionStatus(TaskExecutionStatus.FAILED);
            taskRepository.save(task);

            createAlarm(AlarmType.HEARTBEAT_TIMEOUT, task.getId(), agv.getId(),
                    task.getCurrentNode(),
                    "AGV心跳超时，已离线: " + agv.getAgvNo() + ", 超时: " + timeoutSeconds + "s");

            clearAgvCurrentTaskCache(agv.getAgvNo());
        }

        log.warn("AGV心跳超时: agvNo={}, timeoutSeconds={}, 已标记为离线", agv.getAgvNo(), timeoutSeconds);
    }

    // ==================== 告警管理 ====================

    /**
     * 创建告警记录
     *
     * @param alarmType 告警类型
     * @param taskId 任务ID
     * @param agvId AGV ID
     * @param nodeCode 节点编号
     * @param description 告警描述
     * @return 告警记录
     */
    @Transactional
    public AlarmRecord createAlarm(AlarmType alarmType, String taskId, String agvId,
                                   String nodeCode, String description) {
        try {
            AlarmRecord alarm = new AlarmRecord();
            alarm.setAlarmType(alarmType);
            alarm.setAlarmLevel(alarmType.getLevel());
            alarm.setTitle(alarmType.getDesc());
            alarm.setDescription(description);
            alarm.setAgvId(agvId);
            alarm.setTaskId(taskId);
            alarm.setNodeCode(nodeCode);
            alarm.setHandled(false);

            AlarmRecord saved = alarmRecordRepository.save(alarm);

            String alarmKey = ALARM_PREFIX + saved.getId();
            redisTemplate.opsForValue().set(alarmKey, JSON.toJSONString(saved),
                    ALARM_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForSet().add(ALARM_UNHANDLED_KEY, saved.getId().toString());

            log.warn("创建告警: alarmId={}, type={}, level={}, taskId={}, agvId={}, desc={}",
                    saved.getId(), alarmType.name(), alarmType.getLevel(), taskId, agvId, description);

            return saved;
        } catch (Exception e) {
            log.error("创建告警失败", e);
            return null;
        }
    }

    /**
     * 处理告警
     *
     * @param alarmId 告警ID
     * @param handleResult 处理结果
     * @param handler 处理人
     * @return 是否处理成功
     */
    @Transactional
    public boolean handleAlarm(Long alarmId, String handleResult, String handler) {
        try {
            AlarmRecord alarm = alarmRecordRepository.findById(alarmId)
                    .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + alarmId));

            alarm.setHandled(true);
            alarm.setHandleResult(handleResult);
            alarm.setHandler(handler);
            alarm.setHandleTime(LocalDateTime.now());
            alarmRecordRepository.save(alarm);

            redisTemplate.opsForSet().remove(ALARM_UNHANDLED_KEY, alarmId.toString());

            log.info("告警处理完成: alarmId={}, handler={}, result={}", alarmId, handler, handleResult);
            return true;
        } catch (Exception e) {
            log.error("处理告警失败: alarmId={}", alarmId, e);
            return false;
        }
    }

    /**
     * 获取未处理的告警列表
     *
     * @return 未处理告警列表
     */
    public List<AlarmRecord> getUnhandledAlarms() {
        return alarmRecordRepository.findByHandledFalseOrderByCreateTimeDesc();
    }

    /**
     * 获取所有告警列表
     *
     * @return 告警列表
     */
    public List<AlarmRecord> getAllAlarms() {
        return alarmRecordRepository.findAll();
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据ID获取任务
     */
    private Task getTaskById(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
    }

    /**
     * 解码路径（JSON数组字符串转List）
     */
    private List<String> decodePath(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(pathStr, String.class);
        } catch (Exception e) {
            log.warn("解码路径失败: {}", pathStr);
            return new ArrayList<>();
        }
    }

    /**
     * 记录节点占用时间
     */
    private void recordNodeOccupancyTime(String nodeCode) {
        String key = NODE_OCCUPANCY_TIME_PREFIX + nodeCode;
        redisTemplate.opsForValue().set(key, LocalDateTime.now().toString());
    }

    /**
     * 获取节点占用时间
     */
    private LocalDateTime getNodeOccupancyTime(String nodeCode) {
        String key = NODE_OCCUPANCY_TIME_PREFIX + nodeCode;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 清除节点占用时间
     */
    private void clearNodeOccupancyTime(String nodeCode) {
        String key = NODE_OCCUPANCY_TIME_PREFIX + nodeCode;
        redisTemplate.delete(key);
    }

    /**
     * 更新任务执行缓存
     */
    private void updateTaskExecutionCache(Task task) {
        String key = TASK_EXECUTION_PREFIX + task.getId();
        redisTemplate.opsForValue().set(key, JSON.toJSONString(task),
                TASK_EXECUTION_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 清除任务执行缓存
     */
    private void clearTaskExecutionCache(String taskId) {
        String key = TASK_EXECUTION_PREFIX + taskId;
        redisTemplate.delete(key);
    }

    /**
     * 更新AGV当前任务缓存
     */
    private void updateAgvCurrentTaskCache(String agvNo, String taskId) {
        String key = AGV_CURRENT_TASK_PREFIX + agvNo;
        redisTemplate.opsForValue().set(key, taskId, AGV_CURRENT_TASK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 清除AGV当前任务缓存
     */
    private void clearAgvCurrentTaskCache(String agvNo) {
        String key = AGV_CURRENT_TASK_PREFIX + agvNo;
        redisTemplate.delete(key);
    }

    /**
     * 根据AGV编号获取当前任务
     *
     * @param agvNo AGV编号
     * @return 当前任务
     */
    public Task getCurrentTaskByAgvNo(String agvNo) {
        String key = AGV_CURRENT_TASK_PREFIX + agvNo;
        String taskId = redisTemplate.opsForValue().get(key);
        if (taskId == null) {
            return null;
        }
        return taskRepository.findById(taskId).orElse(null);
    }

    /**
     * 获取正在执行的任务列表
     *
     * @return 正在执行的任务列表
     */
    public List<Task> getExecutingTasks() {
        return taskRepository.findByStatusInAndAgvIdIsNotNull(Arrays.asList(
                TaskStatus.EXECUTING, TaskStatus.ASSIGNED));
    }
}

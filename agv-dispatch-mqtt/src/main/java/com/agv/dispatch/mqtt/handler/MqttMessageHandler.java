package com.agv.dispatch.mqtt.handler;

import com.agv.dispatch.common.constant.MqttTopicConstant;
import com.agv.dispatch.common.dto.AgvStatusDTO;
import com.agv.dispatch.common.dto.AgvStatusReportDTO;
import com.agv.dispatch.common.dto.TaskCreateDTO;
import com.agv.dispatch.common.dto.TaskExecutionFeedbackDTO;
import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.enums.AgvStatus;
import com.agv.dispatch.common.enums.AlarmType;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.common.util.IdGenerator;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.service.AgvStatusService;
import com.agv.dispatch.core.service.TaskDispatchService;
import com.agv.dispatch.core.service.TaskExecutionService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.agv.dispatch.common.constant.RedisKeyConstant.*;

/**
 * MQTT消息处理器
 * 接收并处理所有MQTT消息，包括AGV状态、任务反馈、心跳、故障、执行反馈、控制响应等
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {

    private final TaskDispatchService taskDispatchService;
    private final TaskExecutionService taskExecutionService;
    private final AgvStatusService agvStatusService;
    private final AgvRepository agvRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String HEARTBEAT_TIMEOUT_SECONDS = "60";

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        try {
            MessageHeaders headers = message.getHeaders();
            String topic = headers.get("mqtt_receivedTopic", String.class);
            byte[] payloadBytes = (byte[]) message.getPayload();
            String payload = new String(payloadBytes);

            log.debug("收到MQTT消息, topic={}, payload={}", topic, payload);

            if (topic == null) {
                return;
            }

            if (topic.matches("agv/[^/]+/status")) {
                handleAgvStatus(topic, payload);
            } else if (topic.matches("agv/[^/]+/task/feedback")) {
                handleTaskFeedback(topic, payload);
            } else if (topic.matches("agv/[^/]+/execution/feedback")) {
                handleExecutionFeedback(topic, payload);
            } else if (topic.matches("agv/[^/]+/control/response")) {
                handleControlResponse(topic, payload);
            } else if (topic.matches("agv/[^/]+/heartbeat")) {
                handleHeartbeat(topic, payload);
            } else if (topic.matches("agv/[^/]+/fault")) {
                handleFault(topic, payload);
            } else if (topic.equals(MqttTopicConstant.WMS_TASK_TOPIC)) {
                handleWmsTask(payload);
            } else {
                log.warn("未处理的MQTT主题: {}", topic);
            }

        } catch (Exception e) {
            log.error("处理MQTT消息异常", e);
        }
    }

    private void handleAgvStatus(String topic, String payload) {
        try {
            AgvStatusDTO statusDTO = JSON.parseObject(payload, AgvStatusDTO.class);
            String agvNo = extractAgvNo(topic);

            if (!agvRepository.existsByAgvNo(agvNo)) {
                Agv agv = new Agv();
                agv.setId(IdGenerator.generateId());
                agv.setAgvNo(agvNo);
                agv.setName("AGV-" + agvNo);
                agv.setStatus(AgvStatus.IDLE);
                agv.setBatteryLevel(100.0);
                agv.setXCoord(0.0);
                agv.setYCoord(0.0);
                agv.setCreateTime(LocalDateTime.now());
                agvRepository.save(agv);
                log.info("自动注册AGV: {}", agvNo);
            }

            AgvStatusReportDTO reportDTO = new AgvStatusReportDTO();
            reportDTO.setAgvNo(agvNo);
            reportDTO.setStatus(statusDTO.getStatus() != null ? statusDTO.getStatus().name() : null);
            reportDTO.setCurrentPosition(statusDTO.getCurrentPosition());
            reportDTO.setBatteryLevel(statusDTO.getBatteryLevel());
            reportDTO.setXCoord(statusDTO.getXCoord());
            reportDTO.setYCoord(statusDTO.getYCoord());
            reportDTO.setAngle(statusDTO.getAngle());
            reportDTO.setSpeed(statusDTO.getSpeed());
            reportDTO.setFaultCode(statusDTO.getFaultCode());
            reportDTO.setFaultMessage(statusDTO.getFaultMessage());

            agvStatusService.reportStatus(reportDTO);

            log.debug("AGV状态更新: agvNo={}, status={}", agvNo, statusDTO.getStatus());

        } catch (Exception e) {
            log.error("处理AGV状态消息异常, topic={}, payload={}", topic, payload, e);
        }
    }

    private void handleTaskFeedback(String topic, String payload) {
        try {
            JSONObject feedback = JSON.parseObject(payload);
            String agvNo = extractAgvNo(topic);
            String taskId = feedback.getString("taskId");
            String action = feedback.getString("action");
            Integer step = feedback.getInteger("step");
            String currentPosition = feedback.getString("currentPosition");
            String result = feedback.getString("result");

            if (taskId == null) {
                log.warn("任务反馈缺少taskId: {}", payload);
                return;
            }

            switch (action) {
                case "START":
                    taskDispatchService.updateTaskStatus(taskId, TaskStatus.EXECUTING,
                            "AGV开始执行", "mqtt:" + agvNo);
                    break;
                case "PROGRESS":
                    if (step != null && currentPosition != null) {
                        taskDispatchService.updateTaskProgress(taskId, step, currentPosition);
                    }
                    break;
                case "COMPLETE":
                    taskDispatchService.updateTaskStatus(taskId, TaskStatus.COMPLETED,
                            "AGV完成任务", "mqtt:" + agvNo);
                    break;
                case "ABNORMAL":
                    taskDispatchService.updateTaskStatus(taskId, TaskStatus.ABNORMAL,
                            result != null ? result : "AGV上报异常", "mqtt:" + agvNo);
                    break;
                case "ARRIVED":
                    if (currentPosition != null) {
                        taskDispatchService.updateTaskProgress(taskId,
                                step != null ? step : 0, currentPosition);
                    }
                    break;
                default:
                    log.debug("未处理的任务反馈动作: {}", action);
            }

            log.info("任务反馈: taskId={}, action={}, agvNo={}", taskId, action, agvNo);

        } catch (Exception e) {
            log.error("处理任务反馈异常, topic={}, payload={}", topic, payload, e);
        }
    }

    private void handleHeartbeat(String topic, String payload) {
        try {
            String agvNo = extractAgvNo(topic);
            JSONObject heartbeat = JSON.parseObject(payload);

            String key = HEARTBEAT_PREFIX + agvNo;
            redisTemplate.opsForValue().set(key, payload,
                    Long.parseLong(HEARTBEAT_TIMEOUT_SECONDS), TimeUnit.SECONDS);

            Agv agv = agvRepository.findByAgvNo(agvNo).orElse(null);
            if (agv != null) {
                agv.setLastHeartbeat(LocalDateTime.now());
                if (agv.getStatus() == AgvStatus.OFFLINE) {
                    agv.setStatus(AgvStatus.IDLE);
                }
                if (heartbeat.containsKey("batteryLevel")) {
                    agv.setBatteryLevel(heartbeat.getDouble("batteryLevel"));
                }
                agvRepository.save(agv);
            }

            log.trace("AGV心跳: agvNo={}", agvNo);

        } catch (Exception e) {
            log.error("处理心跳异常, topic={}, payload={}", topic, payload, e);
        }
    }

    private void handleFault(String topic, String payload) {
        try {
            String agvNo = extractAgvNo(topic);
            JSONObject fault = JSON.parseObject(payload);
            String faultCode = fault.getString("faultCode");
            String faultMessage = fault.getString("faultMessage");

            Agv agv = agvRepository.findByAgvNo(agvNo).orElse(null);
            if (agv != null) {
                taskDispatchService.handleAgvFault(agv.getId(), faultCode, faultMessage);
            }

            log.warn("AGV故障告警: agvNo={}, faultCode={}, faultMessage={}",
                    agvNo, faultCode, faultMessage);

        } catch (Exception e) {
            log.error("处理故障消息异常, topic={}, payload={}", topic, payload, e);
        }
    }

    private void handleWmsTask(String payload) {
        try {
            TaskCreateDTO taskDTO = JSON.parseObject(payload, TaskCreateDTO.class);
            if (taskDTO.getStartPoint() == null || taskDTO.getEndPoint() == null) {
                log.error("WMS任务参数不完整: {}", payload);
                return;
            }
            taskDispatchService.createTask(taskDTO);
            log.info("WMS任务创建成功: start={}, end={}", taskDTO.getStartPoint(), taskDTO.getEndPoint());

        } catch (Exception e) {
            log.error("处理WMS任务异常, payload={}", payload, e);
        }
    }

    /**
     * 处理AGV执行反馈消息
     * 
     * 业务逻辑：
     * 1. 从topic中提取AGV编号
     * 2. 解析payload为TaskExecutionFeedbackDTO
     * 3. 补充agvNo字段（从topic获取的优先级高于payload中的）
     * 4. 调用taskDispatchService.handleExecutionFeedback处理业务逻辑
     * 5. 根据不同的action记录不同级别的日志
     * 
     * 支持的action类型：
     * - START: AGV开始执行任务
     * - PROGRESS: 执行进度更新
     * - ARRIVED: 到达路径节点
     * - WORKING: 作业中（装卸货等）
     * - COMPLETE: 任务完成
     * - ABNORMAL: 执行异常
     * - PAUSE: 任务暂停
     * - RESUME: 任务恢复
     * 
     * @param topic MQTT主题，格式：agv/{agvNo}/execution/feedback
     * @param payload 消息内容，JSON格式的TaskExecutionFeedbackDTO
     */
    private void handleExecutionFeedback(String topic, String payload) {
        try {
            String agvNo = extractAgvNo(topic);
            log.debug("收到AGV执行反馈: agvNo={}, payload={}", agvNo, payload);

            TaskExecutionFeedbackDTO feedbackDTO = JSON.parseObject(payload, TaskExecutionFeedbackDTO.class);
            if (feedbackDTO == null) {
                log.error("执行反馈消息解析失败: {}", payload);
                return;
            }

            if (feedbackDTO.getTaskId() == null || feedbackDTO.getTaskId().isEmpty()) {
                log.error("执行反馈缺少taskId: {}", payload);
                return;
            }

            if (feedbackDTO.getAction() == null || feedbackDTO.getAction().isEmpty()) {
                log.error("执行反馈缺少action: {}", payload);
                return;
            }

            feedbackDTO.setAgvNo(agvNo);

            taskExecutionService.handleExecutionFeedback(feedbackDTO);

            String action = feedbackDTO.getAction();
            if ("COMPLETE".equals(action) || "ABNORMAL".equals(action)) {
                log.info("任务执行反馈[{}]: taskId={}, agvNo={}, result={}",
                        action, feedbackDTO.getTaskId(), agvNo, feedbackDTO.getResult());
            } else if ("START".equals(action) || "PAUSE".equals(action) || "RESUME".equals(action)) {
                log.info("任务状态变更[{}]: taskId={}, agvNo={}",
                        action, feedbackDTO.getTaskId(), agvNo);
            } else {
                log.debug("任务执行进度[{}]: taskId={}, agvNo={}, step={}, progress={}%",
                        action, feedbackDTO.getTaskId(), agvNo,
                        feedbackDTO.getCurrentStep(), feedbackDTO.getProgress());
            }

        } catch (Exception e) {
            log.error("处理AGV执行反馈异常, topic={}, payload={}", topic, payload, e);
        }
    }

    /**
     * 处理AGV控制响应消息
     * 
     * 业务逻辑：
     * 1. 从topic中提取AGV编号
     * 2. 解析payload获取控制命令执行结果
     * 3. 记录控制命令执行日志
     * 4. 如果控制失败，触发告警通知
     * 
     * 响应消息格式示例：
     * {
     *   "command": "PAUSE",
     *   "success": true,
     *   "message": "暂停成功",
     *   "timestamp": "2026-06-06T10:05:30"
     * }
     * 
     * @param topic MQTT主题，格式：agv/{agvNo}/control/response
     * @param payload 消息内容，JSON格式的控制响应
     */
    private void handleControlResponse(String topic, String payload) {
        try {
            String agvNo = extractAgvNo(topic);
            log.debug("收到AGV控制响应: agvNo={}, payload={}", agvNo, payload);

            JSONObject response = JSON.parseObject(payload);
            if (response == null) {
                log.error("控制响应消息解析失败: {}", payload);
                return;
            }

            String command = response.getString("command");
            Boolean success = response.getBoolean("success");
            String message = response.getString("message");
            String errorCode = response.getString("errorCode");

            if (success == null) {
                success = true;
            }

            if (success) {
                log.info("AGV控制命令执行成功: agvNo={}, command={}, message={}",
                        agvNo, command, message);
            } else {
                log.error("AGV控制命令执行失败: agvNo={}, command={}, errorCode={}, message={}",
                        agvNo, command, errorCode, message);

                Agv agv = agvRepository.findByAgvNo(agvNo).orElse(null);
                String agvId = agv != null ? agv.getId() : null;

                taskExecutionService.createAlarm(
                        AlarmType.COMMUNICATION_ERROR,
                        null,
                        agvId,
                        null,
                        String.format("命令: %s, 错误码: %s, 错误信息: %s",
                                command, errorCode, message));
            }

        } catch (Exception e) {
            log.error("处理AGV控制响应异常, topic={}, payload={}", topic, payload, e);
        }
    }

    private String extractAgvNo(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "unknown";
    }
}

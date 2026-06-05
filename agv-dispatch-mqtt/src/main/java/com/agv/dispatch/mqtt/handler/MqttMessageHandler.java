package com.agv.dispatch.mqtt.handler;

import com.agv.dispatch.common.constant.MqttTopicConstant;
import com.agv.dispatch.common.dto.AgvStatusDTO;
import com.agv.dispatch.common.dto.AgvStatusReportDTO;
import com.agv.dispatch.common.dto.TaskCreateDTO;
import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.enums.AgvStatus;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.common.util.IdGenerator;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.service.AgvStatusService;
import com.agv.dispatch.core.service.TaskDispatchService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {

    private final TaskDispatchService taskDispatchService;
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

    private String extractAgvNo(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "unknown";
    }
}

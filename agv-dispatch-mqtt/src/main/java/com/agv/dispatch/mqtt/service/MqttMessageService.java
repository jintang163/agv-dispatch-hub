package com.agv.dispatch.mqtt.service;

import com.agv.dispatch.common.constant.MqttTopicConstant;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.mqtt.gateway.MqttGateway;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMessageService {

    private final MqttGateway mqttGateway;

    @Value("${mqtt.qos:1}")
    private int defaultQos;

    public void sendTaskAssign(String agvNo, Task task) {
        String topic = MqttTopicConstant.buildTopic(MqttTopicConstant.TASK_ASSIGN_TOPIC, agvNo);

        Map<String, Object> message = new HashMap<>();
        message.put("taskId", task.getId());
        message.put("taskNo", task.getTaskNo());
        message.put("taskType", task.getTaskType().getCode());
        message.put("startPoint", task.getStartPoint());
        message.put("endPoint", task.getEndPoint());
        message.put("path", task.getPath());
        message.put("priority", task.getPriority().getCode());
        message.put("deadline", task.getDeadline() != null ? task.getDeadline().toString() : null);
        message.put("loadWeight", task.getLoadWeight());
        message.put("cargoInfo", task.getCargoInfo());
        message.put("timestamp", System.currentTimeMillis());

        String payload = JSON.toJSONString(message);
        mqttGateway.send(topic, defaultQos, payload);
        log.info("下发任务: topic={}, taskId={}, agvNo={}", topic, task.getId(), agvNo);
    }

    public void sendTaskCancel(String agvNo, String taskId, String reason) {
        String topic = MqttTopicConstant.buildTopic(MqttTopicConstant.TASK_CANCEL_TOPIC, agvNo);

        Map<String, Object> message = new HashMap<>();
        message.put("taskId", taskId);
        message.put("reason", reason);
        message.put("timestamp", System.currentTimeMillis());

        String payload = JSON.toJSONString(message);
        mqttGateway.send(topic, defaultQos, payload);
        log.info("取消任务: topic={}, taskId={}", topic, taskId);
    }

    public void sendControlCommand(String agvNo, String command, Map<String, Object> params) {
        String topic = MqttTopicConstant.buildTopic(MqttTopicConstant.AGV_CONTROL_TOPIC, agvNo);

        Map<String, Object> message = new HashMap<>();
        message.put("command", command);
        message.put("params", params != null ? params : new HashMap<>());
        message.put("timestamp", System.currentTimeMillis());

        String payload = JSON.toJSONString(message);
        mqttGateway.send(topic, defaultQos, payload);
        log.info("发送控制命令: topic={}, command={}", topic, command);
    }

    public void sendPause(String agvNo) {
        sendControlCommand(agvNo, "PAUSE", null);
    }

    public void sendResume(String agvNo) {
        sendControlCommand(agvNo, "RESUME", null);
    }

    public void sendEmergencyStop(String agvNo) {
        sendControlCommand(agvNo, "EMERGENCY_STOP", null);
    }

    public void sendGoToCharge(String agvNo, String chargingStation) {
        Map<String, Object> params = new HashMap<>();
        params.put("target", chargingStation);
        sendControlCommand(agvNo, "GO_TO_CHARGE", params);
    }

    public void sendMessage(String topic, Object payload) {
        String payloadStr = payload instanceof String ?
                (String) payload : JSON.toJSONString(payload);
        mqttGateway.send(topic, defaultQos, payloadStr);
        log.debug("发送MQTT消息: topic={}", topic);
    }

    public void sendMessage(String topic, int qos, Object payload) {
        String payloadStr = payload instanceof String ?
                (String) payload : JSON.toJSONString(payload);
        mqttGateway.send(topic, qos, payloadStr);
        log.debug("发送MQTT消息: topic={}, qos={}", topic, qos);
    }
}

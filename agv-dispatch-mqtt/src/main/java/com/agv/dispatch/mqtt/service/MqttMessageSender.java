package com.agv.dispatch.mqtt.service;

import com.agv.dispatch.common.constant.MqttTopicConstant;
import com.agv.dispatch.common.dto.AgvRemoteControlDTO;
import com.agv.dispatch.common.dto.TaskCancelDTO;
import com.agv.dispatch.common.dto.TaskDispatchDTO;
import com.agv.dispatch.mqtt.gateway.MqttGateway;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * MQTT消息发送服务
 * 
 * 提供统一的消息发送接口，负责任务下发、远程控制、任务取消等消息的发送
 * 所有消息使用QoS=1保证可靠到达，使用fastjson2进行JSON序列化
 * 
 * 主要功能：
 * 1. 任务下发 - 将详细的任务信息和路径下发给指定AGV
 * 2. 远程控制 - 发送暂停、恢复、急停等控制命令
 * 3. 任务管理 - 发送任务取消、暂停、恢复等管理命令
 * 
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMessageSender {

    private final MqttGateway mqttGateway;

    @Value("${mqtt.qos:1}")
    private int defaultQos;

    /**
     * 下发任务调度指令
     * 
     * 业务逻辑：
     * 1. 校验dispatchDTO的必填字段（agvNo、taskId等）
     * 2. 使用fastjson2将dispatchDTO序列化为JSON字符串
     * 3. 构建topic：agv/{agvNo}/task/dispatch
     * 4. 使用QoS=1发送消息，确保AGV可靠接收
     * 5. 记录发送日志，包含topic、taskId、agvNo等关键信息
     * 
     * @param dispatchDTO 任务下发DTO，包含任务ID、AGV编号、路径点序列等信息
     * @throws IllegalArgumentException 如果必填字段为空
     */
    public void dispatchTask(TaskDispatchDTO dispatchDTO) {
        try {
            if (dispatchDTO == null) {
                throw new IllegalArgumentException("任务下发参数不能为空");
            }
            if (dispatchDTO.getAgvNo() == null || dispatchDTO.getAgvNo().isEmpty()) {
                throw new IllegalArgumentException("AGV编号不能为空");
            }
            if (dispatchDTO.getTaskId() == null || dispatchDTO.getTaskId().isEmpty()) {
                throw new IllegalArgumentException("任务ID不能为空");
            }

            String topic = MqttTopicConstant.buildTopic(
                    MqttTopicConstant.TASK_DISPATCH_TOPIC, dispatchDTO.getAgvNo());
            String payload = JSON.toJSONString(dispatchDTO);

            mqttGateway.send(topic, defaultQos, payload);

            log.info("任务下发成功: topic={}, taskId={}, taskNo={}, agvNo={}, " +
                            "startPoint={}, endPoint={}, pathPoints={}",
                    topic, dispatchDTO.getTaskId(), dispatchDTO.getTaskNo(),
                    dispatchDTO.getAgvNo(), dispatchDTO.getStartPoint(),
                    dispatchDTO.getEndPoint(),
                    dispatchDTO.getPathPoints() != null ? dispatchDTO.getPathPoints().size() : 0);

        } catch (Exception e) {
            log.error("任务下发失败: taskId={}, agvNo={}",
                    dispatchDTO != null ? dispatchDTO.getTaskId() : null,
                    dispatchDTO != null ? dispatchDTO.getAgvNo() : null, e);
            throw e;
        }
    }

    /**
     * 发送远程控制命令
     * 
     * 业务逻辑：
     * 1. 校验controlDTO的必填字段（agvNo、command）
     * 2. 使用fastjson2将controlDTO序列化为JSON字符串
     * 3. 构建topic：agv/{agvNo}/control
     * 4. 使用QoS=1发送消息，确保AGV可靠接收
     * 5. 记录发送日志，包含topic、command、agvNo等关键信息
     * 
     * 支持的控制命令：
     * - PAUSE: 暂停当前任务
     * - RESUME: 恢复暂停的任务
     * - CANCEL: 取消当前任务
     * - STOP: 立即停车
     * - SLOW_DOWN: 慢速行驶
     * - NORMAL_SPEED: 正常行驶
     * - DETOUR: 避障绕行
     * - GO_CHARGE: 返回充电站
     * - GO_TO_POINT: 到指定点
     * 
     * @param agvNo AGV编号
     * @param controlDTO 远程控制DTO，包含控制命令、目标点、速度等参数
     * @throws IllegalArgumentException 如果必填字段为空
     */
    public void sendControlCommand(String agvNo, AgvRemoteControlDTO controlDTO) {
        try {
            if (agvNo == null || agvNo.isEmpty()) {
                throw new IllegalArgumentException("AGV编号不能为空");
            }
            if (controlDTO == null) {
                throw new IllegalArgumentException("控制参数不能为空");
            }
            if (controlDTO.getCommand() == null || controlDTO.getCommand().isEmpty()) {
                throw new IllegalArgumentException("控制命令不能为空");
            }

            controlDTO.setAgvNo(agvNo);

            String topic = MqttTopicConstant.buildTopic(
                    MqttTopicConstant.AGV_CONTROL_TOPIC, agvNo);
            String payload = JSON.toJSONString(controlDTO);

            mqttGateway.send(topic, defaultQos, payload);

            log.info("发送控制命令成功: topic={}, agvNo={}, command={}, " +
                            "targetPoint={}, operator={}, reason={}",
                    topic, agvNo, controlDTO.getCommand(),
                    controlDTO.getTargetPoint(), controlDTO.getOperator(),
                    controlDTO.getReason());

        } catch (Exception e) {
            log.error("发送控制命令失败: agvNo={}, command={}",
                    agvNo, controlDTO != null ? controlDTO.getCommand() : null, e);
            throw e;
        }
    }

    /**
     * 取消任务
     * 
     * 业务逻辑：
     * 1. 校验必填参数（agvNo、taskId）
     * 2. 构建TaskCancelDTO对象，包含taskId、reason、operator
     * 3. 构建topic：agv/{agvNo}/task/cancel
     * 4. 使用fastjson2序列化后以QoS=1发送
     * 5. 记录发送日志
     * 
     * @param agvNo AGV编号
     * @param taskId 任务ID
     * @param reason 取消原因
     * @throws IllegalArgumentException 如果必填字段为空
     */
    public void cancelTask(String agvNo, String taskId, String reason) {
        try {
            if (agvNo == null || agvNo.isEmpty()) {
                throw new IllegalArgumentException("AGV编号不能为空");
            }
            if (taskId == null || taskId.isEmpty()) {
                throw new IllegalArgumentException("任务ID不能为空");
            }

            TaskCancelDTO cancelDTO = new TaskCancelDTO();
            cancelDTO.setTaskId(taskId);
            cancelDTO.setReason(reason);
            cancelDTO.setOperator("system");

            String topic = MqttTopicConstant.buildTopic(
                    MqttTopicConstant.TASK_CANCEL_TOPIC, agvNo);
            String payload = JSON.toJSONString(cancelDTO);

            mqttGateway.send(topic, defaultQos, payload);

            log.info("发送任务取消命令: topic={}, agvNo={}, taskId={}, reason={}",
                    topic, agvNo, taskId, reason);

        } catch (Exception e) {
            log.error("发送任务取消命令失败: agvNo={}, taskId={}", agvNo, taskId, e);
            throw e;
        }
    }

    /**
     * 暂停任务
     * 
     * 业务逻辑：
     * 1. 校验必填参数（agvNo、taskId）
     * 2. 构建消息体，包含taskId和暂停原因
     * 3. 构建topic：agv/{agvNo}/task/pause
     * 4. 使用fastjson2序列化后以QoS=1发送
     * 5. 记录发送日志
     * 
     * @param agvNo AGV编号
     * @param taskId 任务ID
     * @throws IllegalArgumentException 如果必填字段为空
     */
    public void pauseTask(String agvNo, String taskId) {
        try {
            if (agvNo == null || agvNo.isEmpty()) {
                throw new IllegalArgumentException("AGV编号不能为空");
            }
            if (taskId == null || taskId.isEmpty()) {
                throw new IllegalArgumentException("任务ID不能为空");
            }

            Map<String, Object> message = new HashMap<>();
            message.put("taskId", taskId);
            message.put("reason", "系统暂停");
            message.put("operator", "system");
            message.put("timestamp", System.currentTimeMillis());

            String topic = MqttTopicConstant.buildTopic(
                    MqttTopicConstant.TASK_PAUSE_TOPIC, agvNo);
            String payload = JSON.toJSONString(message);

            mqttGateway.send(topic, defaultQos, payload);

            log.info("发送任务暂停命令: topic={}, agvNo={}, taskId={}",
                    topic, agvNo, taskId);

        } catch (Exception e) {
            log.error("发送任务暂停命令失败: agvNo={}, taskId={}", agvNo, taskId, e);
            throw e;
        }
    }

    /**
     * 恢复任务
     * 
     * 业务逻辑：
     * 1. 校验必填参数（agvNo、taskId）
     * 2. 构建消息体，包含taskId和恢复原因
     * 3. 构建topic：agv/{agvNo}/task/resume
     * 4. 使用fastjson2序列化后以QoS=1发送
     * 5. 记录发送日志
     * 
     * @param agvNo AGV编号
     * @param taskId 任务ID
     * @throws IllegalArgumentException 如果必填字段为空
     */
    public void resumeTask(String agvNo, String taskId) {
        try {
            if (agvNo == null || agvNo.isEmpty()) {
                throw new IllegalArgumentException("AGV编号不能为空");
            }
            if (taskId == null || taskId.isEmpty()) {
                throw new IllegalArgumentException("任务ID不能为空");
            }

            Map<String, Object> message = new HashMap<>();
            message.put("taskId", taskId);
            message.put("reason", "系统恢复");
            message.put("operator", "system");
            message.put("timestamp", System.currentTimeMillis());

            String topic = MqttTopicConstant.buildTopic(
                    MqttTopicConstant.TASK_RESUME_TOPIC, agvNo);
            String payload = JSON.toJSONString(message);

            mqttGateway.send(topic, defaultQos, payload);

            log.info("发送任务恢复命令: topic={}, agvNo={}, taskId={}",
                    topic, agvNo, taskId);

        } catch (Exception e) {
            log.error("发送任务恢复命令失败: agvNo={}, taskId={}", agvNo, taskId, e);
            throw e;
        }
    }
}

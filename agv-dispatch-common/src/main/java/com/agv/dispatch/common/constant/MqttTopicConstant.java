package com.agv.dispatch.common.constant;

public class MqttTopicConstant {

    public static final String AGV_STATUS_TOPIC = "agv/+/status";
    public static final String AGV_TASK_FEEDBACK_TOPIC = "agv/+/task/feedback";
    public static final String AGV_HEARTBEAT_TOPIC = "agv/+/heartbeat";
    public static final String AGV_FAULT_TOPIC = "agv/+/fault";

    public static final String TASK_ASSIGN_TOPIC = "agv/{agvNo}/task/assign";
    public static final String TASK_CANCEL_TOPIC = "agv/{agvNo}/task/cancel";
    public static final String AGV_CONTROL_TOPIC = "agv/{agvNo}/control";

    public static final String WMS_TASK_TOPIC = "wms/task/create";

    public static String buildTopic(String template, String agvNo) {
        return template.replace("{agvNo}", agvNo);
    }
}

package com.agv.dispatch.common.constant;

/**
 * MQTT主题常量类
 * 定义系统中所有MQTT消息的主题规范
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
public class MqttTopicConstant {

    /**
     * AGV状态上报主题
     * 订阅模式：agv/+/status
     * AGV定期上报自身状态信息（位置、电量、速度等）
     */
    public static final String AGV_STATUS_TOPIC = "agv/+/status";

    /**
     * AGV任务反馈主题
     * 订阅模式：agv/+/task/feedback
     * AGV上报任务执行过程中的状态反馈（到达节点、动作完成等）
     */
    public static final String AGV_TASK_FEEDBACK_TOPIC = "agv/+/task/feedback";

    /**
     * AGV心跳上报主题
     * 订阅模式：agv/+/heartbeat
     * AGV定期发送心跳报文，用于在线状态监测
     */
    public static final String AGV_HEARTBEAT_TOPIC = "agv/+/heartbeat";

    /**
     * AGV故障上报主题
     * 订阅模式：agv/+/fault
     * AGV上报故障信息（故障代码、故障描述等）
     */
    public static final String AGV_FAULT_TOPIC = "agv/+/fault";

    /**
     * AGV执行反馈主题
     * 订阅模式：agv/+/execution/feedback
     * AGV上报详细执行过程反馈（动作执行结果、传感器数据等）
     */
    public static final String AGV_EXECUTION_FEEDBACK_TOPIC = "agv/+/execution/feedback";

    /**
     * AGV控制响应主题
     * 订阅模式：agv/+/control/response
     * AGV响应控制指令的执行结果
     */
    public static final String AGV_CONTROL_RESPONSE_TOPIC = "agv/+/control/response";

    /**
     * 任务分配主题
     * 发布模式：agv/{agvNo}/task/assign
     * 调度系统向指定AGV分配任务
     */
    public static final String TASK_ASSIGN_TOPIC = "agv/{agvNo}/task/assign";

    /**
     * 任务取消主题
     * 发布模式：agv/{agvNo}/task/cancel
     * 调度系统向指定AGV发送取消任务指令
     */
    public static final String TASK_CANCEL_TOPIC = "agv/{agvNo}/task/cancel";

    /**
     * AGV控制主题
     * 发布模式：agv/{agvNo}/control
     * 调度系统向指定AGV发送控制指令（暂停、恢复、急停等）
     */
    public static final String AGV_CONTROL_TOPIC = "agv/{agvNo}/control";

    /**
     * 任务调度主题
     * 发布模式：agv/{agvNo}/task/dispatch
     * 调度系统向指定AGV发送详细的任务调度指令（路径、动作序列等）
     */
    public static final String TASK_DISPATCH_TOPIC = "agv/{agvNo}/task/dispatch";

    /**
     * 任务暂停主题
     * 发布模式：agv/{agvNo}/task/pause
     * 调度系统向指定AGV发送暂停当前任务指令
     */
    public static final String TASK_PAUSE_TOPIC = "agv/{agvNo}/task/pause";

    /**
     * 任务恢复主题
     * 发布模式：agv/{agvNo}/task/resume
     * 调度系统向指定AGV发送恢复暂停任务指令
     */
    public static final String TASK_RESUME_TOPIC = "agv/{agvNo}/task/resume";

    /**
     * WMS任务创建主题
     * 订阅模式：wms/task/create
     * 接收WMS系统下发的任务创建请求
     */
    public static final String WMS_TASK_TOPIC = "wms/task/create";

    /**
     * 构建具体AGV的主题
     *
     * @param template 主题模板，包含{agvNo}占位符
     * @param agvNo    AGV编号
     * @return 替换占位符后的完整主题
     */
    public static String buildTopic(String template, String agvNo) {
        return template.replace("{agvNo}", agvNo);
    }
}

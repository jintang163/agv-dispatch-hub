package com.agv.dispatch.common.enums;

import lombok.Getter;

/**
 * 任务执行状态枚举
 * 描述任务在执行过程中的详细状态
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Getter
public enum TaskExecutionStatus {

    /**
     * 待下发：任务已分配给AGV，但尚未下发到AGV
     */
    PENDING_DISPATCH(1, "待下发"),

    /**
     * 已下发：任务已下发到AGV，等待AGV确认
     */
    DISPATCHED(2, "已下发"),

    /**
     * 执行中：AGV正在执行任务
     */
    EXECUTING(3, "执行中"),

    /**
     * 节点到达：AGV到达某个路径节点
     */
    ARRIVED_NODE(4, "节点到达"),

    /**
     * 作业中：AGV在目标点进行作业（装卸货等）
     */
    WORKING(5, "作业中"),

    /**
     * 已暂停：任务被手动暂停
     */
    PAUSED(6, "已暂停"),

    /**
     * 已完成：任务执行完成
     */
    COMPLETED(7, "已完成"),

    /**
     * 已取消：任务被手动取消
     */
    CANCELLED(8, "已取消"),

    /**
     * 执行超时：任务执行时间超过预期
     */
    TIMEOUT(9, "执行超时"),

    /**
     * 执行失败：AGV上报执行失败
     */
    FAILED(10, "执行失败"),

    /**
     * 重新调度：任务因故障或阻塞被重新调度
     */
    RESCHEDULED(11, "重新调度");

    private final int code;
    private final String desc;

    TaskExecutionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

package com.agv.dispatch.common.enums;

import lombok.Getter;

/**
 * 告警类型枚举
 * 定义系统中的各类告警
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Getter
public enum AlarmType {

    /**
     * 任务执行超时：任务执行时间超过预计完成时间
     */
    TASK_TIMEOUT(1, "任务执行超时", "ERROR"),

    /**
     * 节点占用超时：AGV在某个节点停留时间超过阈值
     */
    NODE_OCCUPANCY_TIMEOUT(2, "节点占用超时", "WARNING"),

    /**
     * AGV心跳超时：AGV超过指定时间未上报心跳
     */
    HEARTBEAT_TIMEOUT(3, "AGV心跳超时", "ERROR"),

    /**
     * AGV故障：AGV上报故障信息
     */
    AGV_FAULT(4, "AGV故障", "ERROR"),

    /**
     * 路径阻塞：AGV报告路径被阻塞
     */
    PATH_BLOCKED(5, "路径阻塞", "WARNING"),

    /**
     * 冲突检测：检测到AGV冲突
     */
    CONFLICT_DETECTED(6, "冲突检测", "WARNING"),

    /**
     * 死锁检测：检测到死锁
     */
    DEADLOCK_DETECTED(7, "死锁检测", "ERROR"),

    /**
     * 低电量：AGV电量低于阈值
     */
    LOW_BATTERY(8, "低电量", "WARNING"),

    /**
     * 任务失败：AGV上报任务执行失败
     */
    TASK_FAILED(9, "任务失败", "ERROR"),

    /**
     * 通信异常：与AGV通信异常
     */
    COMMUNICATION_ERROR(10, "通信异常", "ERROR");

    private final int code;
    private final String desc;
    private final String level;

    AlarmType(int code, String desc, String level) {
        this.code = code;
        this.desc = desc;
        this.level = level;
    }
}

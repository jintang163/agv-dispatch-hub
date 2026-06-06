package com.agv.dispatch.common.enums;

import lombok.Getter;

/**
 * AGV控制命令类型枚举
 * 定义下发给AGV的远程控制命令
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Getter
public enum AgvControlCommand {

    /**
     * 暂停：暂停AGV当前任务
     */
    PAUSE(1, "暂停"),

    /**
     * 恢复：恢复AGV执行任务
     */
    RESUME(2, "恢复"),

    /**
     * 取消：取消AGV当前任务
     */
    CANCEL(3, "取消任务"),

    /**
     * 立即停车：紧急停车
     */
    STOP(4, "立即停车"),

    /**
     * 慢速行驶：降低行驶速度
     */
    SLOW_DOWN(5, "慢速行驶"),

    /**
     * 正常行驶：恢复正常速度
     */
    NORMAL_SPEED(6, "正常行驶"),

    /**
     * 避障绕行：命令AGV避开障碍物
     */
    DETOUR(7, "避障绕行"),

    /**
     * 返回充电站：命令AGV返回充电站充电
     */
    GO_CHARGE(8, "返回充电站"),

    /**
     * 到指定点：命令AGV行驶到指定节点
     */
    GO_TO_POINT(9, "到指定点");

    private final int code;
    private final String desc;

    AgvControlCommand(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

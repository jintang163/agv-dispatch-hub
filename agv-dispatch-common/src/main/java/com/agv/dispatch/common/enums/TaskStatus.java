package com.agv.dispatch.common.enums;

import lombok.Getter;

@Getter
public enum TaskStatus {

    PENDING(0, "待分配"),
    ASSIGNED(1, "已分配"),
    EXECUTING(2, "执行中"),
    PAUSED(6, "已暂停"),
    COMPLETED(3, "完成"),
    CANCELLED(4, "取消"),
    ABNORMAL(5, "异常");

    private final int code;
    private final String desc;

    TaskStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TaskStatus of(int code) {
        for (TaskStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return PENDING;
    }

    public boolean canTransitionTo(TaskStatus newStatus) {
        return switch (this) {
            case PENDING -> newStatus == ASSIGNED || newStatus == CANCELLED;
            case ASSIGNED -> newStatus == EXECUTING || newStatus == CANCELLED || newStatus == PENDING || newStatus == PAUSED;
            case EXECUTING -> newStatus == COMPLETED || newStatus == ABNORMAL || newStatus == CANCELLED || newStatus == PAUSED;
            case PAUSED -> newStatus == EXECUTING || newStatus == CANCELLED || newStatus == PENDING;
            case ABNORMAL -> newStatus == PENDING || newStatus == CANCELLED;
            default -> false;
        };
    }
}

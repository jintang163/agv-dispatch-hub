package com.agv.dispatch.common.enums;

import lombok.Getter;

@Getter
public enum TaskPriority {

    HIGH(3, "高优先级"),
    MEDIUM(2, "中优先级"),
    LOW(1, "低优先级");

    private final int code;
    private final String desc;

    TaskPriority(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TaskPriority of(int code) {
        for (TaskPriority priority : values()) {
            if (priority.code == code) {
                return priority;
            }
        }
        return MEDIUM;
    }
}

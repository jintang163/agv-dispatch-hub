package com.agv.dispatch.common.enums;

import lombok.Getter;

@Getter
public enum TaskType {

    TRANSPORT(1, "搬运"),
    PICKING(2, "拣选"),
    CHARGING(3, "充电"),
    IDLE(4, "待命");

    private final int code;
    private final String desc;

    TaskType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TaskType of(int code) {
        for (TaskType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return TRANSPORT;
    }
}

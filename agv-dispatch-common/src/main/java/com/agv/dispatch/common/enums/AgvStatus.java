package com.agv.dispatch.common.enums;

import lombok.Getter;

@Getter
public enum AgvStatus {

    IDLE(0, "空闲"),
    WORKING(1, "工作中"),
    CHARGING(2, "充电中"),
    FAULT(3, "故障"),
    OFFLINE(4, "离线"),
    PAUSED(5, "暂停");

    private final int code;
    private final String desc;

    AgvStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AgvStatus of(int code) {
        for (AgvStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return OFFLINE;
    }

    public boolean isAvailable() {
        return this == IDLE;
    }
}

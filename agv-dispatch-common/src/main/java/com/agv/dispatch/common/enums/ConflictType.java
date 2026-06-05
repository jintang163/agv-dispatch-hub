package com.agv.dispatch.common.enums;

import lombok.Getter;

@Getter
public enum ConflictType {

    HEAD_ON(1, "对向冲突"),
    CROSS(2, "交叉冲突"),
    FOLLOW(3, "跟车冲突"),
    RESOURCE(4, "资源冲突");

    private final int code;
    private final String desc;

    ConflictType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

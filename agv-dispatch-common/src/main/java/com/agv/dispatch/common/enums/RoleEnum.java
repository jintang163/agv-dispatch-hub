package com.agv.dispatch.common.enums;

import lombok.Getter;

@Getter
public enum RoleEnum {

    ADMIN(1, "管理员", "拥有所有权限"),
    DISPATCHER(2, "调度员", "可下发任务、手动干预"),
    READ_ONLY(3, "只读", "仅查看数据");

    private final int code;
    private final String desc;
    private final String remark;

    RoleEnum(int code, String desc, String remark) {
        this.code = code;
        this.desc = desc;
        this.remark = remark;
    }

    public static RoleEnum of(int code) {
        for (RoleEnum role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return READ_ONLY;
    }

    public boolean hasPermission(String permission) {
        if (this == ADMIN) {
            return true;
        }
        if (this == DISPATCHER) {
            return !"user:manage".equals(permission);
        }
        return false;
    }
}

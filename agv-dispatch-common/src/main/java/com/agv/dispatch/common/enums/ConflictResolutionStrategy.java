package com.agv.dispatch.common.enums;

import lombok.Getter;

@Getter
public enum ConflictResolutionStrategy {

    WAIT(1, "低优先级等待"),
    DETOUR(2, "绕行重规划"),
    YIELD(3, "让行动作（路口会车）"),
    REASSIGN(4, "任务重分配"),
    DEADLOCK_RECOVERY(5, "死锁恢复");

    private final int code;
    private final String desc;

    ConflictResolutionStrategy(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

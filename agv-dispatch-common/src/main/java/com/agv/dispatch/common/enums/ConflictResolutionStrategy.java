package com.agv.dispatch.common.enums;

import lombok.Getter;

/**
 * 冲突解决策略枚举
 * 定义系统中处理冲突和死锁的各种策略
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Getter
public enum ConflictResolutionStrategy {

    /**
     * 低优先级等待策略
     * 比较任务优先级，优先级低的AGV暂停等待，让优先级高的AGV先通过
     * 适用于：大部分冲突场景，尤其是同方向行驶的冲突
     */
    WAIT(1, "低优先级等待"),

    /**
     * 绕行重规划策略
     * 为其中一辆AGV重新规划路径，避开冲突节点
     * 适用于：对向冲突、存在可替代路径的场景
     */
    DETOUR(2, "绕行重规划"),

    /**
     * 让行动作（路口会车）策略
     * 在路口等关键节点，根据先到先得原则，一方获得通行权，另一方让行
     * 适用于：路口交叉冲突、通道会车场景
     */
    YIELD(3, "让行动作（路口会车）"),

    /**
     * 任务重分配策略
     * 将其中一辆AGV的任务重新分配给其他空闲AGV执行
     * 适用于：任务可以重新分配、有空闲AGV可用的场景
     */
    REASSIGN(4, "任务重分配"),

    /**
     * 死锁恢复策略
     * 检测到死锁时，主动选择一辆AGV作为牺牲者，撤回并重新规划其路径
     * 适用于：循环等待死锁场景
     */
    DEADLOCK_RECOVERY(5, "死锁恢复");

    /**
     * 策略编码
     */
    private final int code;

    /**
     * 策略描述
     */
    private final String desc;

    ConflictResolutionStrategy(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

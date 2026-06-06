package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.ConflictResolutionStrategy;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 死锁记录实体
 * 记录检测到的死锁情况及解决结果，用于死锁分析和审计
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Entity
@Table(name = "agv_deadlock_record", indexes = {
        @Index(name = "idx_deadlock_create_time", columnList = "createTime"),
        @Index(name = "idx_deadlock_resolved", columnList = "resolved")
})
public class DeadlockRecord {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 等待链JSON数组，记录死锁中AGV的等待关系
     * 格式: ["AGV001", "AGV002", "AGV003"] 表示 AGV001等待AGV002, AGV002等待AGV003, AGV003等待AGV001
     */
    @Column(length = 1024, nullable = false)
    private String waitChain;

    /**
     * 涉及的AGV数量
     */
    @Column(nullable = false)
    private Integer agvCount;

    /**
     * 被选中的牺牲AGV ID，死锁恢复时选择该AGV重新规划路径
     */
    @Column(length = 32)
    private String selectedAgvId;

    /**
     * 采用的解决策略：WAIT-等待, DETOUR-绕行, YIELD-让行, REASSIGN-重分配, DEADLOCK_RECOVERY-死锁恢复
     */
    @Enumerated(EnumType.ORDINAL)
    private ConflictResolutionStrategy resolutionStrategy;

    /**
     * 解决详情描述
     */
    @Column(length = 1024)
    private String resolutionDetail;

    /**
     * 是否已解决
     */
    private Boolean resolved;

    /**
     * 创建时间（死锁检测时间）
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    /**
     * 解决时间
     */
    private LocalDateTime resolvedTime;
}

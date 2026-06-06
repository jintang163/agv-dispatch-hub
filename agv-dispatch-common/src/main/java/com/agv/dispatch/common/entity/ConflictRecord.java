package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.ConflictResolutionStrategy;
import com.agv.dispatch.common.enums.ConflictType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 冲突记录实体
 * 记录AGV之间检测到的冲突情况及解决结果，用于冲突分析和审计
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Entity
@Table(name = "agv_conflict_record", indexes = {
        @Index(name = "idx_conflict_create_time", columnList = "createTime"),
        @Index(name = "idx_conflict_resolved", columnList = "resolved")
})
public class ConflictRecord {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 冲突类型：PATH-路径冲突, POSITION-位置冲突, RESOURCE-资源冲突, CROSS-交叉冲突, HEAD_ON-对向冲突, FOLLOW-跟车冲突
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ConflictType conflictType;

    /**
     * 冲突位置（节点编号）
     */
    @Column(length = 32)
    private String location;

    /**
     * 冲突涉及的AGV1 ID
     */
    @Column(length = 32)
    private String agvId1;

    /**
     * 冲突涉及的AGV2 ID
     */
    @Column(length = 32)
    private String agvId2;

    /**
     * AGV1的当前任务ID
     */
    @Column(length = 32)
    private String taskId1;

    /**
     * AGV2的当前任务ID
     */
    @Column(length = 32)
    private String taskId2;

    /**
     * 解决措施描述
     */
    @Column(length = 512)
    private String resolution;

    /**
     * 采用的解决策略：WAIT-等待, DETOUR-绕行, YIELD-让行, REASSIGN-重分配, DEADLOCK_RECOVERY-死锁恢复
     */
    @Enumerated(EnumType.ORDINAL)
    private ConflictResolutionStrategy resolutionStrategy;

    /**
     * 是否已解决
     */
    private Boolean resolved;

    /**
     * 创建时间（冲突检测时间）
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    /**
     * 解决时间
     */
    private LocalDateTime resolveTime;
}

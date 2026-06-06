package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.ConflictResolutionStrategy;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agv_deadlock_record", indexes = {
        @Index(name = "idx_deadlock_create_time", columnList = "createTime"),
        @Index(name = "idx_deadlock_resolved", columnList = "resolved")
})
public class DeadlockRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1024, nullable = false)
    private String waitChain;

    @Column(nullable = false)
    private Integer agvCount;

    @Column(length = 32)
    private String selectedAgvId;

    @Enumerated(EnumType.ORDINAL)
    private ConflictResolutionStrategy resolutionStrategy;

    @Column(length = 1024)
    private String resolutionDetail;

    private Boolean resolved;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    private LocalDateTime resolvedTime;
}

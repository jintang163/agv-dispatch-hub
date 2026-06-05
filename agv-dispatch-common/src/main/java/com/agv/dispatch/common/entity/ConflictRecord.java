package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.ConflictType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agv_conflict_record", indexes = {
        @Index(name = "idx_create_time", columnList = "createTime")
})
public class ConflictRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32)
    private String agvId1;

    @Column(length = 32)
    private String agvId2;

    @Column(nullable = false)
    private ConflictType conflictType;

    @Column(length = 32)
    private String location;

    @Column(length = 32)
    private String taskId1;

    @Column(length = 32)
    private String taskId2;

    @Column(length = 512)
    private String resolution;

    private Boolean resolved;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    private LocalDateTime resolvedTime;
}

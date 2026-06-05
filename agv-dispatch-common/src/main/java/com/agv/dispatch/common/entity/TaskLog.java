package com.agv.dispatch.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agv_task_log", indexes = {
        @Index(name = "idx_task_id", columnList = "taskId")
})
public class TaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String taskId;

    @Column(length = 32)
    private String agvId;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(length = 32)
    private String oldStatus;

    @Column(length = 32)
    private String newStatus;

    @Column(length = 512)
    private String remark;

    @Column(length = 64)
    private String operator;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;
}

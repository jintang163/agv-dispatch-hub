package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.common.enums.TaskType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agv_task", indexes = {
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_priority_deadline", columnList = "priority, deadline"),
        @Index(name = "idx_agv_id", columnList = "agvId")
})
public class Task {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 64)
    private String taskNo;

    @Column(nullable = false)
    private TaskType taskType;

    @Column(nullable = false)
    private TaskPriority priority;

    @Column(nullable = false)
    private TaskStatus status;

    @Column(length = 32)
    private String agvId;

    @Column(nullable = false, length = 32)
    private String startPoint;

    @Column(nullable = false, length = 32)
    private String endPoint;

    private Double loadWeight;

    @Column(length = 128)
    private String cargoInfo;

    private LocalDateTime deadline;

    @Column(length = 512)
    private String remark;

    private LocalDateTime assignedTime;

    private LocalDateTime startTime;

    private LocalDateTime completedTime;

    @Column(length = 256)
    private String path;

    private Integer currentStep;

    @Column(length = 128)
    private String wmsOrderNo;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}

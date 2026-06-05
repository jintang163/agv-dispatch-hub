package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.AgvStatus;
import com.agv.dispatch.common.enums.TaskType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "agv_robot", indexes = {
        @Index(name = "idx_status", columnList = "status")
})
public class Agv {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, unique = true, length = 32)
    private String agvNo;

    @Column(length = 64)
    private String name;

    @Column(nullable = false)
    private AgvStatus status;

    @Column(length = 32)
    private String currentTaskId;

    @Column(length = 32)
    private String currentPosition;

    private Double batteryLevel;

    private Double xCoord;

    private Double yCoord;

    private Double angle;

    private Double speed;

    @Column(length = 128)
    private String model;

    private Double maxLoad;

    @Convert(converter = TaskTypeListConverter.class)
    @Column(columnDefinition = "VARCHAR(256)")
    private List<TaskType> allowedTaskTypes;

    @Column(length = 128)
    private String ipAddress;

    private LocalDateTime lastHeartbeat;

    @Column(length = 256)
    private String faultCode;

    @Column(length = 512)
    private String faultMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}

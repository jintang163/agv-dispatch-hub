package com.agv.dispatch.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agv_map_node")
public class MapNode {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, unique = true, length = 32)
    private String nodeCode;

    @Column(length = 64)
    private String nodeName;

    @Column(nullable = false)
    private Double xCoord;

    @Column(nullable = false)
    private Double yCoord;

    @Column(length = 32)
    private String nodeType;

    private Boolean isChargingStation;

    private Boolean isTransferStation;

    @Column(length = 128)
    private String connectedNodes;

    @Column(length = 512)
    private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}

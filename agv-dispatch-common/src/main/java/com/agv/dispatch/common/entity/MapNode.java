package com.agv.dispatch.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 地图节点实体
 * 表示AGV调度地图中的一个节点，包括普通节点、路口、通道、充电站、换乘站等
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Entity
@Table(name = "agv_map_node")
public class MapNode {

    /**
     * 主键ID
     */
    @Id
    @Column(length = 32)
    private String id;

    /**
     * 节点编码，唯一标识
     */
    @Column(nullable = false, unique = true, length = 32)
    private String nodeCode;

    /**
     * 节点名称
     */
    @Column(length = 64)
    private String nodeName;

    /**
     * X坐标（米）
     */
    @Column(nullable = false)
    private Double xCoord;

    /**
     * Y坐标（米）
     */
    @Column(nullable = false)
    private Double yCoord;

    /**
     * 节点类型：NORMAL-普通节点, INTERSECTION-路口, PASSAGE-通道, STATION-站点
     */
    @Column(length = 32)
    private String nodeType;

    /**
     * 是否为充电站
     */
    private Boolean isChargingStation;

    /**
     * 是否为换乘站
     */
    private Boolean isTransferStation;

    /**
     * 是否为路口节点，路口节点需要特殊的会车协议处理
     */
    private Boolean isIntersection;

    /**
     * 是否为通道节点，通道节点通常容量有限
     */
    private Boolean isPassage;

    /**
     * 是否为关键节点（路口、充电站、换乘站等），关键节点会进行重点冲突检测
     */
    private Boolean isCriticalPoint;

    /**
     * 通道容量（可同时容纳的AGV数量），默认1
     */
    private Integer passageCapacity;

    /**
     * 该节点的限速（米/秒），null表示使用全局默认速度
     */
    private Double speedLimit;

    /**
     * 相邻连接的节点编码，多个用逗号分隔
     */
    @Column(length = 128)
    private String connectedNodes;

    /**
     * 备注信息
     */
    @Column(length = 512)
    private String remark;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    private LocalDateTime updateTime;
}

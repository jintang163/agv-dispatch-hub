package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.TaskExecutionStatus;
import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.common.enums.TaskType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 任务实体
 * 表示AGV调度系统中的一个任务，包含任务的基本信息、执行状态、路径信息等
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Entity
@Table(name = "agv_task", indexes = {
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_execution_status", columnList = "executionStatus"),
        @Index(name = "idx_priority_deadline", columnList = "priority, deadline"),
        @Index(name = "idx_agv_id", columnList = "agvId"),
        @Index(name = "idx_start_time", columnList = "startTime")
})
public class Task {

    /**
     * 主键ID
     */
    @Id
    @Column(length = 32)
    private String id;

    /**
     * 任务编号
     */
    @Column(nullable = false, length = 64)
    private String taskNo;

    /**
     * 任务类型：TRANSPORT-搬运, PICKING-拣选, CHARGING-充电等
     */
    @Column(nullable = false)
    private TaskType taskType;

    /**
     * 任务优先级：LOW-低, MEDIUM-中, HIGH-高, URGENT-紧急
     */
    @Column(nullable = false)
    private TaskPriority priority;

    /**
     * 任务状态：PENDING-待分配, ASSIGNED-已分配, EXECUTING-执行中, COMPLETED-已完成, CANCELLED-已取消, ABNORMAL-异常
     */
    @Column(nullable = false)
    private TaskStatus status;

    /**
     * 任务执行状态：详细的执行过程状态
     */
    @Enumerated(EnumType.ORDINAL)
    private TaskExecutionStatus executionStatus;

    /**
     * 分配的AGV ID
     */
    @Column(length = 32)
    private String agvId;

    /**
     * 起点节点编号
     */
    @Column(nullable = false, length = 32)
    private String startPoint;

    /**
     * 终点节点编号
     */
    @Column(nullable = false, length = 32)
    private String endPoint;

    /**
     * 当前所在节点编号
     */
    @Column(length = 32)
    private String currentNode;

    /**
     * 载重（kg）
     */
    private Double loadWeight;

    /**
     * 货物信息
     */
    @Column(length = 128)
    private String cargoInfo;

    /**
     * 任务截止时间
     */
    private LocalDateTime deadline;

    /**
     * 预计完成时间
     */
    private LocalDateTime estimatedCompleteTime;

    /**
     * 任务超时时间（秒）
     */
    private Integer timeoutSeconds;

    /**
     * 节点占用超时时间（秒）
     */
    private Integer nodeTimeoutSeconds;

    /**
     * 备注
     */
    @Column(length = 512)
    private String remark;

    /**
     * 任务分配时间
     */
    private LocalDateTime assignedTime;

    /**
     * 任务下发时间
     */
    private LocalDateTime dispatchedTime;

    /**
     * 任务开始执行时间
     */
    private LocalDateTime startTime;

    /**
     * 最近一次节点到达时间
     */
    private LocalDateTime lastNodeArrivalTime;

    /**
     * 任务完成时间
     */
    private LocalDateTime completedTime;

    /**
     * 暂停时间
     */
    private LocalDateTime pausedTime;

    /**
     * 路径节点序列（JSON数组格式）
     */
    @Column(length = 1024)
    private String path;

    /**
     * 原始路径（用于绕行后对比）
     */
    @Column(length = 1024)
    private String originalPath;

    /**
     * 当前执行步骤索引
     */
    private Integer currentStep;

    /**
     * 总步骤数
     */
    private Integer totalSteps;

    /**
     * 路径总长度（米）
     */
    private Double totalDistance;

    /**
     * 预计行驶时间（秒）
     */
    private Double estimatedTime;

    /**
     * 进度百分比（AGV上报）
     */
    private Integer progress;

    /**
     * WMS订单号
     */
    @Column(length = 128)
    private String wmsOrderNo;

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

    /**
     * 计算任务进度百分比
     *
     * @return 进度百分比
     */
    public Integer calculateProgress() {
        if (currentStep == null || totalSteps == null || totalSteps == 0) {
            return 0;
        }
        return (int) Math.round((double) currentStep / totalSteps * 100);
    }

    /**
     * 检查任务是否超时
     *
     * @return 是否超时
     */
    public boolean isTimeout() {
        if (startTime == null || estimatedCompleteTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(estimatedCompleteTime);
    }
}

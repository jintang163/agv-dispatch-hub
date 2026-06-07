package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.OperationType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 * 记录用户在系统中的所有操作
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Data
@Entity
@Table(name = "operation_log", indexes = {
        @Index(name = "idx_operation_type", columnList = "operationType"),
        @Index(name = "idx_operator", columnList = "operator"),
        @Index(name = "idx_create_time", columnList = "createTime"),
        @Index(name = "idx_task_id", columnList = "taskId"),
        @Index(name = "idx_agv_id", columnList = "agvId")
})
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private OperationType operationType;

    @Column(nullable = false, length = 64)
    private String operator;

    @Column(length = 64)
    private String operatorName;

    @Column(length = 32)
    private String operationIp;

    @Column(length = 512)
    private String operationDetail;

    @Column(length = 64)
    private String taskId;

    @Column(length = 64)
    private String taskNo;

    @Column(length = 64)
    private String agvId;

    @Column(length = 64)
    private String agvNo;

    @Column(length = 1024)
    private String beforeData;

    @Column(length = 1024)
    private String afterData;

    @Column(nullable = false)
    private Boolean success = true;

    @Column(length = 512)
    private String errorMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    private Long executeTime;

    public static OperationLog create(OperationType type, String operator) {
        OperationLog log = new OperationLog();
        log.setOperationType(type);
        log.setOperator(operator);
        return log;
    }

    public OperationLog taskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public OperationLog taskNo(String taskNo) {
        this.taskNo = taskNo;
        return this;
    }

    public OperationLog agvId(String agvId) {
        this.agvId = agvId;
        return this;
    }

    public OperationLog agvNo(String agvNo) {
        this.agvNo = agvNo;
        return this;
    }

    public OperationLog detail(String detail) {
        this.operationDetail = detail;
        return this;
    }

    public OperationLog success(boolean success) {
        this.success = success;
        return this;
    }

    public OperationLog error(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
        return this;
    }

    public OperationLog executeTime(Long executeTime) {
        this.executeTime = executeTime;
        return this;
    }

    public OperationLog beforeData(String beforeData) {
        this.beforeData = beforeData;
        return this;
    }

    public OperationLog afterData(String afterData) {
        this.afterData = afterData;
        return this;
    }
}

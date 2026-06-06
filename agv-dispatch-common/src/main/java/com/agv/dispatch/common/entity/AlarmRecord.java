package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.AlarmType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 告警记录实体
 * 记录系统中产生的各类告警信息
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Entity
@Table(name = "agv_alarm_record", indexes = {
        @Index(name = "idx_alarm_type", columnList = "alarmType"),
        @Index(name = "idx_alarm_level", columnList = "alarmLevel"),
        @Index(name = "idx_alarm_handled", columnList = "handled"),
        @Index(name = "idx_alarm_create_time", columnList = "createTime")
})
public class AlarmRecord {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 告警类型
     */
    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private AlarmType alarmType;

    /**
     * 告警级别：WARNING-警告, ERROR-错误
     */
    @Column(length = 16, nullable = false)
    private String alarmLevel;

    /**
     * 告警标题
     */
    @Column(length = 128, nullable = false)
    private String title;

    /**
     * 告警详情描述
     */
    @Column(length = 512)
    private String description;

    /**
     * 关联的AGV ID
     */
    @Column(length = 32)
    private String agvId;

    /**
     * 关联的任务ID
     */
    @Column(length = 32)
    private String taskId;

    /**
     * 关联的节点编号
     */
    @Column(length = 32)
    private String nodeCode;

    /**
     * 是否已处理
     */
    private Boolean handled;

    /**
     * 处理结果
     */
    @Column(length = 512)
    private String handleResult;

    /**
     * 处理人
     */
    @Column(length = 32)
    private String handler;

    /**
     * 创建时间（告警产生时间）
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;
}

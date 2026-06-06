package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.AlarmRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 告警记录数据访问接口
 * 提供告警记录的增删改查及自定义查询方法
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Repository
public interface AlarmRecordRepository extends JpaRepository<AlarmRecord, Long> {

    /**
     * 查询所有未处理的告警记录，按创建时间倒序排列
     *
     * @return 未处理的告警记录列表
     */
    List<AlarmRecord> findByHandledFalseOrderByCreateTimeDesc();

    /**
     * 根据AGV ID查询该AGV相关的未处理告警记录
     *
     * @param agvId AGV编号
     * @return 该AGV的未处理告警记录列表
     */
    List<AlarmRecord> findByAgvIdAndHandledFalse(String agvId);

    /**
     * 根据任务ID查询该任务相关的所有告警记录
     *
     * @param taskId 任务ID
     * @return 该任务的告警记录列表
     */
    List<AlarmRecord> findByTaskId(String taskId);

    /**
     * 根据告警级别查询该级别所有未处理的告警记录
     *
     * @param alarmLevel 告警级别（WARNING/ERROR）
     * @return 该级别的未处理告警记录列表
     */
    List<AlarmRecord> findByAlarmLevelAndHandledFalse(String alarmLevel);

    /**
     * 统计所有未处理的告警记录数量
     *
     * @return 未处理告警记录总数
     */
    long countByHandledFalse();
}

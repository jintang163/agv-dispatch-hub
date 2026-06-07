package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.OperationLog;
import com.agv.dispatch.common.enums.OperationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志数据访问接口
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    Page<OperationLog> findByOperator(String operator, Pageable pageable);

    Page<OperationLog> findByOperationType(OperationType operationType, Pageable pageable);

    Page<OperationLog> findByTaskId(String taskId, Pageable pageable);

    Page<OperationLog> findByAgvId(String agvId, Pageable pageable);

    Page<OperationLog> findByCreateTimeBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    Page<OperationLog> findByOperatorAndOperationType(String operator, OperationType operationType, Pageable pageable);

    List<OperationLog> findByOperatorOrderByCreateTimeDesc(String operator);

    List<OperationLog> findTop10ByOrderByCreateTimeDesc();

    long countByOperationTypeAndSuccess(OperationType operationType, Boolean success);

    long countByCreateTimeBetween(LocalDateTime startTime, LocalDateTime endTime);
}

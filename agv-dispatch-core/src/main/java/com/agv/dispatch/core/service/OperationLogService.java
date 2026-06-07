package com.agv.dispatch.core.service;

import com.agv.dispatch.common.context.UserContext;
import com.agv.dispatch.common.entity.OperationLog;
import com.agv.dispatch.common.enums.OperationType;
import com.agv.dispatch.common.util.JsonUtil;
import com.agv.dispatch.core.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志服务
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(OperationLog operationLog) {
        try {
            operationLogRepository.save(operationLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(OperationType type, String operator, String operatorName,
                             String detail, Map<String, Object> params) {
        try {
            OperationLog opLog = OperationLog.create(type, operator)
                    .operatorName(operatorName)
                    .detail(detail);

            if (params != null) {
                if (params.containsKey("taskId")) {
                    opLog.taskId(String.valueOf(params.get("taskId")));
                }
                if (params.containsKey("taskNo")) {
                    opLog.taskNo(String.valueOf(params.get("taskNo")));
                }
                if (params.containsKey("agvId")) {
                    opLog.agvId(String.valueOf(params.get("agvId")));
                }
                if (params.containsKey("agvNo")) {
                    opLog.agvNo(String.valueOf(params.get("agvNo")));
                }
                if (params.containsKey("beforeData")) {
                    opLog.beforeData(JsonUtil.toJson(params.get("beforeData")));
                }
                if (params.containsKey("afterData")) {
                    opLog.afterData(JsonUtil.toJson(params.get("afterData")));
                }
                if (params.containsKey("success")) {
                    opLog.success((Boolean) params.get("success"));
                }
                if (params.containsKey("errorMessage")) {
                    opLog.error(String.valueOf(params.get("errorMessage")));
                }
            }

            operationLogRepository.save(opLog);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(OperationType type, String operator, String operatorName,
                             String taskId, String taskNo, String agvId, String agvNo,
                             String detail, boolean success, String errorMessage, Long executeTime) {
        try {
            OperationLog opLog = OperationLog.create(type, operator)
                    .operatorName(operatorName)
                    .taskId(taskId)
                    .taskNo(taskNo)
                    .agvId(agvId)
                    .agvNo(agvNo)
                    .detail(detail)
                    .success(success)
                    .error(errorMessage)
                    .executeTime(executeTime);
            operationLogRepository.save(opLog);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(OperationType type, String operator, String operatorName,
                             String taskId, String taskNo, String agvId, String agvNo,
                             String detail, boolean success, String errorMessage, Long executeTime,
                             String beforeData, String afterData) {
        try {
            OperationLog opLog = OperationLog.create(type, operator)
                    .operatorName(operatorName)
                    .taskId(taskId)
                    .taskNo(taskNo)
                    .agvId(agvId)
                    .agvNo(agvNo)
                    .detail(detail)
                    .success(success)
                    .error(errorMessage)
                    .executeTime(executeTime)
                    .beforeData(beforeData)
                    .afterData(afterData);
            operationLogRepository.save(opLog);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(OperationType type,
                             String taskId, String taskNo, String agvId, String agvNo,
                             String detail, boolean success, String errorMessage, Long executeTime,
                             String beforeData, String afterData) {
        try {
            String operator = UserContext.getUsername();
            String operatorName = UserContext.getRealName();
            String operationIp = UserContext.getIp();

            OperationLog opLog = OperationLog.create(type, operator)
                    .operatorName(operatorName)
                    .operationIp(operationIp)
                    .taskId(taskId)
                    .taskNo(taskNo)
                    .agvId(agvId)
                    .agvNo(agvNo)
                    .detail(detail)
                    .success(success)
                    .error(errorMessage)
                    .executeTime(executeTime)
                    .beforeData(beforeData)
                    .afterData(afterData);
            operationLogRepository.save(opLog);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(OperationType type,
                             String taskId, String taskNo, String agvId, String agvNo,
                             String detail, boolean success, Long executeTime,
                             String beforeData, String afterData) {
        logOperation(type, taskId, taskNo, agvId, agvNo, detail, success, null, executeTime, beforeData, afterData);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(OperationType type,
                             String taskId, String taskNo, String agvId, String agvNo,
                             String detail, boolean success, Long executeTime) {
        logOperation(type, taskId, taskNo, agvId, agvNo, detail, success, null, executeTime, null, null);
    }

    public Page<OperationLog> queryLogs(String operator, OperationType operationType,
                                         String taskId, String agvId,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         Boolean success, Pageable pageable) {
        Specification<OperationLog> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (operator != null && !operator.isEmpty()) {
                predicates = cb.and(predicates, cb.like(root.get("operator"), "%" + operator + "%"));
            }
            if (operationType != null) {
                predicates = cb.and(predicates, cb.equal(root.get("operationType"), operationType));
            }
            if (taskId != null && !taskId.isEmpty()) {
                predicates = cb.and(predicates, cb.equal(root.get("taskId"), taskId));
            }
            if (agvId != null && !agvId.isEmpty()) {
                predicates = cb.and(predicates, cb.equal(root.get("agvId"), agvId));
            }
            if (startTime != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            if (success != null) {
                predicates = cb.and(predicates, cb.equal(root.get("success"), success));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return predicates;
        };

        return operationLogRepository.findAll(spec, pageable);
    }

    public OperationLog getLogById(Long id) {
        return operationLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("日志不存在"));
    }

    public List<OperationLog> getRecentLogs(int limit) {
        return operationLogRepository.findTop10ByOrderByCreateTimeDesc();
    }

    public List<OperationLog> getUserLogs(String operator) {
        return operationLogRepository.findByOperatorOrderByCreateTimeDesc(operator);
    }

    public Map<String, Object> getLogStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        long totalCount = operationLogRepository.countByCreateTimeBetween(startTime, endTime);
        long successCount = 0;
        long failCount = 0;

        for (OperationType type : OperationType.values()) {
            successCount += operationLogRepository.countByOperationTypeAndSuccess(type, true);
            failCount += operationLogRepository.countByOperationTypeAndSuccess(type, false);
        }

        return Map.of(
                "totalCount", totalCount,
                "successCount", successCount,
                "failCount", failCount,
                "successRate", totalCount > 0 ? (double) successCount / totalCount * 100 : 0
        );
    }
}

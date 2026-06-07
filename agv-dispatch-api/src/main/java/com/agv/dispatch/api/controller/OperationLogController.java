package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.dto.Result;
import com.agv.dispatch.common.entity.OperationLog;
import com.agv.dispatch.common.enums.OperationType;
import com.agv.dispatch.core.service.AuthService;
import com.agv.dispatch.core.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志接口
 *
 * @author agv-dispatch
 * @since 2026-06-07
 */
@RestController
@RequestMapping("/api/v1/operation-logs")
@RequiredArgsConstructor
@Tag(name = "操作日志", description = "操作日志查询、统计、导出等接口")
public class OperationLogController {

    private final OperationLogService operationLogService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "分页查询操作日志")
    public Result<Page<OperationLog>> getLogs(
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "操作类型") @RequestParam(required = false) OperationType operationType,
            @Parameter(description = "任务ID") @RequestParam(required = false) String taskId,
            @Parameter(description = "AGV ID") @RequestParam(required = false) String agvId,
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @Parameter(description = "是否成功") @RequestParam(required = false) Boolean success,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        if (!authService.hasPermission("log:view")) {
            return Result.fail(403, "没有查看日志的权限");
        }
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        Page<OperationLog> page = operationLogService.queryLogs(
                operator, operationType, taskId, agvId, startTime, endTime, success, pageable);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取日志详情")
    public Result<OperationLog> getLogById(@Parameter(description = "日志ID") @PathVariable Long id) {
        if (!authService.hasPermission("log:view")) {
            return Result.fail(403, "没有查看日志的权限");
        }
        OperationLog log = operationLogService.getLogById(id);
        return Result.success(log);
    }

    @GetMapping("/recent")
    @Operation(summary = "获取最近的操作日志")
    public Result<List<OperationLog>> getRecentLogs(
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "10") int limit) {
        if (!authService.hasPermission("log:view")) {
            return Result.fail(403, "没有查看日志的权限");
        }
        List<OperationLog> logs = operationLogService.getRecentLogs(limit);
        return Result.success(logs);
    }

    @GetMapping("/user/{operator}")
    @Operation(summary = "获取指定用户的操作日志")
    public Result<List<OperationLog>> getUserLogs(@Parameter(description = "操作人") @PathVariable String operator) {
        if (!authService.hasPermission("log:view")) {
            return Result.fail(403, "没有查看日志的权限");
        }
        List<OperationLog> logs = operationLogService.getUserLogs(operator);
        return Result.success(logs);
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取日志统计信息")
    public Result<Map<String, Object>> getStatistics(
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        if (!authService.hasPermission("log:view")) {
            return Result.fail(403, "没有查看日志的权限");
        }
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        Map<String, Object> statistics = operationLogService.getLogStatistics(startTime, endTime);
        return Result.success(statistics);
    }

    @GetMapping("/types")
    @Operation(summary = "获取所有操作类型")
    public Result<OperationType[]> getOperationTypes() {
        return Result.success(OperationType.values());
    }

    @GetMapping("/export")
    @Operation(summary = "导出操作日志")
    public Result<String> exportLogs(
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "操作类型") @RequestParam(required = false) OperationType operationType,
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        if (!authService.hasPermission("log:export")) {
            return Result.fail(403, "没有导出日志的权限");
        }
        return Result.success("导出功能待实现");
    }
}

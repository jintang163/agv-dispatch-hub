package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.dto.PathPlanningResult;
import com.agv.dispatch.common.dto.Result;
import com.agv.dispatch.common.entity.ConflictRecord;
import com.agv.dispatch.common.entity.DeadlockRecord;
import com.agv.dispatch.core.service.ConflictDetectionService;
import com.agv.dispatch.core.service.DeadlockDetectionService;
import com.agv.dispatch.core.service.PathPlanningService;
import com.agv.dispatch.core.service.TaskDispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 调度控制器
 * 负责任务调度、冲突检测与解决、死锁检测与处理
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
@Tag(name = "调度控制", description = "任务调度、冲突检测与解决、死锁处理接口")
public class DispatchController {

    private final ConflictDetectionService conflictDetectionService;
    private final DeadlockDetectionService deadlockDetectionService;
    private final PathPlanningService pathPlanningService;
    private final TaskDispatchService taskDispatchService;

    // ==================== 冲突检测与解决 ====================

    /**
     * 检测所有冲突
     * 检测AGV之间的路径冲突、位置冲突、资源冲突和路口冲突
     *
     * @return 冲突记录列表
     */
    @GetMapping("/conflicts")
    @Operation(summary = "检测所有冲突", description = "检测AGV之间的路径冲突、位置冲突、资源冲突和路口冲突")
    public Result<List<ConflictRecord>> detectConflicts() {
        List<ConflictRecord> conflicts = conflictDetectionService.detectConflicts();
        return Result.success(conflicts);
    }

    /**
     * 获取未解决的冲突
     *
     * @return 未解决的冲突记录列表
     */
    @GetMapping("/conflicts/unresolved")
    @Operation(summary = "获取未解决的冲突", description = "获取所有尚未解决的冲突记录")
    public Result<List<ConflictRecord>> getUnresolvedConflicts() {
        List<ConflictRecord> conflicts = conflictDetectionService.getUnresolvedConflicts();
        return Result.success(conflicts);
    }

    /**
     * 解决单个冲突
     * 根据冲突类型自动选择最优的解决策略
     *
     * @param conflictId 冲突记录ID
     * @return 解决详情
     */
    @PostMapping("/conflicts/{conflictId}/resolve")
    @Operation(summary = "解决单个冲突", description = "根据冲突类型自动选择最优解决策略（等待/绕行/让行/重分配）")
    public Result<String> resolveConflict(
            @Parameter(description = "冲突记录ID") @PathVariable Long conflictId) {
        String resolution = conflictDetectionService.resolveConflict(conflictId);
        return Result.success(resolution);
    }

    /**
     * 解决所有冲突
     * 批量处理所有未解决的冲突
     */
    @PostMapping("/conflicts/resolve-all")
    @Operation(summary = "解决所有冲突", description = "批量处理所有未解决的冲突")
    public Result<Void> resolveAllConflicts() {
        conflictDetectionService.resolveAllConflicts();
        return Result.success();
    }

    // ==================== 死锁检测与处理 ====================

    /**
     * 手动触发死锁检测
     * 检测系统中是否存在循环等待的死锁情况
     *
     * @return 死锁记录列表
     */
    @GetMapping("/deadlocks/detect")
    @Operation(summary = "手动触发死锁检测", description = "检测系统中是否存在循环等待的死锁情况")
    public Result<List<DeadlockRecord>> detectDeadlocks() {
        List<DeadlockRecord> deadlocks = deadlockDetectionService.detectAndResolveDeadlocks();
        return Result.success(deadlocks);
    }

    /**
     * 获取未解决的死锁
     *
     * @return 未解决的死锁记录列表
     */
    @GetMapping("/deadlocks")
    @Operation(summary = "获取当前死锁", description = "获取所有尚未解决的死锁记录")
    public Result<List<DeadlockRecord>> getCurrentDeadlocks() {
        List<DeadlockRecord> deadlocks = taskDispatchService.getCurrentDeadlocks();
        return Result.success(deadlocks);
    }

    /**
     * 手动解决单个死锁
     * 强制解除指定的死锁，选择牺牲AGV重新规划
     *
     * @param deadlockId 死锁记录ID
     * @return 解决结果
     */
    @PostMapping("/deadlocks/{deadlockId}/resolve")
    @Operation(summary = "手动解决死锁", description = "强制解除指定死锁，选择牺牲AGV重新规划路径")
    public Result<String> resolveDeadlock(
            @Parameter(description = "死锁记录ID") @PathVariable Long deadlockId) {
        String result = taskDispatchService.resolveDeadlockManually(deadlockId);
        return Result.success(result);
    }

    /**
     * 强制解决所有死锁
     * 批量处理所有未解决的死锁
     */
    @PostMapping("/deadlocks/resolve-all")
    @Operation(summary = "强制解决所有死锁", description = "批量处理所有未解决的死锁")
    public Result<Void> resolveAllDeadlocks() {
        taskDispatchService.forceResolveAllDeadlocks();
        return Result.success();
    }

    // ==================== 动态重规划 ====================

    /**
     * 动态重规划任务路径
     * 当路径被阻塞或任务变更时，从当前位置重新规划路径
     *
     * @param taskId 任务ID
     * @param blockedNode 阻塞的节点编号（可选）
     * @param reason 阻塞原因（可选）
     * @param operator 操作人（可选）
     * @return 重规划结果
     */
    @PostMapping("/tasks/{taskId}/dynamic-replan")
    @Operation(summary = "动态重规划任务", description = "当路径被阻塞或任务变更时，从当前位置重新规划路径")
    public Result<PathPlanningResult> dynamicReplanTask(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "阻塞的节点编号") @RequestParam(required = false) String blockedNode,
            @Parameter(description = "阻塞原因") @RequestParam(required = false) String reason,
            @Parameter(description = "操作人") @RequestParam(required = false, defaultValue = "admin") String operator) {
        PathPlanningResult result = taskDispatchService.dynamicReplanTask(taskId, blockedNode, reason, operator);
        return Result.success(result);
    }

    /**
     * 从当前位置重规划任务路径
     * 不考虑阻塞，直接从任务当前执行位置重新规划最优路径
     *
     * @param taskId 任务ID
     * @param operator 操作人（可选）
     * @return 重规划结果
     */
    @PostMapping("/tasks/{taskId}/replan")
    @Operation(summary = "从当前位置重规划", description = "从任务当前执行位置重新规划最优路径")
    public Result<PathPlanningResult> replanTaskFromCurrent(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "操作人") @RequestParam(required = false, defaultValue = "admin") String operator) {
        PathPlanningResult result = taskDispatchService.replanTaskFromCurrent(taskId, operator);
        return Result.success(result);
    }

    /**
     * 处理路径阻塞
     * AGV报告路径阻塞，系统自动标记阻塞节点并尝试重规划
     *
     * @param agvId AGV ID
     * @param blockedNode 阻塞节点编号
     * @param reason 阻塞原因
     */
    @PostMapping("/path-blocked")
    @Operation(summary = "处理路径阻塞", description = "AGV报告路径阻塞，系统自动标记阻塞节点并尝试重规划")
    public Result<Void> handlePathBlocked(
            @Parameter(description = "AGV ID") @RequestParam String agvId,
            @Parameter(description = "阻塞节点编号") @RequestParam String blockedNode,
            @Parameter(description = "阻塞原因") @RequestParam String reason) {
        taskDispatchService.handlePathBlocked(agvId, blockedNode, reason);
        return Result.success();
    }

    // ==================== 状态查询 ====================

    /**
     * 获取所有被阻塞的路径节点
     *
     * @return 阻塞节点映射（节点编号 -> 阻塞原因）
     */
    @GetMapping("/blocked-paths")
    @Operation(summary = "获取阻塞路径", description = "获取所有被标记为阻塞的节点及其原因")
    public Result<Map<String, String>> getBlockedPaths() {
        Map<String, String> blocked = taskDispatchService.getAllBlockedPaths();
        return Result.success(blocked);
    }

    /**
     * 获取所有被锁定的路口
     *
     * @return 锁定路口映射（路口编号 -> 锁定AGV ID）
     */
    @GetMapping("/locked-intersections")
    @Operation(summary = "获取锁定路口", description = "获取所有被AGV锁定的路口")
    public Result<Map<String, String>> getLockedIntersections() {
        Map<String, String> locked = taskDispatchService.getAllLockedIntersections();
        return Result.success(locked);
    }

    /**
     * 标记节点为阻塞状态
     *
     * @param nodeCode 节点编号
     * @param reason 阻塞原因
     */
    @PostMapping("/blocked/{nodeCode}")
    @Operation(summary = "标记节点阻塞", description = "手动标记某个节点为阻塞状态")
    public Result<Void> markPathBlocked(
            @Parameter(description = "节点编号") @PathVariable String nodeCode,
            @Parameter(description = "阻塞原因") @RequestParam String reason) {
        pathPlanningService.markPathBlocked(nodeCode, reason);
        return Result.success();
    }

    /**
     * 清除节点阻塞标记
     *
     * @param nodeCode 节点编号
     */
    @DeleteMapping("/blocked/{nodeCode}")
    @Operation(summary = "清除节点阻塞", description = "清除节点的阻塞标记")
    public Result<Void> clearPathBlocked(
            @Parameter(description = "节点编号") @PathVariable String nodeCode) {
        taskDispatchService.clearPathBlocked(nodeCode);
        return Result.success();
    }
}

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
import java.util.Set;

@RestController
@RequestMapping("/api/v1/path-planning")
@RequiredArgsConstructor
@Tag(name = "路径规划与冲突解决", description = "路径规划、冲突检测、死锁处理、节点锁管理接口")
public class PathPlanningController {

    private final PathPlanningService pathPlanningService;
    private final ConflictDetectionService conflictDetectionService;
    private final DeadlockDetectionService deadlockDetectionService;
    private final TaskDispatchService taskDispatchService;

    @GetMapping("/plan")
    @Operation(summary = "路径规划", description = "使用指定算法规划从起点到终点的最优路径")
    public Result<PathPlanningResult> planPath(
            @Parameter(description = "起点节点编号") @RequestParam String startPoint,
            @Parameter(description = "终点节点编号") @RequestParam String endPoint,
            @Parameter(description = "算法类型: A* 或 DIJKSTRA, 默认 A*")
            @RequestParam(defaultValue = "A*") String algorithm) {
        PathPlanningResult result = pathPlanningService.planPathWithAlgorithm(startPoint, endPoint, algorithm);
        return Result.success(result);
    }

    @PostMapping("/plan-detour")
    @Operation(summary = "绕行规划", description = "规划避开指定节点的路径")
    public Result<PathPlanningResult> planPathWithDetour(
            @Parameter(description = "起点节点编号") @RequestParam String startPoint,
            @Parameter(description = "终点节点编号") @RequestParam String endPoint,
            @Parameter(description = "需要避开的节点列表") @RequestBody Set<String> avoidNodes) {
        PathPlanningResult result = pathPlanningService.planPathWithDetour(startPoint, endPoint, avoidNodes);
        return Result.success(result);
    }

    @PostMapping("/dynamic-replan/{taskId}")
    @Operation(summary = "动态重规划", description = "当路径被阻塞时，从当前位置重新规划路径")
    public Result<PathPlanningResult> dynamicReplan(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "阻塞的节点编号") @RequestParam(required = false) String blockedNode,
            @Parameter(description = "阻塞原因") @RequestParam(required = false) String reason,
            @Parameter(description = "操作人") @RequestParam(required = false, defaultValue = "admin") String operator) {
        PathPlanningResult result = taskDispatchService.dynamicReplanTask(taskId, blockedNode, reason, operator);
        return Result.success(result);
    }

    @PostMapping("/replan-from-current/{taskId}")
    @Operation(summary = "从当前位置重规划", description = "从任务当前执行位置重新规划最优路径")
    public Result<PathPlanningResult> replanFromCurrent(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "操作人") @RequestParam(required = false, defaultValue = "admin") String operator) {
        PathPlanningResult result = taskDispatchService.replanTaskFromCurrent(taskId, operator);
        return Result.success(result);
    }

    @GetMapping("/occupied")
    @Operation(summary = "获取被占用的路径节点")
    public Result<Map<String, String>> getOccupiedPaths() {
        Map<String, String> occupied = pathPlanningService.getOccupiedPaths();
        return Result.success(occupied);
    }

    @GetMapping("/blocked")
    @Operation(summary = "获取被阻塞的路径节点")
    public Result<Map<String, String>> getBlockedPaths() {
        Map<String, String> blocked = taskDispatchService.getAllBlockedPaths();
        return Result.success(blocked);
    }

    @PostMapping("/blocked/{nodeCode}")
    @Operation(summary = "标记节点阻塞", description = "手动标记某个节点为阻塞状态")
    public Result<Void> markPathBlocked(
            @Parameter(description = "节点编号") @PathVariable String nodeCode,
            @Parameter(description = "阻塞原因") @RequestParam String reason) {
        pathPlanningService.markPathBlocked(nodeCode, reason);
        return Result.success();
    }

    @DeleteMapping("/blocked/{nodeCode}")
    @Operation(summary = "清除节点阻塞", description = "清除节点的阻塞标记")
    public Result<Void> clearPathBlocked(
            @Parameter(description = "节点编号") @PathVariable String nodeCode) {
        taskDispatchService.clearPathBlocked(nodeCode);
        return Result.success();
    }

    @GetMapping("/locked-intersections")
    @Operation(summary = "获取被锁定的路口")
    public Result<Map<String, String>> getLockedIntersections() {
        Map<String, String> locked = taskDispatchService.getAllLockedIntersections();
        return Result.success(locked);
    }

    @PostMapping("/node-lock/{nodeCode}")
    @Operation(summary = "获取节点锁", description = "尝试获取指定节点的分布式锁")
    public Result<Boolean> tryLockNode(
            @Parameter(description = "节点编号") @PathVariable String nodeCode,
            @Parameter(description = "AGV ID") @RequestParam String agvId) {
        boolean locked = pathPlanningService.tryLockNode(nodeCode, agvId);
        return Result.success(locked);
    }

    @DeleteMapping("/node-lock/{nodeCode}")
    @Operation(summary = "释放节点锁", description = "释放指定节点的分布式锁")
    public Result<Void> unlockNode(
            @Parameter(description = "节点编号") @PathVariable String nodeCode,
            @Parameter(description = "AGV ID") @RequestParam String agvId) {
        pathPlanningService.unlockNode(nodeCode, agvId);
        return Result.success();
    }

    @GetMapping("/node-lock/{nodeCode}/holder")
    @Operation(summary = "获取节点锁持有者")
    public Result<String> getNodeLockHolder(
            @Parameter(description = "节点编号") @PathVariable String nodeCode) {
        String holder = pathPlanningService.getNodeLockHolder(nodeCode);
        return Result.success(holder);
    }

    @PostMapping("/intersection-pass/{intersectionCode}")
    @Operation(summary = "尝试通过路口", description = "AGV尝试获取路口通行权（路口会车协议）")
    public Result<Boolean> tryIntersectionPass(
            @Parameter(description = "路口节点编号") @PathVariable String intersectionCode,
            @Parameter(description = "AGV ID") @RequestParam String agvId) {
        boolean allowed = conflictDetectionService.tryIntersectionPass(agvId, intersectionCode);
        return Result.success(allowed);
    }

    @PostMapping("/intersection-pass/{intersectionCode}/complete")
    @Operation(summary = "完成路口通行", description = "AGV通过路口后释放路口锁")
    public Result<Void> completeIntersectionPass(
            @Parameter(description = "路口节点编号") @PathVariable String intersectionCode,
            @Parameter(description = "AGV ID") @RequestParam String agvId) {
        conflictDetectionService.completeIntersectionPass(agvId, intersectionCode);
        return Result.success();
    }

    @GetMapping("/conflicts")
    @Operation(summary = "检测所有冲突")
    public Result<List<ConflictRecord>> detectConflicts() {
        List<ConflictRecord> conflicts = conflictDetectionService.detectConflicts();
        return Result.success(conflicts);
    }

    @GetMapping("/conflicts/unresolved")
    @Operation(summary = "获取未解决的冲突")
    public Result<List<ConflictRecord>> getUnresolvedConflicts() {
        List<ConflictRecord> conflicts = conflictDetectionService.getUnresolvedConflicts();
        return Result.success(conflicts);
    }

    @PostMapping("/conflicts/{conflictId}/resolve")
    @Operation(summary = "解决单个冲突")
    public Result<String> resolveConflict(@PathVariable Long conflictId) {
        String resolution = conflictDetectionService.resolveConflict(conflictId);
        return Result.success(resolution);
    }

    @PostMapping("/conflicts/resolve-all")
    @Operation(summary = "解决所有冲突")
    public Result<Void> resolveAllConflicts() {
        conflictDetectionService.resolveAllConflicts();
        return Result.success();
    }

    @GetMapping("/deadlocks")
    @Operation(summary = "检测死锁")
    public Result<List<DeadlockRecord>> detectDeadlocks() {
        List<DeadlockRecord> deadlocks = deadlockDetectionService.detectDeadlocks();
        return Result.success(deadlocks);
    }

    @GetMapping("/deadlocks/unresolved")
    @Operation(summary = "获取未解决的死锁")
    public Result<List<DeadlockRecord>> getUnresolvedDeadlocks() {
        List<DeadlockRecord> deadlocks = taskDispatchService.getCurrentDeadlocks();
        return Result.success(deadlocks);
    }

    @PostMapping("/deadlocks/{deadlockId}/resolve")
    @Operation(summary = "解决单个死锁")
    public Result<String> resolveDeadlock(@PathVariable Long deadlockId) {
        String result = taskDispatchService.resolveDeadlockManually(deadlockId);
        return Result.success(result);
    }

    @PostMapping("/deadlocks/resolve-all")
    @Operation(summary = "解决所有死锁")
    public Result<Void> resolveAllDeadlocks() {
        taskDispatchService.forceResolveAllDeadlocks();
        return Result.success();
    }

    @PostMapping("/handle-path-blocked")
    @Operation(summary = "处理路径阻塞", description = "AGV报告路径阻塞，系统自动尝试重规划")
    public Result<Void> handlePathBlocked(
            @Parameter(description = "AGV ID") @RequestParam String agvId,
            @Parameter(description = "阻塞节点") @RequestParam String blockedNode,
            @Parameter(description = "阻塞原因") @RequestParam String reason) {
        taskDispatchService.handlePathBlocked(agvId, blockedNode, reason);
        return Result.success();
    }

    @PostMapping("/graph/init")
    @Operation(summary = "初始化地图拓扑图")
    public Result<Void> initGraph() {
        pathPlanningService.initGraph();
        return Result.success();
    }

    @PostMapping("/graph/refresh")
    @Operation(summary = "刷新节点缓存")
    public Result<Void> refreshNodeCache() {
        pathPlanningService.refreshNodeCache();
        return Result.success();
    }

    @GetMapping("/distance")
    @Operation(summary = "计算路径长度", description = "计算路径的总长度（米）")
    public Result<Double> calculatePathDistance(@RequestBody List<String> path) {
        double distance = pathPlanningService.calculatePathDistance(path);
        return Result.success(distance);
    }

    @GetMapping("/estimated-time")
    @Operation(summary = "估算路径时间", description = "估算路径的预计行驶时间（秒）")
    public Result<Double> estimateTravelTime(@RequestBody List<String> path) {
        double time = pathPlanningService.estimateTravelTime(path);
        return Result.success(time);
    }

    @GetMapping("/conflict-nodes")
    @Operation(summary = "获取冲突节点", description = "获取两条路径的冲突节点")
    public Result<List<String>> getConflictNodes(
            @Parameter(description = "路径1") @RequestParam String path1,
            @Parameter(description = "路径2") @RequestParam String path2) {
        List<String> list1 = pathPlanningService.decodePath(path1);
        List<String> list2 = pathPlanningService.decodePath(path2);
        List<String> conflicts = pathPlanningService.getConflictNodes(list1, list2);
        return Result.success(conflicts);
    }
}

package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.dto.PathPlanningResult;
import com.agv.dispatch.common.dto.Result;
import com.agv.dispatch.core.service.ConflictDetectionService;
import com.agv.dispatch.core.service.PathPlanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路径规划控制器
 * 负责路径规划、节点锁管理、路口锁管理、地图拓扑管理
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@RestController
@RequestMapping("/api/v1/path-planning")
@RequiredArgsConstructor
@Tag(name = "路径规划", description = "路径规划、节点锁/路口锁管理、地图拓扑管理接口")
public class PathPlanningController {

    private final PathPlanningService pathPlanningService;
    private final ConflictDetectionService conflictDetectionService;

    // ==================== 路径规划 ====================

    /**
     * 路径规划
     * 使用指定算法规划从起点到终点的最优路径
     *
     * @param startPoint 起点节点编号
     * @param endPoint 终点节点编号
     * @param algorithm 算法类型：A* 或 DIJKSTRA，默认 A*
     * @return 路径规划结果
     */
    @GetMapping("/plan")
    @Operation(summary = "路径规划", description = "使用指定算法（A*或Dijkstra）规划从起点到终点的最优路径")
    public Result<PathPlanningResult> planPath(
            @Parameter(description = "起点节点编号") @RequestParam String startPoint,
            @Parameter(description = "终点节点编号") @RequestParam String endPoint,
            @Parameter(description = "算法类型: A* 或 DIJKSTRA, 默认 A*")
            @RequestParam(defaultValue = "A*") String algorithm) {
        PathPlanningResult result = pathPlanningService.planPathWithAlgorithm(startPoint, endPoint, algorithm);
        return Result.success(result);
    }

    /**
     * 绕行规划
     * 规划避开指定节点的路径，用于冲突解决时的重规划
     *
     * @param startPoint 起点节点编号
     * @param endPoint 终点节点编号
     * @param avoidNodes 需要避开的节点列表
     * @return 路径规划结果
     */
    @PostMapping("/plan-detour")
    @Operation(summary = "绕行规划", description = "规划避开指定节点的路径，用于冲突解决时的重规划")
    public Result<PathPlanningResult> planPathWithDetour(
            @Parameter(description = "起点节点编号") @RequestParam String startPoint,
            @Parameter(description = "终点节点编号") @RequestParam String endPoint,
            @Parameter(description = "需要避开的节点列表") @RequestBody Set<String> avoidNodes) {
        PathPlanningResult result = pathPlanningService.planPathWithDetour(startPoint, endPoint, avoidNodes);
        return Result.success(result);
    }

    /**
     * 计算路径长度
     *
     * @param path 路径节点列表
     * @return 路径总长度（米）
     */
    @GetMapping("/distance")
    @Operation(summary = "计算路径长度", description = "计算路径的总长度（米）")
    public Result<Double> calculatePathDistance(
            @Parameter(description = "路径节点列表") @RequestBody List<String> path) {
        double distance = pathPlanningService.calculatePathDistance(path);
        return Result.success(distance);
    }

    /**
     * 估算路径行驶时间
     * 考虑节点限速、路口惩罚等因素
     *
     * @param path 路径节点列表
     * @return 预计行驶时间（秒）
     */
    @GetMapping("/estimated-time")
    @Operation(summary = "估算路径时间", description = "估算路径的预计行驶时间（秒），考虑节点限速、路口惩罚等因素")
    public Result<Double> estimateTravelTime(
            @Parameter(description = "路径节点列表") @RequestBody List<String> path) {
        double time = pathPlanningService.estimateTravelTime(path);
        return Result.success(time);
    }

    /**
     * 获取两条路径的冲突节点
     *
     * @param path1 路径1的节点编码字符串，逗号分隔
     * @param path2 路径2的节点编码字符串，逗号分隔
     * @return 冲突节点列表
     */
    @GetMapping("/conflict-nodes")
    @Operation(summary = "获取冲突节点", description = "获取两条路径的冲突节点")
    public Result<List<String>> getConflictNodes(
            @Parameter(description = "路径1（节点编码，逗号分隔）") @RequestParam String path1,
            @Parameter(description = "路径2（节点编码，逗号分隔）") @RequestParam String path2) {
        List<String> list1 = pathPlanningService.decodePath(path1);
        List<String> list2 = pathPlanningService.decodePath(path2);
        List<String> conflicts = pathPlanningService.getConflictNodes(list1, list2);
        return Result.success(conflicts);
    }

    // ==================== 节点锁管理 ====================

    /**
     * 尝试获取节点锁
     * 使用 Redis 分布式锁实现节点预占，防止多个AGV同时占用同一节点
     *
     * @param nodeCode 节点编号
     * @param agvId AGV ID
     * @return 是否获取成功
     */
    @PostMapping("/node-lock/{nodeCode}")
    @Operation(summary = "获取节点锁", description = "尝试获取指定节点的分布式锁，防止多个AGV同时占用同一节点")
    public Result<Boolean> tryLockNode(
            @Parameter(description = "节点编号") @PathVariable String nodeCode,
            @Parameter(description = "AGV ID") @RequestParam String agvId) {
        boolean locked = pathPlanningService.tryLockNode(nodeCode, agvId);
        return Result.success(locked);
    }

    /**
     * 释放节点锁
     *
     * @param nodeCode 节点编号
     * @param agvId AGV ID
     */
    @DeleteMapping("/node-lock/{nodeCode}")
    @Operation(summary = "释放节点锁", description = "释放指定节点的分布式锁")
    public Result<Void> unlockNode(
            @Parameter(description = "节点编号") @PathVariable String nodeCode,
            @Parameter(description = "AGV ID") @RequestParam String agvId) {
        pathPlanningService.unlockNode(nodeCode, agvId);
        return Result.success();
    }

    /**
     * 获取节点锁的持有者
     *
     * @param nodeCode 节点编号
     * @return 持有锁的AGV ID
     */
    @GetMapping("/node-lock/{nodeCode}/holder")
    @Operation(summary = "获取节点锁持有者", description = "获取当前持有指定节点锁的AGV ID")
    public Result<String> getNodeLockHolder(
            @Parameter(description = "节点编号") @PathVariable String nodeCode) {
        String holder = pathPlanningService.getNodeLockHolder(nodeCode);
        return Result.success(holder);
    }

    // ==================== 路口锁管理 ====================

    /**
     * 尝试通过路口（路口会车协议）
     * 先到先得原则，获取锁的AGV优先通过
     *
     * @param intersectionCode 路口节点编号
     * @param agvId AGV ID
     * @return 是否允许通过
     */
    @PostMapping("/intersection-pass/{intersectionCode}")
    @Operation(summary = "尝试通过路口", description = "AGV尝试获取路口通行权（路口会车协议），先到先得")
    public Result<Boolean> tryIntersectionPass(
            @Parameter(description = "路口节点编号") @PathVariable String intersectionCode,
            @Parameter(description = "AGV ID") @RequestParam String agvId) {
        boolean allowed = conflictDetectionService.tryIntersectionPass(agvId, intersectionCode);
        return Result.success(allowed);
    }

    /**
     * 完成路口通行，释放路口锁
     *
     * @param intersectionCode 路口节点编号
     * @param agvId AGV ID
     */
    @PostMapping("/intersection-pass/{intersectionCode}/complete")
    @Operation(summary = "完成路口通行", description = "AGV通过路口后释放路口锁")
    public Result<Void> completeIntersectionPass(
            @Parameter(description = "路口节点编号") @PathVariable String intersectionCode,
            @Parameter(description = "AGV ID") @RequestParam String agvId) {
        conflictDetectionService.completeIntersectionPass(agvId, intersectionCode);
        return Result.success();
    }

    /**
     * 获取所有被占用的路径节点
     *
     * @return 占用节点映射（节点编号 -> 占用AGV ID）
     */
    @GetMapping("/occupied")
    @Operation(summary = "获取被占用的路径节点", description = "获取所有被AGV占用的路径节点")
    public Result<Map<String, String>> getOccupiedPaths() {
        Map<String, String> occupied = pathPlanningService.getOccupiedPaths();
        return Result.success(occupied);
    }

    /**
     * 获取所有被阻塞的路径节点
     *
     * @return 阻塞节点映射（节点编号 -> 阻塞原因）
     */
    @GetMapping("/blocked")
    @Operation(summary = "获取被阻塞的路径节点", description = "获取所有被标记为阻塞的节点")
    public Result<Map<String, String>> getBlockedPaths() {
        Map<String, String> blocked = pathPlanningService.getBlockedPaths();
        return Result.success(blocked);
    }

    /**
     * 获取所有被锁定的路口
     *
     * @return 锁定路口映射（路口编号 -> 锁定AGV ID）
     */
    @GetMapping("/locked-intersections")
    @Operation(summary = "获取被锁定的路口", description = "获取所有被AGV锁定的路口")
    public Result<Map<String, String>> getLockedIntersections() {
        Map<String, String> locked = pathPlanningService.getLockedIntersections();
        return Result.success(locked);
    }

    // ==================== 地图拓扑管理 ====================

    /**
     * 初始化地图拓扑图
     * 从数据库加载所有节点和连接关系，构建拓扑图
     */
    @PostMapping("/graph/init")
    @Operation(summary = "初始化地图拓扑图", description = "从数据库加载所有节点和连接关系，构建拓扑图")
    public Result<Void> initGraph() {
        pathPlanningService.initGraph();
        return Result.success();
    }

    /**
     * 刷新节点缓存
     * 重新从数据库加载节点信息到缓存
     */
    @PostMapping("/graph/refresh")
    @Operation(summary = "刷新节点缓存", description = "重新从数据库加载节点信息到缓存")
    public Result<Void> refreshNodeCache() {
        pathPlanningService.refreshNodeCache();
        return Result.success();
    }
}

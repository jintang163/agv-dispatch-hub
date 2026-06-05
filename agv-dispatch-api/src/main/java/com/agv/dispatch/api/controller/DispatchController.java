package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.dto.Result;
import com.agv.dispatch.common.entity.ConflictRecord;
import com.agv.dispatch.core.service.ConflictDetectionService;
import com.agv.dispatch.core.service.PathPlanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
@Tag(name = "调度控制", description = "冲突检测、路径规划、调度控制接口")
public class DispatchController {

    private final ConflictDetectionService conflictDetectionService;
    private final PathPlanningService pathPlanningService;

    @GetMapping("/conflicts")
    @Operation(summary = "检测路径冲突")
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
    @Operation(summary = "解决冲突")
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

    @GetMapping("/path/plan")
    @Operation(summary = "路径规划")
    public Result<List<String>> planPath(
            @RequestParam String startPoint,
            @RequestParam String endPoint) {
        List<String> path = pathPlanningService.planPath(startPoint, endPoint);
        return Result.success(path);
    }

    @GetMapping("/path/occupied")
    @Operation(summary = "获取被占用的路径节点")
    public Result<Map<String, String>> getOccupiedPaths() {
        Map<String, String> occupied = pathPlanningService.getOccupiedPaths();
        return Result.success(occupied);
    }

    @PostMapping("/path/init")
    @Operation(summary = "初始化地图拓扑图")
    public Result<Void> initGraph() {
        pathPlanningService.initGraph();
        return Result.success();
    }
}

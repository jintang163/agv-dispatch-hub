package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.context.UserContext;
import com.agv.dispatch.common.dto.AgvRemoteControlDTO;
import com.agv.dispatch.common.dto.PathPlanningResult;
import com.agv.dispatch.common.dto.Result;
import com.agv.dispatch.common.entity.AlarmRecord;
import com.agv.dispatch.common.entity.ConflictRecord;
import com.agv.dispatch.common.entity.DeadlockRecord;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.core.service.AuthService;
import com.agv.dispatch.core.service.ConflictDetectionService;
import com.agv.dispatch.core.service.DeadlockDetectionService;
import com.agv.dispatch.core.service.OperationLogService;
import com.agv.dispatch.core.service.PathPlanningService;
import com.agv.dispatch.core.service.TaskDispatchService;
import com.agv.dispatch.common.enums.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final AuthService authService;
    private final OperationLogService operationLogService;

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
        if (!authService.hasPermission("agv:view")) {
            return Result.fail(403, "没有查看调度信息的权限");
        }
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
        if (!authService.hasPermission("agv:view")) {
            return Result.fail(403, "没有查看调度信息的权限");
        }
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
        if (!authService.hasPermission("agv:control")) {
            return Result.fail(403, "没有控制调度的权限");
        }
        long startTime = System.currentTimeMillis();
        String resolution = conflictDetectionService.resolveConflict(conflictId);
        String detail = String.format("冲突解决：冲突ID %d，解决方式：%s", conflictId, resolution);
        operationLogService.logOperation(OperationType.CONFLICT_RESOLVE,
                null, null, null, null, detail, true, System.currentTimeMillis() - startTime);
        return Result.success(resolution);
    }

    /**
     * 解决所有冲突
     * 批量处理所有未解决的冲突
     */
    @PostMapping("/conflicts/resolve-all")
    @Operation(summary = "解决所有冲突", description = "批量处理所有未解决的冲突")
    public Result<Void> resolveAllConflicts() {
        if (!authService.hasPermission("agv:control")) {
            return Result.fail(403, "没有控制调度的权限");
        }
        long startTime = System.currentTimeMillis();
        conflictDetectionService.resolveAllConflicts();
        operationLogService.logOperation(OperationType.CONFLICT_RESOLVE,
                null, null, null, null, "批量解决所有冲突", true, System.currentTimeMillis() - startTime);
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
        if (!authService.hasPermission("agv:view")) {
            return Result.fail(403, "没有查看调度信息的权限");
        }
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
        if (!authService.hasPermission("agv:view")) {
            return Result.fail(403, "没有查看调度信息的权限");
        }
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
        if (!authService.hasPermission("agv:control")) {
            return Result.fail(403, "没有控制调度的权限");
        }
        long startTime = System.currentTimeMillis();
        String result = taskDispatchService.resolveDeadlockManually(deadlockId);
        String detail = String.format("死锁解决：死锁ID %d，解决方式：%s", deadlockId, result);
        operationLogService.logOperation(OperationType.DEADLOCK_RESOLVE,
                null, null, null, null, detail, true, System.currentTimeMillis() - startTime);
        return Result.success(result);
    }

    /**
     * 强制解决所有死锁
     * 批量处理所有未解决的死锁
     */
    @PostMapping("/deadlocks/resolve-all")
    @Operation(summary = "强制解决所有死锁", description = "批量处理所有未解决的死锁")
    public Result<Void> resolveAllDeadlocks() {
        if (!authService.hasPermission("agv:control")) {
            return Result.fail(403, "没有控制调度的权限");
        }
        long startTime = System.currentTimeMillis();
        taskDispatchService.forceResolveAllDeadlocks();
        operationLogService.logOperation(OperationType.DEADLOCK_RESOLVE,
                null, null, null, null, "批量解决所有死锁", true, System.currentTimeMillis() - startTime);
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
     * @return 重规划结果
     */
    @PostMapping("/tasks/{taskId}/dynamic-replan")
    @Operation(summary = "动态重规划任务", description = "当路径被阻塞或任务变更时，从当前位置重新规划路径")
    public Result<PathPlanningResult> dynamicReplanTask(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "阻塞的节点编号") @RequestParam(required = false) String blockedNode,
            @Parameter(description = "阻塞原因") @RequestParam(required = false) String reason) {
        if (!authService.hasPermission("task:dispatch")) {
            return Result.fail(403, "没有调度任务的权限");
        }
        long startTime = System.currentTimeMillis();
        String operator = UserContext.getUsername();
        PathPlanningResult result = taskDispatchService.dynamicReplanTask(taskId, blockedNode, reason, operator);
        String detail = String.format("路径重规划：任务 %s，阻塞节点：%s，原因：%s",
                taskId, blockedNode != null ? blockedNode : "无", reason != null ? reason : "手动重规划");
        operationLogService.logOperation(OperationType.PATH_REPLAN,
                String.valueOf(taskId), null, null, null, detail, true, System.currentTimeMillis() - startTime);
        return Result.success(result);
    }

    /**
     * 从当前位置重规划任务路径
     * 不考虑阻塞，直接从任务当前执行位置重新规划最优路径
     *
     * @param taskId 任务ID
     * @return 重规划结果
     */
    @PostMapping("/tasks/{taskId}/replan")
    @Operation(summary = "从当前位置重规划", description = "从任务当前执行位置重新规划最优路径")
    public Result<PathPlanningResult> replanTaskFromCurrent(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        if (!authService.hasPermission("task:dispatch")) {
            return Result.fail(403, "没有调度任务的权限");
        }
        long startTime = System.currentTimeMillis();
        String operator = UserContext.getUsername();
        PathPlanningResult result = taskDispatchService.replanTaskFromCurrent(taskId, operator);
        String detail = String.format("路径重规划：任务 %s 从当前位置重新规划", taskId);
        operationLogService.logOperation(OperationType.PATH_REPLAN,
                String.valueOf(taskId), null, null, null, detail, true, System.currentTimeMillis() - startTime);
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
        if (!authService.hasPermission("agv:view")) {
            return Result.fail(403, "没有查看调度信息的权限");
        }
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
        if (!authService.hasPermission("agv:view")) {
            return Result.fail(403, "没有查看调度信息的权限");
        }
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
        if (!authService.hasPermission("agv:control")) {
            return Result.fail(403, "没有控制调度的权限");
        }
        long startTime = System.currentTimeMillis();
        pathPlanningService.markPathBlocked(nodeCode, reason);
        String detail = String.format("标记路径阻塞：节点 %s，原因：%s", nodeCode, reason);
        operationLogService.logOperation(OperationType.PATH_BLOCK_MARK,
                null, null, null, null, detail, true, System.currentTimeMillis() - startTime);
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
        if (!authService.hasPermission("agv:control")) {
            return Result.fail(403, "没有控制调度的权限");
        }
        long startTime = System.currentTimeMillis();
        taskDispatchService.clearPathBlocked(nodeCode);
        String detail = String.format("清除路径阻塞：节点 %s", nodeCode);
        operationLogService.logOperation(OperationType.PATH_BLOCK_CLEAR,
                null, null, null, null, detail, true, System.currentTimeMillis() - startTime);
        return Result.success();
    }

    // ==================== 任务执行控制 ====================

    /**
     * 下发任务给AGV
     * 将任务信息和路径点序列通过MQTT下发给指定AGV
     *
     * @param taskId 任务ID
     * @return 下发是否成功
     */
    @PostMapping("/tasks/{taskId}/dispatch")
    @Operation(summary = "下发任务", description = "将任务信息和路径点序列通过MQTT下发给AGV")
    public Result<Boolean> dispatchTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        if (!authService.hasPermission("task:dispatch")) {
            return Result.fail(403, "没有调度任务的权限");
        }
        long startTime = System.currentTimeMillis();
        boolean success = taskDispatchService.dispatchTask(taskId);
        String detail = String.format("任务下发：任务ID %s", taskId);
        operationLogService.logOperation(OperationType.TASK_DISPATCH,
                String.valueOf(taskId), null, null, null, detail, success, System.currentTimeMillis() - startTime);
        return Result.success(success);
    }

    /**
     * 暂停任务
     * 发送暂停命令给AGV，更新任务状态
     *
     * @param taskId 任务ID
     * @param reason 暂停原因
     * @return 操作是否成功
     */
    @PostMapping("/tasks/{taskId}/pause")
    @Operation(summary = "暂停任务", description = "发送暂停命令给AGV，暂停任务执行")
    public Result<Boolean> pauseTask(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "暂停原因") @RequestParam(required = false) String reason) {
        if (!authService.hasPermission("task:control")) {
            return Result.fail(403, "没有控制任务的权限");
        }
        long startTime = System.currentTimeMillis();
        String operator = UserContext.getUsername();
        boolean success = taskDispatchService.pauseTask(taskId, operator, reason);
        String detail = String.format("任务暂停：任务ID %s，原因：%s", taskId, reason != null ? reason : "无");
        operationLogService.logOperation(OperationType.TASK_PAUSE,
                String.valueOf(taskId), null, null, null, detail, success, System.currentTimeMillis() - startTime);
        return Result.success(success);
    }

    /**
     * 恢复任务
     * 发送恢复命令给AGV，继续任务执行
     *
     * @param taskId 任务ID
     * @return 操作是否成功
     */
    @PostMapping("/tasks/{taskId}/resume")
    @Operation(summary = "恢复任务", description = "发送恢复命令给AGV，继续任务执行")
    public Result<Boolean> resumeTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        if (!authService.hasPermission("task:control")) {
            return Result.fail(403, "没有控制任务的权限");
        }
        long startTime = System.currentTimeMillis();
        String operator = UserContext.getUsername();
        boolean success = taskDispatchService.resumeTask(taskId, operator);
        String detail = String.format("任务恢复：任务ID %s", taskId);
        operationLogService.logOperation(OperationType.TASK_RESUME,
                String.valueOf(taskId), null, null, null, detail, success, System.currentTimeMillis() - startTime);
        return Result.success(success);
    }

    /**
     * 取消任务
     * 发送取消命令给AGV，取消任务执行，释放资源
     *
     * @param taskId 任务ID
     * @param reason 取消原因
     * @return 操作是否成功
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "取消任务", description = "发送取消命令给AGV，取消任务执行，释放资源")
    public Result<Boolean> cancelTask(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "取消原因") @RequestParam(required = false) String reason) {
        if (!authService.hasPermission("task:cancel")) {
            return Result.fail(403, "没有取消任务的权限");
        }
        long startTime = System.currentTimeMillis();
        String operator = UserContext.getUsername();
        boolean success = taskDispatchService.cancelTask(taskId, operator, reason);
        String detail = String.format("任务取消：任务ID %s，原因：%s", taskId, reason != null ? reason : "无");
        operationLogService.logOperation(OperationType.TASK_CANCEL,
                String.valueOf(taskId), null, null, null, detail, success, System.currentTimeMillis() - startTime);
        return Result.success(success);
    }

    /**
     * 获取正在执行的任务列表
     *
     * @return 正在执行的任务列表
     */
    @GetMapping("/tasks/executing")
    @Operation(summary = "获取执行中的任务", description = "获取所有正在执行的任务列表")
    public Result<List<Task>> getExecutingTasks() {
        if (!authService.hasPermission("task:view")) {
            return Result.fail(403, "没有查看任务的权限");
        }
        List<Task> tasks = taskDispatchService.getExecutingTasks();
        return Result.success(tasks);
    }

    /**
     * 根据AGV编号获取当前任务
     *
     * @param agvNo AGV编号
     * @return 当前任务
     */
    @GetMapping("/agvs/{agvNo}/current-task")
    @Operation(summary = "获取AGV当前任务", description = "根据AGV编号获取当前正在执行的任务")
    public Result<Task> getCurrentTaskByAgvNo(
            @Parameter(description = "AGV编号") @PathVariable String agvNo) {
        if (!authService.hasPermission("task:view")) {
            return Result.fail(403, "没有查看任务的权限");
        }
        Task task = taskDispatchService.getCurrentTaskByAgvNo(agvNo);
        return Result.success(task);
    }

    // ==================== AGV远程控制 ====================

    /**
     * 远程控制AGV
     * 发送控制命令给指定AGV（暂停、恢复、急停、慢速、正常行驶、避障、充电、到指定点）
     *
     * @param controlDTO 控制参数
     * @return 操作是否成功
     */
    @PostMapping("/agvs/control")
    @Operation(summary = "远程控制AGV", description = "发送控制命令给指定AGV（暂停、恢复、急停、慢速、正常行驶、避障、充电、到指定点）")
    public Result<Boolean> remoteControlAgv(
            @Valid @RequestBody AgvRemoteControlDTO controlDTO) {
        if (!authService.hasPermission("agv:control")) {
            return Result.fail(403, "没有控制AGV的权限");
        }
        long startTime = System.currentTimeMillis();
        boolean success = taskDispatchService.remoteControlAgv(controlDTO);
        String detail = String.format("AGV远程控制：AGV %s，指令：%s",
                controlDTO.getAgvId(), controlDTO.getCommand());
        operationLogService.logOperation(OperationType.AGV_REMOTE_CONTROL,
                null, null, controlDTO.getAgvId(), null, detail, success, System.currentTimeMillis() - startTime);
        return Result.success(success);
    }

    // ==================== 告警管理 ====================

    /**
     * 获取未处理的告警列表
     *
     * @return 未处理告警列表
     */
    @GetMapping("/alarms/unhandled")
    @Operation(summary = "获取未处理告警", description = "获取所有未处理的告警列表，按时间倒序排列")
    public Result<List<AlarmRecord>> getUnhandledAlarms() {
        if (!authService.hasPermission("agv:view")) {
            return Result.fail(403, "没有查看告警的权限");
        }
        List<AlarmRecord> alarms = taskDispatchService.getUnhandledAlarms();
        return Result.success(alarms);
    }

    /**
     * 获取所有告警列表
     *
     * @return 告警列表
     */
    @GetMapping("/alarms")
    @Operation(summary = "获取所有告警", description = "获取所有告警记录列表")
    public Result<List<AlarmRecord>> getAllAlarms() {
        if (!authService.hasPermission("agv:view")) {
            return Result.fail(403, "没有查看告警的权限");
        }
        List<AlarmRecord> alarms = taskDispatchService.getAllAlarms();
        return Result.success(alarms);
    }

    /**
     * 处理告警
     * 标记告警为已处理，并记录处理结果
     *
     * @param alarmId 告警ID
     * @param handleResult 处理结果
     * @return 处理是否成功
     */
    @PostMapping("/alarms/{alarmId}/handle")
    @Operation(summary = "处理告警", description = "标记告警为已处理，并记录处理结果")
    public Result<Boolean> handleAlarm(
            @Parameter(description = "告警ID") @PathVariable Long alarmId,
            @Parameter(description = "处理结果") @RequestParam String handleResult) {
        if (!authService.hasPermission("agv:control")) {
            return Result.fail(403, "没有处理告警的权限");
        }
        long startTime = System.currentTimeMillis();
        String operator = UserContext.getUsername();
        boolean success = taskDispatchService.handleAlarm(alarmId, handleResult, operator);
        String detail = String.format("告警处理：告警ID %d，结果：%s", alarmId, handleResult);
        operationLogService.logOperation(OperationType.ALARM_HANDLE,
                null, null, null, null, detail, success, System.currentTimeMillis() - startTime);
        return Result.success(success);
    }
}

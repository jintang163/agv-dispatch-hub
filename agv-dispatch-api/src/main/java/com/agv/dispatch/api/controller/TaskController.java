package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.context.UserContext;
import com.agv.dispatch.common.dto.*;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.entity.TaskLog;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.service.AuthService;
import com.agv.dispatch.core.service.TaskDispatchService;
import com.agv.dispatch.mqtt.service.MqttMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "任务管理", description = "任务创建、分配、取消、查询等接口")
public class TaskController {

    private final TaskDispatchService taskDispatchService;
    private final MqttMessageService mqttMessageService;
    private final AgvRepository agvRepository;
    private final AuthService authService;

    @PostMapping
    @Operation(summary = "创建任务", description = "接收WMS下发的搬运、拣选等任务")
    public Result<Task> createTask(@Valid @RequestBody TaskCreateDTO dto) {
        if (!authService.hasPermission("task:create")) {
            return Result.fail(403, "没有创建任务的权限");
        }
        Task task = taskDispatchService.createTask(dto);
        return Result.success(task);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询任务详情")
    public Result<Task> getTaskById(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        if (!authService.hasPermission("task:view")) {
            return Result.fail(403, "没有查看任务的权限");
        }
        Task task = taskDispatchService.getTaskById(taskId);
        return Result.success(task);
    }

    @PostMapping("/query")
    @Operation(summary = "分页查询任务列表")
    public Result<Page<Task>> queryTasks(@RequestBody TaskQueryDTO dto) {
        if (!authService.hasPermission("task:view")) {
            return Result.fail(403, "没有查看任务的权限");
        }
        Page<Task> page = taskDispatchService.queryTasks(dto);
        return Result.success(page);
    }

    @PostMapping("/assign")
    @Operation(summary = "手动分配任务", description = "将任务分配给指定AGV")
    public Result<Task> assignTask(@Valid @RequestBody TaskAssignDTO dto) {
        if (!authService.hasPermission("task:dispatch")) {
            return Result.fail(403, "没有分配任务的权限");
        }
        String operator = UserContext.getUsername();
        Task task = taskDispatchService.assignTask(
                dto.getTaskId(), dto.getAgvId(), dto.getPath(), operator);

        agvRepository.findById(dto.getAgvId()).ifPresent(agv -> {
            mqttMessageService.sendTaskAssign(agv.getAgvNo(), task);
        });

        return Result.success(task);
    }

    @PostMapping("/{taskId}/auto-assign")
    @Operation(summary = "自动分配任务", description = "系统自动选择最优AGV分配任务")
    public Result<Task> autoAssignTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        if (!authService.hasPermission("task:dispatch")) {
            return Result.fail(403, "没有分配任务的权限");
        }
        String operator = UserContext.getUsername();
        Task task = taskDispatchService.autoAssignTask(taskId, operator);

        agvRepository.findById(task.getAgvId()).ifPresent(agv -> {
            mqttMessageService.sendTaskAssign(agv.getAgvNo(), task);
        });

        return Result.success(task);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消任务")
    public Result<Void> cancelTask(@Valid @RequestBody TaskCancelDTO dto) {
        if (!authService.hasPermission("task:cancel")) {
            return Result.fail(403, "没有取消任务的权限");
        }
        String operator = UserContext.getUsername();
        Task task = taskDispatchService.getTaskById(dto.getTaskId());
        String agvId = task.getAgvId();

        taskDispatchService.cancelTask(dto.getTaskId(), dto.getReason(), operator);

        if (agvId != null) {
            agvRepository.findById(agvId).ifPresent(agv -> {
                mqttMessageService.sendTaskCancel(agv.getAgvNo(), dto.getTaskId(), dto.getReason());
            });
        }

        return Result.success();
    }

    @PostMapping("/reassign")
    @Operation(summary = "任务重分配", description = "AGV故障或路径阻塞时强制重分配")
    public Result<Task> reassignTask(@Valid @RequestBody TaskReassignDTO dto) {
        if (!authService.hasPermission("task:dispatch")) {
            return Result.fail(403, "没有分配任务的权限");
        }
        String operator = UserContext.getUsername();
        Task task = taskDispatchService.reassignTask(
                dto.getTaskId(), dto.getTargetAgvId(), dto.getReason(), operator);

        if (task.getAgvId() != null) {
            agvRepository.findById(task.getAgvId()).ifPresent(agv -> {
                mqttMessageService.sendTaskAssign(agv.getAgvNo(), task);
            });
        }

        return Result.success(task);
    }

    @PostMapping("/priority")
    @Operation(summary = "更新任务优先级", description = "支持高优先级任务插队")
    public Result<Void> updatePriority(@Valid @RequestBody TaskPriorityUpdateDTO dto) {
        if (!authService.hasPermission("task:update")) {
            return Result.fail(403, "没有更新任务的权限");
        }
        String operator = UserContext.getUsername();
        taskDispatchService.updateTaskPriority(
                dto.getTaskId(), dto.getPriority(), operator);
        return Result.success();
    }

    @GetMapping("/queue")
    @Operation(summary = "获取待分配任务队列", description = "按优先级和截止时间排序的队列")
    public Result<List<Task>> getPendingQueue() {
        if (!authService.hasPermission("task:view")) {
            return Result.fail(403, "没有查看任务的权限");
        }
        List<Task> queue = taskDispatchService.getPendingQueue();
        return Result.success(queue);
    }

    @GetMapping("/queue/size")
    @Operation(summary = "获取队列大小")
    public Result<Long> getQueueSize() {
        if (!authService.hasPermission("task:view")) {
            return Result.fail(403, "没有查看任务的权限");
        }
        return Result.success(taskDispatchService.getQueueSize());
    }

    @PostMapping("/queue/refresh")
    @Operation(summary = "刷新任务队列")
    public Result<Void> refreshQueue() {
        if (!authService.hasPermission("task:update")) {
            return Result.fail(403, "没有更新任务的权限");
        }
        taskDispatchService.refreshQueue();
        return Result.success();
    }

    @GetMapping("/{taskId}/logs")
    @Operation(summary = "获取任务操作日志")
    public Result<List<TaskLog>> getTaskLogs(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        if (!authService.hasPermission("task:view")) {
            return Result.fail(403, "没有查看任务的权限");
        }
        List<TaskLog> logs = taskDispatchService.getTaskLogs(taskId);
        return Result.success(logs);
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取任务统计信息")
    public Result<TaskStatisticsVO> getStatistics() {
        if (!authService.hasPermission("task:view")) {
            return Result.fail(403, "没有查看任务的权限");
        }
        TaskStatisticsVO vo = taskDispatchService.getStatistics();
        return Result.success(vo);
    }
}

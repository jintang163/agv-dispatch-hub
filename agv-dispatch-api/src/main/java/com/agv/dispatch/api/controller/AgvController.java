package com.agv.dispatch.api.controller;

import com.agv.dispatch.common.dto.Result;
import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.enums.AgvStatus;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.repository.TaskRepository;
import com.agv.dispatch.core.service.TaskDispatchService;
import com.agv.dispatch.mqtt.service.MqttMessageService;
import com.alibaba.fastjson2.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.agv.dispatch.common.constant.RedisKeyConstant.AGV_STATUS_PREFIX;

@RestController
@RequestMapping("/api/v1/agvs")
@RequiredArgsConstructor
@Tag(name = "AGV管理", description = "AGV查询、控制接口")
public class AgvController {

    private final AgvRepository agvRepository;
    private final TaskRepository taskRepository;
    private final TaskDispatchService taskDispatchService;
    private final MqttMessageService mqttMessageService;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    @Operation(summary = "查询AGV列表")
    public Result<List<Agv>> getAgvList(
            @Parameter(description = "AGV状态") @RequestParam(required = false) AgvStatus status) {
        List<Agv> agvs;
        if (status != null) {
            agvs = agvRepository.findByStatus(status);
        } else {
            agvs = agvRepository.findAll();
        }
        return Result.success(agvs);
    }

    @GetMapping("/{agvId}")
    @Operation(summary = "查询AGV详情")
    public Result<Agv> getAgvById(@PathVariable String agvId) {
        String cacheKey = AGV_STATUS_PREFIX + agvId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            try {
                return Result.success(JSON.parseObject(cached, Agv.class));
            } catch (Exception e) {
                // ignore
            }
        }

        return agvRepository.findById(agvId)
                .map(Result::success)
                .orElse(Result.fail("AGV不存在"));
    }

    @GetMapping("/{agvId}/current-task")
    @Operation(summary = "获取AGV当前任务")
    public Result<Task> getCurrentTask(@PathVariable String agvId) {
        return agvRepository.findById(agvId)
                .map(agv -> {
                    if (agv.getCurrentTaskId() != null) {
                        return taskRepository.findById(agv.getCurrentTaskId())
                                .map(Result::success)
                                .orElse(Result.success(null));
                    }
                    return Result.success(null);
                })
                .orElse(Result.fail("AGV不存在"));
    }

    @GetMapping("/available")
    @Operation(summary = "获取可用AGV列表")
    public Result<List<Agv>> getAvailableAgvs() {
        List<Agv> agvs = agvRepository.findAvailableAgvsOrdered();
        return Result.success(agvs);
    }

    @PostMapping("/{agvId}/pause")
    @Operation(summary = "暂停AGV")
    public Result<Void> pauseAgv(
            @PathVariable String agvId,
            @Parameter(description = "操作员") @RequestParam(required = false) String operator) {
        taskDispatchService.pauseAgv(agvId, operator);
        agvRepository.findById(agvId).ifPresent(agv ->
                mqttMessageService.sendPause(agv.getAgvNo()));
        return Result.success();
    }

    @PostMapping("/{agvId}/resume")
    @Operation(summary = "恢复AGV")
    public Result<Void> resumeAgv(
            @PathVariable String agvId,
            @Parameter(description = "操作员") @RequestParam(required = false) String operator) {
        taskDispatchService.resumeAgv(agvId, operator);
        agvRepository.findById(agvId).ifPresent(agv ->
                mqttMessageService.sendResume(agv.getAgvNo()));
        return Result.success();
    }

    @PostMapping("/{agvId}/stop")
    @Operation(summary = "紧急停车")
    public Result<Void> emergencyStop(
            @PathVariable String agvId,
            @Parameter(description = "操作员") @RequestParam(required = false) String operator) {
        agvRepository.findById(agvId).ifPresent(agv -> {
            agv.setStatus(AgvStatus.PAUSED);
            agvRepository.save(agv);
            mqttMessageService.sendEmergencyStop(agv.getAgvNo());
        });
        return Result.success();
    }

    @PostMapping("/{agvId}/charge")
    @Operation(summary = "呼叫AGV去充电")
    public Result<Void> goToCharge(
            @PathVariable String agvId,
            @Parameter(description = "充电站编码") @RequestParam String chargingStation,
            @Parameter(description = "操作员") @RequestParam(required = false) String operator) {
        agvRepository.findById(agvId).ifPresent(agv -> {
            if (agv.getCurrentTaskId() != null) {
                Task task = taskRepository.findById(agv.getCurrentTaskId()).orElse(null);
                if (task != null && task.getStatus() != TaskStatus.COMPLETED
                        && task.getStatus() != TaskStatus.CANCELLED) {
                    taskDispatchService.reassignTask(agv.getCurrentTaskId(), null, "AGV需要充电", operator);
                }
            }
            agv.setStatus(AgvStatus.CHARGING);
            agvRepository.save(agv);
            mqttMessageService.sendGoToCharge(agv.getAgvNo(), chargingStation);
        });
        return Result.success();
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取AGV统计信息")
    public Result<Map<String, Long>> getAgvStatistics() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", agvRepository.count());
        stats.put("idle", agvRepository.countByStatus(AgvStatus.IDLE));
        stats.put("working", agvRepository.countByStatus(AgvStatus.WORKING));
        stats.put("charging", agvRepository.countByStatus(AgvStatus.CHARGING));
        stats.put("fault", agvRepository.countByStatus(AgvStatus.FAULT));
        stats.put("offline", agvRepository.countByStatus(AgvStatus.OFFLINE));
        stats.put("paused", agvRepository.countByStatus(AgvStatus.PAUSED));
        return Result.success(stats);
    }
}

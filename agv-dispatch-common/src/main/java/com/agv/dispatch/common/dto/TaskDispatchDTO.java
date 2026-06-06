package com.agv.dispatch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 任务下发DTO
 * 用于将任务信息和路径点序列下发给AGV
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Schema(description = "任务下发DTO")
public class TaskDispatchDTO {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID", example = "T202606060001")
    private String taskId;

    @NotBlank(message = "任务编号不能为空")
    @Schema(description = "任务编号", example = "T202606060001")
    private String taskNo;

    @Schema(description = "任务类型", example = "TRANSPORT")
    private String taskType;

    @NotBlank(message = "AGV编号不能为空")
    @Schema(description = "AGV编号", example = "AGV001")
    private String agvNo;

    @NotBlank(message = "起点不能为空")
    @Schema(description = "起点节点编号", example = "N001")
    private String startPoint;

    @NotBlank(message = "终点不能为空")
    @Schema(description = "终点节点编号", example = "N010")
    private String endPoint;

    @NotEmpty(message = "路径点序列不能为空")
    @Schema(description = "路径点序列（按顺序排列的节点编号列表）",
            example = "[\"N001\", \"N002\", \"N003\", \"N010\"]")
    private List<String> pathPoints;

    @Schema(description = "预计行驶时间（秒）", example = "120")
    private Double estimatedTime;

    @Schema(description = "路径总长度（米）", example = "50.5")
    private Double totalDistance;

    @Schema(description = "货物信息", example = "货物A，重量50kg")
    private String cargoInfo;

    @Schema(description = "载重（kg）", example = "50.0")
    private Double loadWeight;

    @Schema(description = "截止时间", example = "2026-06-06T12:00:00")
    private String deadline;

    @Schema(description = "下发时间", example = "2026-06-06T10:00:00")
    private String dispatchTime;

    @Schema(description = "优先级", example = "HIGH")
    private String priority;
}

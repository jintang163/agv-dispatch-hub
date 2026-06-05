package com.agv.dispatch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "任务重分配DTO")
public class TaskReassignDTO {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "目标AGV编号，不填则自动分配")
    private String targetAgvId;

    @Schema(description = "重分配原因")
    private String reason;

    @Schema(description = "操作员")
    private String operator;
}

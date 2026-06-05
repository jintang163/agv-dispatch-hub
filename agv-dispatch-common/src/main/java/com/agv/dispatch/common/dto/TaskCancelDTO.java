package com.agv.dispatch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "任务取消DTO")
public class TaskCancelDTO {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "取消原因")
    private String reason;

    @Schema(description = "操作员")
    private String operator;
}

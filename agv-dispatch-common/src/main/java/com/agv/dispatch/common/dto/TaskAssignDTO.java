package com.agv.dispatch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "任务分配DTO")
public class TaskAssignDTO {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private String taskId;

    @NotBlank(message = "AGV编号不能为空")
    @Schema(description = "AGV编号")
    private String agvId;

    @Schema(description = "路径规划结果")
    private String path;

    @Schema(description = "操作员")
    private String operator;
}

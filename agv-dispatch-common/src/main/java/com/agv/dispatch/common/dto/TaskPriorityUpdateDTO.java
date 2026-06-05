package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.TaskPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "任务优先级更新DTO")
public class TaskPriorityUpdateDTO {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private String taskId;

    @NotNull(message = "优先级不能为空")
    @Schema(description = "新优先级")
    private TaskPriority priority;

    @Schema(description = "操作员")
    private String operator;
}

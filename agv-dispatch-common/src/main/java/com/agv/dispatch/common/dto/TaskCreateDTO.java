package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.common.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "任务创建DTO")
public class TaskCreateDTO {

    @NotBlank(message = "起点不能为空")
    @Schema(description = "起点编码")
    private String startPoint;

    @NotBlank(message = "终点不能为空")
    @Schema(description = "终点编码")
    private String endPoint;

    @NotNull(message = "任务类型不能为空")
    @Schema(description = "任务类型")
    private TaskType taskType;

    @Schema(description = "任务优先级")
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Schema(description = "期望完成时间")
    private LocalDateTime deadline;

    @Schema(description = "负载重量(kg)")
    private Double loadWeight;

    @Schema(description = "货物信息")
    private String cargoInfo;

    @Schema(description = "WMS订单号")
    private String wmsOrderNo;

    @Schema(description = "备注")
    private String remark;
}

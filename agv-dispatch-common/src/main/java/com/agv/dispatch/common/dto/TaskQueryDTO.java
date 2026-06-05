package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.common.enums.TaskStatus;
import com.agv.dispatch.common.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "任务查询DTO")
public class TaskQueryDTO {

    @Schema(description = "任务编号")
    private String taskNo;

    @Schema(description = "WMS订单号")
    private String wmsOrderNo;

    @Schema(description = "任务状态")
    private TaskStatus status;

    @Schema(description = "任务类型")
    private TaskType taskType;

    @Schema(description = "任务优先级")
    private TaskPriority priority;

    @Schema(description = "AGV编号")
    private String agvId;

    @Schema(description = "创建时间开始")
    private LocalDateTime createTimeStart;

    @Schema(description = "创建时间结束")
    private LocalDateTime createTimeEnd;

    @Schema(description = "页码", defaultValue = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", defaultValue = "20")
    private Integer pageSize = 20;
}

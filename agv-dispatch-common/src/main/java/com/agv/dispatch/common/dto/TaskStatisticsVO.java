package com.agv.dispatch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务统计VO")
public class TaskStatisticsVO {

    @Schema(description = "待分配任务数")
    private Long pendingCount;

    @Schema(description = "已分配任务数")
    private Long assignedCount;

    @Schema(description = "执行中任务数")
    private Long executingCount;

    @Schema(description = "今日完成数")
    private Long todayCompletedCount;

    @Schema(description = "今日异常数")
    private Long todayAbnormalCount;

    @Schema(description = "高优先级待处理数")
    private Long highPriorityCount;

    @Schema(description = "平均完成时间(分钟)")
    private Double avgCompletionTime;
}

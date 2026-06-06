package com.agv.dispatch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AGV执行反馈DTO
 * 接收AGV上报的任务执行状态和进度
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Schema(description = "AGV执行反馈DTO")
public class TaskExecutionFeedbackDTO {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID", example = "T202606060001")
    private String taskId;

    @NotBlank(message = "AGV编号不能为空")
    @Schema(description = "AGV编号", example = "AGV001")
    private String agvNo;

    @NotBlank(message = "反馈动作不能为空")
    @Schema(description = "反馈动作: START-开始执行, PROGRESS-进度更新, ARRIVED-到达节点, " +
            "WORKING-作业中, COMPLETE-完成, ABNORMAL-异常, PAUSE-暂停, RESUME-恢复",
            example = "ARRIVED")
    private String action;

    @Schema(description = "当前执行步骤", example = "3")
    private Integer currentStep;

    @Schema(description = "当前所在节点编号", example = "N003")
    private String currentNode;

    @Schema(description = "到达的节点编号（当action=ARRIVED时）", example = "N003")
    private String arrivedNode;

    @Schema(description = "进度百分比", example = "30")
    private Integer progress;

    @Schema(description = "执行结果: SUCCESS-成功, FAILED-失败", example = "SUCCESS")
    private String result;

    @Schema(description = "错误码（失败时）", example = "E001")
    private String errorCode;

    @Schema(description = "错误信息（失败时）", example = "路径被阻塞")
    private String errorMessage;

    @Schema(description = "当前速度（m/s）", example = "1.0")
    private Double speed;

    @Schema(description = "当前电量（%）", example = "85")
    private Double batteryLevel;

    @Schema(description = "X坐标", example = "10.5")
    private Double xCoord;

    @Schema(description = "Y坐标", example = "20.3")
    private Double yCoord;

    @Schema(description = "航向角（度）", example = "90")
    private Double angle;

    @Schema(description = "反馈时间戳", example = "2026-06-06T10:05:30")
    private String timestamp;
}

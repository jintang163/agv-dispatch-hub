package com.agv.dispatch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AGV远程控制DTO
 * 用于手动干预AGV的控制命令
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Schema(description = "AGV远程控制DTO")
public class AgvRemoteControlDTO {

    @NotBlank(message = "AGV编号不能为空")
    @Schema(description = "AGV编号", example = "AGV001")
    private String agvNo;

    @NotNull(message = "控制命令不能为空")
    @Schema(description = "控制命令: PAUSE-暂停, RESUME-恢复, CANCEL-取消任务, " +
            "STOP-立即停车, SLOW_DOWN-慢速行驶, NORMAL_SPEED-正常行驶, " +
            "DETOUR-避障绕行, GO_CHARGE-返回充电站, GO_TO_POINT-到指定点",
            example = "PAUSE")
    private String command;

    @Schema(description = "目标节点编号（当command=GO_TO_POINT时）", example = "N010")
    private String targetPoint;

    @Schema(description = "速度参数（m/s）（当command=SLOW_DOWN或NORMAL_SPEED时）", example = "0.5")
    private Double speed;

    @Schema(description = "操作人", example = "admin")
    private String operator;

    @Schema(description = "控制原因", example = "路径阻塞，需要临时停车")
    private String reason;

    @Schema(description = "命令超时时间（秒）", example = "60")
    private Integer timeout;
}

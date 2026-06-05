package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.AgvStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgvStatusDTO {

    private String agvNo;

    private AgvStatus status;

    private String currentPosition;

    private Double batteryLevel;

    private Double xCoord;

    private Double yCoord;

    private Double angle;

    private Double speed;

    private String currentTaskId;

    private String faultCode;

    private String faultMessage;

    private LocalDateTime timestamp;
}

package com.agv.dispatch.common.dto;

import lombok.Data;

@Data
public class AgvStatusReportDTO {

    private String agvNo;

    private Double batteryLevel;

    private Double xCoord;

    private Double yCoord;

    private Double angle;

    private Double speed;

    private String currentPosition;

    private String status;

    private String faultCode;

    private String faultMessage;
}

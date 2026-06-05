package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.AgvStatus;
import com.agv.dispatch.common.enums.TaskType;
import lombok.Data;

import java.util.List;

@Data
public class AgvUpdateDTO {

    private String name;

    private String model;

    private Double maxLoad;

    private String ipAddress;

    private AgvStatus status;

    private Double batteryLevel;

    private Double xCoord;

    private Double yCoord;

    private Double angle;

    private Double speed;

    private String currentPosition;

    private List<TaskType> allowedTaskTypes;

    private String faultCode;

    private String faultMessage;
}

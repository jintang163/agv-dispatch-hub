package com.agv.dispatch.common.dto;

import com.agv.dispatch.common.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AgvRegisterDTO {

    @NotBlank(message = "AGV编号不能为空")
    private String agvNo;

    private String name;

    private String model;

    private Double maxLoad;

    private String ipAddress;

    private Double xCoord;

    private Double yCoord;

    private List<TaskType> allowedTaskTypes;
}

package com.agv.dispatch.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathPlanningResult {

    private boolean success;

    private List<String> path;

    private double totalDistance;

    private double estimatedTime;

    private String algorithm;

    private boolean hasDetour;

    private String originalPath;

    private String message;

    public static PathPlanningResult success(List<String> path, double totalDistance, String algorithm) {
        return PathPlanningResult.builder()
                .success(true)
                .path(path)
                .totalDistance(totalDistance)
                .algorithm(algorithm)
                .hasDetour(false)
                .build();
    }

    public static PathPlanningResult detour(List<String> path, double totalDistance, String originalPath, String algorithm) {
        return PathPlanningResult.builder()
                .success(true)
                .path(path)
                .totalDistance(totalDistance)
                .algorithm(algorithm)
                .hasDetour(true)
                .originalPath(originalPath)
                .build();
    }

    public static PathPlanningResult failure(String message) {
        return PathPlanningResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}

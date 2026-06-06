package com.agv.dispatch.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 路径规划结果DTO
 * 封装路径规划的返回结果，包括路径、距离、预计时间等信息
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathPlanningResult {

    /**
     * 规划是否成功
     */
    private boolean success;

    /**
     * 规划的路径节点列表（按顺序排列）
     */
    private List<String> path;

    /**
     * 路径总长度（米）
     */
    private double totalDistance;

    /**
     * 预计行驶时间（秒）
     */
    private double estimatedTime;

    /**
     * 使用的算法：A* 或 DIJKSTRA
     */
    private String algorithm;

    /**
     * 是否为绕行路径（避开了某些节点）
     */
    private boolean hasDetour;

    /**
     * 原始路径（绕行前的路径，JSON格式）
     */
    private String originalPath;

    /**
     * 错误消息（规划失败时）
     */
    private String message;

    /**
     * 创建成功的路径规划结果
     *
     * @param path 路径节点列表
     * @param totalDistance 总距离
     * @param algorithm 使用的算法
     * @return 路径规划结果
     */
    public static PathPlanningResult success(List<String> path, double totalDistance, String algorithm) {
        return PathPlanningResult.builder()
                .success(true)
                .path(path)
                .totalDistance(totalDistance)
                .algorithm(algorithm)
                .hasDetour(false)
                .build();
    }

    /**
     * 创建绕行的路径规划结果
     *
     * @param path 绕行后的路径节点列表
     * @param totalDistance 总距离
     * @param originalPath 原始路径
     * @param algorithm 使用的算法
     * @return 路径规划结果
     */
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

    /**
     * 创建失败的路径规划结果
     *
     * @param message 错误消息
     * @return 路径规划结果
     */
    public static PathPlanningResult failure(String message) {
        return PathPlanningResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}

package com.agv.dispatch.core.service;

import com.agv.dispatch.common.entity.MapNode;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.core.repository.MapNodeRepository;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.agv.dispatch.common.constant.RedisKeyConstant.PATH_OCCUPY_PREFIX;
import static com.agv.dispatch.common.constant.RedisKeyConstant.PATH_OCCUPY_SECONDS;

/**
 * 路径规划服务
 * 基于Dijkstra算法实现最短路径规划
 * 核心功能：
 * - 地图拓扑图初始化与维护
 * - 考虑路径占用的动态路径规划
 * - 路径占用与释放管理（前瞻3步）
 * - 路径编码与解码
 * - 路径可用性检测
 *
 * 路径占用机制：
 * - AGV执行任务时，占用当前位置及前瞻3步的节点
 * - 已走过的节点自动释放
 * - 其他AGV规划路径时会避开已占用节点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PathPlanningService {

    private final MapNodeRepository mapNodeRepository;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, Map<String, Double>> graph = new HashMap<>();

    /**
     * 初始化地图拓扑图
     * 从数据库加载所有地图节点及其连接关系，构建邻接表
     * 系统启动或地图变更时调用
     */
    public void initGraph() {
        List<MapNode> nodes = mapNodeRepository.findAll();
        graph.clear();
        for (MapNode node : nodes) {
            Map<String, Double> neighbors = new HashMap<>();
            if (node.getConnectedNodes() != null && !node.getConnectedNodes().isEmpty()) {
                String[] connected = node.getConnectedNodes().split(",");
                for (String neighborCode : connected) {
                    mapNodeRepository.findByNodeCode(neighborCode.trim()).ifPresent(neighbor -> {
                        double distance = calculateDistance(node, neighbor);
                        neighbors.put(neighborCode.trim(), distance);
                    });
                }
            }
            graph.put(node.getNodeCode(), neighbors);
        }
        log.info("地图拓扑图初始化完成，节点数: {}", graph.size());
    }

    /**
     * 规划从起点到终点的最优路径
     * 使用Dijkstra算法，考虑路径占用惩罚
     * 已被其他AGV占用的节点会增加代价，尽量避开冲突
     *
     * @param startPoint 起点节点编号
     * @param endPoint 终点节点编号
     * @return 路径节点列表，如果无法规划返回空列表
     */
    public List<String> planPath(String startPoint, String endPoint) {
        if (startPoint.equals(endPoint)) {
            return Collections.singletonList(startPoint);
        }
        if (graph.isEmpty()) {
            initGraph();
        }

        Map<String, Double> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Map.Entry<String, Double>> pq = new PriorityQueue<>(
                Map.Entry.comparingByValue());

        for (String node : graph.keySet()) {
            distances.put(node, Double.MAX_VALUE);
        }
        distances.put(startPoint, 0.0);
        pq.add(new AbstractMap.SimpleEntry<>(startPoint, 0.0));

        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            Map.Entry<String, Double> current = pq.poll();
            String currentNode = current.getKey();

            if (visited.contains(currentNode)) {
                continue;
            }
            visited.add(currentNode);

            if (currentNode.equals(endPoint)) {
                break;
            }

            Map<String, Double> neighbors = graph.getOrDefault(currentNode, Collections.emptyMap());
            for (Map.Entry<String, Double> neighbor : neighbors.entrySet()) {
                String nextNode = neighbor.getKey();
                if (visited.contains(nextNode)) {
                    continue;
                }

                double edgeWeight = neighbor.getValue();
                double occupationPenalty = getPathOccupationPenalty(nextNode);
                double newDist = distances.get(currentNode) + edgeWeight + occupationPenalty;

                if (newDist < distances.getOrDefault(nextNode, Double.MAX_VALUE)) {
                    distances.put(nextNode, newDist);
                    previous.put(nextNode, currentNode);
                    pq.add(new AbstractMap.SimpleEntry<>(nextNode, newDist));
                }
            }
        }

        return reconstructPath(previous, startPoint, endPoint);
    }

    /**
     * 获取节点的占用惩罚值
     * 已被占用的节点增加100的代价，让规划算法尽量避开
     *
     * @param nodeCode 节点编号
     * @return 占用惩罚值，已占用返回100，未占用返回0
     */
    private double getPathOccupationPenalty(String nodeCode) {
        String key = PATH_OCCUPY_PREFIX + nodeCode;
        Boolean occupied = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(occupied) ? 100.0 : 0.0;
    }

    private List<String> reconstructPath(Map<String, String> previous,
                                         String start, String end) {
        List<String> path = new ArrayList<>();
        String current = end;

        if (!previous.containsKey(end) && !start.equals(end)) {
            return Collections.emptyList();
        }

        while (current != null) {
            path.add(0, current);
            if (current.equals(start)) {
                break;
            }
            current = previous.get(current);
        }

        return path;
    }

    /**
     * 检查路径是否可用（无占用）
     * 路径上所有节点都未被占用时才返回true
     *
     * @param path 待检查的路径节点列表
     * @return true表示路径可用，false表示有节点被占用
     */
    public boolean isPathAvailable(List<String> path) {
        for (String node : path) {
            String key = PATH_OCCUPY_PREFIX + node;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 占用路径
     * 占用当前位置及前瞻3步的节点，防止其他AGV冲突
     * 占用信息存储在Redis中，设置过期时间防止死锁
     *
     * @param agvId AGV ID
     * @param path 完整路径节点列表
     * @param currentStep 当前执行到的步骤索引
     */
    public void occupyPath(String agvId, List<String> path, int currentStep) {
        if (path == null || currentStep >= path.size()) {
            return;
        }

        int lookAhead = Math.min(currentStep + 3, path.size());
        for (int i = currentStep; i < lookAhead; i++) {
            String node = path.get(i);
            String key = PATH_OCCUPY_PREFIX + node;
            redisTemplate.opsForValue().set(key, agvId, PATH_OCCUPY_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 释放已走过的路径
     * 释放当前步骤之前的所有节点，让其他AGV可以使用
     * 只释放当前AGV占用的节点
     *
     * @param agvId AGV ID
     * @param path 完整路径节点列表
     * @param currentStep 当前执行到的步骤索引
     */
    public void releasePath(String agvId, List<String> path, int currentStep) {
        if (path == null || currentStep <= 0) {
            return;
        }

        for (int i = 0; i < currentStep; i++) {
            String node = path.get(i);
            String key = PATH_OCCUPY_PREFIX + node;
            String occupant = redisTemplate.opsForValue().get(key);
            if (agvId.equals(occupant)) {
                redisTemplate.delete(key);
            }
        }
    }

    /**
     * 释放AGV占用的所有路径
     * 任务取消、AGV故障或任务重分配时调用
     * 遍历所有占用的路径节点，只释放当前AGV占用的
     *
     * @param agvId AGV ID
     */
    public void releaseAllPath(String agvId) {
        Set<String> keys = redisTemplate.keys(PATH_OCCUPY_PREFIX + "*");
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            String occupant = redisTemplate.opsForValue().get(key);
            if (agvId.equals(occupant)) {
                redisTemplate.delete(key);
            }
        }
    }

    private double calculateDistance(MapNode node1, MapNode node2) {
        double dx = node1.getXCoord() - node2.getXCoord();
        double dy = node1.getYCoord() - node2.getYCoord();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 将路径节点列表编码为JSON字符串
     * 用于存储到数据库
     *
     * @param path 路径节点列表
     * @return JSON格式的路径字符串
     */
    public String encodePath(List<String> path) {
        return JSON.toJSONString(path);
    }

    /**
     * 将JSON格式的路径字符串解码为节点列表
     *
     * @param pathJson JSON格式的路径字符串
     * @return 路径节点列表
     */
    @SuppressWarnings("unchecked")
    public List<String> decodePath(String pathJson) {
        if (pathJson == null || pathJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return JSON.parseObject(pathJson, List.class);
        } catch (Exception e) {
            return Collections.singletonList(pathJson);
        }
    }

    /**
     * 获取当前所有被占用的路径节点
     * 用于页面展示和调度控制
     *
     * @return 节点编号 -> 占用AGV ID 的映射
     */
    public Map<String, String> getOccupiedPaths() {
        Set<String> keys = redisTemplate.keys(PATH_OCCUPY_PREFIX + "*");
        Map<String, String> occupied = new HashMap<>();
        if (keys == null) {
            return occupied;
        }
        for (String key : keys) {
            String node = key.replace(PATH_OCCUPY_PREFIX, "");
            String agvId = redisTemplate.opsForValue().get(key);
            if (agvId != null) {
                occupied.put(node, agvId);
            }
        }
        return occupied;
    }

    /**
     * 计算任务路径的总长度
     * 根据路径节点的坐标计算欧几里得距离之和
     *
     * @param task 任务实体（包含路径信息）
     * @return 路径总长度（米）
     */
    public double calculatePathLength(Task task) {
        List<String> path = decodePath(task.getPath());
        if (path.size() < 2) {
            return 0;
        }
        double totalLength = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            MapNode node1 = mapNodeRepository.findByNodeCode(path.get(i)).orElse(null);
            MapNode node2 = mapNodeRepository.findByNodeCode(path.get(i + 1)).orElse(null);
            if (node1 != null && node2 != null) {
                totalLength += calculateDistance(node1, node2);
            }
        }
        return totalLength;
    }
}

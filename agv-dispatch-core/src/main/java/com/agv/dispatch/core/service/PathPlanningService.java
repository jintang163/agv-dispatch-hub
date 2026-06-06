package com.agv.dispatch.core.service;

import com.agv.dispatch.common.dto.PathPlanningResult;
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

import static com.agv.dispatch.common.constant.RedisKeyConstant.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PathPlanningService {

    private final MapNodeRepository mapNodeRepository;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, Map<String, Double>> graph = new HashMap<>();
    private final Map<String, MapNode> nodeCache = new HashMap<>();

    private static final double DEFAULT_AGV_SPEED = 1.0;
    private static final double OCCUPATION_PENALTY = 1000.0;
    private static final double BLOCKED_PENALTY = 10000.0;
    private static final double INTERSECTION_PENALTY = 50.0;
    private static final int MAX_REPLAN_ATTEMPTS = 3;

    public void initGraph() {
        List<MapNode> nodes = mapNodeRepository.findAll();
        graph.clear();
        nodeCache.clear();
        for (MapNode node : nodes) {
            nodeCache.put(node.getNodeCode(), node);
            Map<String, Double> neighbors = new HashMap<>();
            if (node.getConnectedNodes() != null && !node.getConnectedNodes().isEmpty()) {
                String[] connected = node.getConnectedNodes().split(",");
                for (String neighborCode : connected) {
                    mapNodeRepository.findByNodeCode(neighborCode.trim()).ifPresent(neighbor -> {
                        double distance = calculateDistance(node, neighbor);
                        double adjustedWeight = adjustEdgeWeight(node, neighbor, distance);
                        neighbors.put(neighborCode.trim(), adjustedWeight);
                    });
                }
            }
            graph.put(node.getNodeCode(), neighbors);
        }
        log.info("地图拓扑图初始化完成，节点数: {}", graph.size());
    }

    private double adjustEdgeWeight(MapNode node1, MapNode node2, double baseDistance) {
        double weight = baseDistance;
        if (Boolean.TRUE.equals(node1.getIsIntersection()) || Boolean.TRUE.equals(node2.getIsIntersection())) {
            weight += INTERSECTION_PENALTY;
        }
        if (node1.getSpeedLimit() != null && node1.getSpeedLimit() > 0) {
            weight *= (DEFAULT_AGV_SPEED / node1.getSpeedLimit());
        }
        if (node2.getSpeedLimit() != null && node2.getSpeedLimit() > 0) {
            weight *= (DEFAULT_AGV_SPEED / node2.getSpeedLimit());
        }
        return weight;
    }

    public List<String> planPath(String startPoint, String endPoint) {
        return planPathWithAlgorithm(startPoint, endPoint, "A*").getPath();
    }

    public PathPlanningResult planPathWithAlgorithm(String startPoint, String endPoint, String algorithm) {
        if (startPoint.equals(endPoint)) {
            return PathPlanningResult.success(Collections.singletonList(startPoint), 0, algorithm);
        }
        if (graph.isEmpty()) {
            initGraph();
        }

        List<String> path;
        switch (algorithm.toUpperCase()) {
            case "ASTAR":
            case "A*":
                path = astar(startPoint, endPoint);
                break;
            case "DIJKSTRA":
                path = dijkstra(startPoint, endPoint);
                break;
            default:
                path = astar(startPoint, endPoint);
        }

        if (path.isEmpty()) {
            return PathPlanningResult.failure("无法规划路径，起点: " + startPoint + ", 终点: " + endPoint);
        }

        double totalDistance = calculatePathDistance(path);
        return PathPlanningResult.success(path, totalDistance, algorithm);
    }

    private List<String> dijkstra(String startPoint, String endPoint) {
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
                double blockedPenalty = getBlockedPenalty(nextNode);
                double newDist = distances.get(currentNode) + edgeWeight + occupationPenalty + blockedPenalty;

                if (newDist < distances.getOrDefault(nextNode, Double.MAX_VALUE)) {
                    distances.put(nextNode, newDist);
                    previous.put(nextNode, currentNode);
                    pq.add(new AbstractMap.SimpleEntry<>(nextNode, newDist));
                }
            }
        }

        return reconstructPath(previous, startPoint, endPoint);
    }

    private List<String> astar(String startPoint, String endPoint) {
        Map<String, Double> gScore = new HashMap<>();
        Map<String, Double> fScore = new HashMap<>();
        Map<String, String> previous = new HashMap<>();

        for (String node : graph.keySet()) {
            gScore.put(node, Double.MAX_VALUE);
            fScore.put(node, Double.MAX_VALUE);
        }
        gScore.put(startPoint, 0.0);
        fScore.put(startPoint, heuristic(startPoint, endPoint));

        PriorityQueue<Map.Entry<String, Double>> openSet = new PriorityQueue<>(
                Map.Entry.comparingByValue());
        openSet.add(new AbstractMap.SimpleEntry<>(startPoint, fScore.get(startPoint)));

        Set<String> closedSet = new HashSet<>();

        while (!openSet.isEmpty()) {
            Map.Entry<String, Double> current = openSet.poll();
            String currentNode = current.getKey();

            if (currentNode.equals(endPoint)) {
                return reconstructPath(previous, startPoint, endPoint);
            }

            if (closedSet.contains(currentNode)) {
                continue;
            }
            closedSet.add(currentNode);

            Map<String, Double> neighbors = graph.getOrDefault(currentNode, Collections.emptyMap());
            for (Map.Entry<String, Double> neighbor : neighbors.entrySet()) {
                String nextNode = neighbor.getKey();
                if (closedSet.contains(nextNode)) {
                    continue;
                }

                double edgeWeight = neighbor.getValue();
                double occupationPenalty = getPathOccupationPenalty(nextNode);
                double blockedPenalty = getBlockedPenalty(nextNode);
                double tentativeG = gScore.get(currentNode) + edgeWeight + occupationPenalty + blockedPenalty;

                if (tentativeG < gScore.getOrDefault(nextNode, Double.MAX_VALUE)) {
                    previous.put(nextNode, currentNode);
                    gScore.put(nextNode, tentativeG);
                    double f = tentativeG + heuristic(nextNode, endPoint);
                    fScore.put(nextNode, f);
                    openSet.add(new AbstractMap.SimpleEntry<>(nextNode, f));
                }
            }
        }

        return Collections.emptyList();
    }

    private double heuristic(String nodeA, String nodeB) {
        MapNode n1 = nodeCache.get(nodeA);
        MapNode n2 = nodeCache.get(nodeB);
        if (n1 == null || n2 == null) {
            return 0;
        }
        return calculateDistance(n1, n2);
    }

    private double getPathOccupationPenalty(String nodeCode) {
        String key = PATH_OCCUPY_PREFIX + nodeCode;
        Boolean occupied = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(occupied) ? OCCUPATION_PENALTY : 0.0;
    }

    private double getBlockedPenalty(String nodeCode) {
        String key = PATH_BLOCKED_PREFIX + nodeCode;
        Boolean blocked = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(blocked) ? BLOCKED_PENALTY : 0.0;
    }

    public PathPlanningResult planPathWithDetour(String startPoint, String endPoint, Set<String> avoidNodes) {
        if (startPoint.equals(endPoint)) {
            return PathPlanningResult.success(Collections.singletonList(startPoint), 0, "A*-DETOUR");
        }
        if (graph.isEmpty()) {
            initGraph();
        }

        Map<String, Double> gScore = new HashMap<>();
        Map<String, Double> fScore = new HashMap<>();
        Map<String, String> previous = new HashMap<>();

        for (String node : graph.keySet()) {
            gScore.put(node, Double.MAX_VALUE);
            fScore.put(node, Double.MAX_VALUE);
        }
        gScore.put(startPoint, 0.0);
        fScore.put(startPoint, heuristic(startPoint, endPoint));

        PriorityQueue<Map.Entry<String, Double>> openSet = new PriorityQueue<>(
                Map.Entry.comparingByValue());
        openSet.add(new AbstractMap.SimpleEntry<>(startPoint, fScore.get(startPoint)));

        Set<String> closedSet = new HashSet<>();

        while (!openSet.isEmpty()) {
            Map.Entry<String, Double> current = openSet.poll();
            String currentNode = current.getKey();

            if (currentNode.equals(endPoint)) {
                List<String> path = reconstructPath(previous, startPoint, endPoint);
                double distance = calculatePathDistance(path);
                return PathPlanningResult.detour(path, distance, avoidNodes.toString(), "A*-DETOUR");
            }

            if (closedSet.contains(currentNode)) {
                continue;
            }
            closedSet.add(currentNode);

            Map<String, Double> neighbors = graph.getOrDefault(currentNode, Collections.emptyMap());
            for (Map.Entry<String, Double> neighbor : neighbors.entrySet()) {
                String nextNode = neighbor.getKey();
                if (closedSet.contains(nextNode) || avoidNodes.contains(nextNode)) {
                    continue;
                }

                double edgeWeight = neighbor.getValue();
                double occupationPenalty = getPathOccupationPenalty(nextNode);
                double tentativeG = gScore.get(currentNode) + edgeWeight + occupationPenalty;

                if (tentativeG < gScore.getOrDefault(nextNode, Double.MAX_VALUE)) {
                    previous.put(nextNode, currentNode);
                    gScore.put(nextNode, tentativeG);
                    double f = tentativeG + heuristic(nextNode, endPoint);
                    fScore.put(nextNode, f);
                    openSet.add(new AbstractMap.SimpleEntry<>(nextNode, f));
                }
            }
        }

        return PathPlanningResult.failure("无法规划绕行路径，需避开节点: " + avoidNodes);
    }

    public PathPlanningResult dynamicReplan(Task task, String blockedNode) {
        List<String> currentPath = decodePath(task.getPath());
        Integer currentStep = task.getCurrentStep();
        if (currentStep == null) {
            currentStep = 0;
        }

        if (currentStep >= currentPath.size() - 1) {
            return PathPlanningResult.failure("任务已接近完成，无需重规划");
        }

        String currentPosition = currentPath.get(currentStep);
        Set<String> blockedNodes = new HashSet<>();
        blockedNodes.add(blockedNode);

        for (int i = 0; i < currentStep; i++) {
            blockedNodes.add(currentPath.get(i));
        }

        String originalPath = task.getPath();

        for (int attempt = 0; attempt < MAX_REPLAN_ATTEMPTS; attempt++) {
            PathPlanningResult result = planPathWithDetour(currentPosition, task.getEndPoint(), blockedNodes);
            if (result.isSuccess()) {
                List<String> newPath = new ArrayList<>();
                for (int i = 0; i < currentStep; i++) {
                    newPath.add(currentPath.get(i));
                }
                newPath.addAll(result.getPath());

                double totalDistance = calculatePathDistance(newPath);
                return PathPlanningResult.detour(newPath, totalDistance, originalPath, "DYNAMIC-REPLAN");
            }

            log.warn("第 {} 次重规划失败，尝试减少规避节点", attempt + 1);
            blockedNodes.remove(blockedNode);
        }

        return PathPlanningResult.failure("动态重规划失败，所有路径均被阻塞");
    }

    public PathPlanningResult replanFromCurrentPosition(Task task) {
        List<String> currentPath = decodePath(task.getPath());
        Integer currentStep = task.getCurrentStep();
        if (currentStep == null) {
            currentStep = 0;
        }

        if (currentStep >= currentPath.size() - 1) {
            return PathPlanningResult.failure("任务已接近完成，无需重规划");
        }

        String currentPosition = currentPath.get(currentStep);
        PathPlanningResult result = planPathWithAlgorithm(currentPosition, task.getEndPoint(), "A*");

        if (!result.isSuccess()) {
            return result;
        }

        List<String> newPath = new ArrayList<>();
        for (int i = 0; i < currentStep; i++) {
            newPath.add(currentPath.get(i));
        }
        newPath.addAll(result.getPath());

        double totalDistance = calculatePathDistance(newPath);
        return PathPlanningResult.builder()
                .success(true)
                .path(newPath)
                .totalDistance(totalDistance)
                .algorithm("REPLAN-FROM-CURRENT")
                .hasDetour(!newPath.equals(currentPath))
                .originalPath(task.getPath())
                .build();
    }

    public boolean tryLockNode(String nodeCode, String agvId) {
        String key = NODE_LOCK_PREFIX + nodeCode;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(key, agvId, NODE_LOCK_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            log.debug("AGV {} 获得节点锁: {}", agvId, nodeCode);
        }
        return Boolean.TRUE.equals(locked);
    }

    public void unlockNode(String nodeCode, String agvId) {
        String key = NODE_LOCK_PREFIX + nodeCode;
        String holder = redisTemplate.opsForValue().get(key);
        if (agvId.equals(holder)) {
            redisTemplate.delete(key);
            log.debug("AGV {} 释放节点锁: {}", agvId, nodeCode);
        }
    }

    public boolean tryLockIntersection(String intersectionCode, String agvId) {
        String key = INTERSECTION_LOCK_PREFIX + intersectionCode;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(key, agvId, INTERSECTION_LOCK_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            log.debug("AGV {} 获得路口锁: {}", agvId, intersectionCode);
        }
        return Boolean.TRUE.equals(locked);
    }

    public void unlockIntersection(String intersectionCode, String agvId) {
        String key = INTERSECTION_LOCK_PREFIX + intersectionCode;
        String holder = redisTemplate.opsForValue().get(key);
        if (agvId.equals(holder)) {
            redisTemplate.delete(key);
            log.debug("AGV {} 释放路口锁: {}", agvId, intersectionCode);
        }
    }

    public boolean isNodeLocked(String nodeCode) {
        String key = NODE_LOCK_PREFIX + nodeCode;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public String getNodeLockHolder(String nodeCode) {
        String key = NODE_LOCK_PREFIX + nodeCode;
        return redisTemplate.opsForValue().get(key);
    }

    public void markPathBlocked(String nodeCode, String reason) {
        String key = PATH_BLOCKED_PREFIX + nodeCode;
        redisTemplate.opsForValue().set(key, reason, PATH_BLOCKED_SECONDS, TimeUnit.SECONDS);
        log.warn("节点 {} 被标记为阻塞: {}", nodeCode, reason);
    }

    public void clearPathBlocked(String nodeCode) {
        String key = PATH_BLOCKED_PREFIX + nodeCode;
        redisTemplate.delete(key);
        log.info("节点 {} 阻塞已清除", nodeCode);
    }

    public boolean isPathBlocked(String nodeCode) {
        String key = PATH_BLOCKED_PREFIX + nodeCode;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Map<String, String> getBlockedPaths() {
        Set<String> keys = redisTemplate.keys(PATH_BLOCKED_PREFIX + "*");
        Map<String, String> blocked = new HashMap<>();
        if (keys == null) {
            return blocked;
        }
        for (String key : keys) {
            String node = key.replace(PATH_BLOCKED_PREFIX, "");
            String reason = redisTemplate.opsForValue().get(key);
            if (reason != null) {
                blocked.put(node, reason);
            }
        }
        return blocked;
    }

    public void setAgvWaitingFor(String agvId, String targetAgvId) {
        String key = AGV_WAITING_FOR_PREFIX + agvId;
        redisTemplate.opsForValue().set(key, targetAgvId, WAITING_STATUS_SECONDS, TimeUnit.SECONDS);
    }

    public String getAgvWaitingFor(String agvId) {
        String key = AGV_WAITING_FOR_PREFIX + agvId;
        return redisTemplate.opsForValue().get(key);
    }

    public void clearAgvWaitingFor(String agvId) {
        String key = AGV_WAITING_FOR_PREFIX + agvId;
        redisTemplate.delete(key);
    }

    public boolean checkPathConflict(List<String> path1, List<String> path2) {
        Set<String> path1Set = new HashSet<>(path1);
        for (String node : path2) {
            if (path1Set.contains(node)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getConflictNodes(List<String> path1, List<String> path2) {
        Set<String> path1Set = new HashSet<>(path1);
        List<String> conflicts = new ArrayList<>();
        for (String node : path2) {
            if (path1Set.contains(node)) {
                conflicts.add(node);
            }
        }
        return conflicts;
    }

    public double calculatePathDistance(List<String> path) {
        if (path == null || path.size() < 2) {
            return 0;
        }
        double totalDistance = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            MapNode node1 = nodeCache.get(path.get(i));
            MapNode node2 = nodeCache.get(path.get(i + 1));
            if (node1 != null && node2 != null) {
                totalDistance += calculateDistance(node1, node2);
            }
        }
        return totalDistance;
    }

    public double estimateTravelTime(List<String> path) {
        double distance = calculatePathDistance(path);
        return distance / DEFAULT_AGV_SPEED;
    }

    private List<String> reconstructPath(Map<String, String> previous, String start, String end) {
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

    public boolean isPathAvailable(List<String> path) {
        for (String node : path) {
            String key = PATH_OCCUPY_PREFIX + node;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                return false;
            }
        }
        return true;
    }

    public void occupyPath(String agvId, List<String> path, int currentStep) {
        if (path == null || currentStep >= path.size()) {
            return;
        }

        int lookAhead = Math.min(currentStep + 3, path.size());
        for (int i = currentStep; i < lookAhead; i++) {
            String node = path.get(i);
            String key = PATH_OCCUPY_PREFIX + node;
            redisTemplate.opsForValue().set(key, agvId, PATH_OCCUPY_SECONDS, TimeUnit.SECONDS);

            MapNode mapNode = nodeCache.get(node);
            if (mapNode != null && Boolean.TRUE.equals(mapNode.getIsIntersection())) {
                tryLockIntersection(node, agvId);
            }
        }
    }

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

            MapNode mapNode = nodeCache.get(node);
            if (mapNode != null && Boolean.TRUE.equals(mapNode.getIsIntersection())) {
                unlockIntersection(node, agvId);
            }
        }
    }

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

        Set<String> intersectionKeys = redisTemplate.keys(INTERSECTION_LOCK_PREFIX + "*");
        if (intersectionKeys != null) {
            for (String key : intersectionKeys) {
                String holder = redisTemplate.opsForValue().get(key);
                if (agvId.equals(holder)) {
                    redisTemplate.delete(key);
                }
            }
        }

        Set<String> nodeKeys = redisTemplate.keys(NODE_LOCK_PREFIX + "*");
        if (nodeKeys != null) {
            for (String key : nodeKeys) {
                String holder = redisTemplate.opsForValue().get(key);
                if (agvId.equals(holder)) {
                    redisTemplate.delete(key);
                }
            }
        }

        clearAgvWaitingFor(agvId);
    }

    private double calculateDistance(MapNode node1, MapNode node2) {
        double dx = node1.getXCoord() - node2.getXCoord();
        double dy = node1.getYCoord() - node2.getYCoord();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public String encodePath(List<String> path) {
        return JSON.toJSONString(path);
    }

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

    public Map<String, String> getLockedIntersections() {
        Set<String> keys = redisTemplate.keys(INTERSECTION_LOCK_PREFIX + "*");
        Map<String, String> locked = new HashMap<>();
        if (keys == null) {
            return locked;
        }
        for (String key : keys) {
            String intersection = key.replace(INTERSECTION_LOCK_PREFIX, "");
            String agvId = redisTemplate.opsForValue().get(key);
            if (agvId != null) {
                locked.put(intersection, agvId);
            }
        }
        return locked;
    }

    public double calculatePathLength(Task task) {
        List<String> path = decodePath(task.getPath());
        return calculatePathDistance(path);
    }

    public void refreshNodeCache() {
        List<MapNode> nodes = mapNodeRepository.findAll();
        nodeCache.clear();
        for (MapNode node : nodes) {
            nodeCache.put(node.getNodeCode(), node);
        }
    }

    public MapNode getNodeFromCache(String nodeCode) {
        return nodeCache.get(nodeCode);
    }
}

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

@Slf4j
@Service
@RequiredArgsConstructor
public class PathPlanningService {

    private final MapNodeRepository mapNodeRepository;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, Map<String, Double>> graph = new HashMap<>();

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

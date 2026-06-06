package com.agv.dispatch.core.service;

import com.agv.dispatch.common.dto.PathPlanningResult;
import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.ConflictRecord;
import com.agv.dispatch.common.entity.MapNode;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.enums.ConflictResolutionStrategy;
import com.agv.dispatch.common.enums.ConflictType;
import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.repository.ConflictRecordRepository;
import com.agv.dispatch.core.repository.TaskRepository;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.agv.dispatch.common.constant.RedisKeyConstant.*;

/**
 * 冲突检测与解决服务
 * 负责检测AGV之间的各类冲突（路径冲突、位置冲突、资源冲突、路口冲突），
 * 并根据冲突类型选择最优解决策略（等待、绕行、让行、重分配），
 * 同时管理路口通行协议和分布式锁的生命周期
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionService {

    /**
     * AGV数据访问层
     */
    private final AgvRepository agvRepository;

    /**
     * 任务数据访问层
     */
    private final TaskRepository taskRepository;

    /**
     * 冲突记录数据访问层
     */
    private final ConflictRecordRepository conflictRecordRepository;

    /**
     * 路径规划服务，用于路径解码、节点查询、锁管理等
     */
    private final PathPlanningService pathPlanningService;

    /**
     * Redis模板，用于分布式缓存和锁管理
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * AGV安全距离（米），小于此距离视为位置冲突
     */
    private static final double SAFE_DISTANCE = 2.0;

    /**
     * 绕行策略阈值，当有至少此数量的绕行选项时采用绕行策略
     */
    private static final int DETOUR_THRESHOLD = 2;

    /**
     * 检测所有类型的冲突
     * 综合检测路径冲突、位置冲突、资源冲突和路口冲突四类冲突，
     * 检测完成后将冲突结果缓存到Redis中
     *
     * @return 冲突记录列表
     */
    public List<ConflictRecord> detectConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();

        conflicts.addAll(detectPathConflicts());
        conflicts.addAll(detectPositionConflicts());
        conflicts.addAll(detectResourceConflicts());
        conflicts.addAll(detectIntersectionConflicts());

        cacheConflicts(conflicts);
        return conflicts;
    }

    private List<ConflictRecord> detectPathConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();
        List<Agv> workingAgvs = agvRepository.findByStatusIn(
                Arrays.asList(com.agv.dispatch.common.enums.AgvStatus.WORKING,
                        com.agv.dispatch.common.enums.AgvStatus.PAUSED));

        Map<String, List<Agv>> nodeOccupancy = new HashMap<>();

        for (Agv agv : workingAgvs) {
            Task task = taskRepository.findById(agv.getCurrentTaskId()).orElse(null);
            if (task == null || task.getPath() == null) {
                continue;
            }

            List<String> path = pathPlanningService.decodePath(task.getPath());
            Integer currentStep = task.getCurrentStep();
            if (currentStep == null) {
                currentStep = 0;
            }

            int lookAhead = Math.min(currentStep + 3, path.size());
            for (int i = currentStep; i < lookAhead; i++) {
                if (i >= 0 && i < path.size()) {
                    String node = path.get(i);
                    nodeOccupancy.computeIfAbsent(node, k -> new ArrayList<>()).add(agv);
                }
            }
        }

        for (Map.Entry<String, List<Agv>> entry : nodeOccupancy.entrySet()) {
            String node = entry.getKey();
            List<Agv> agvsAtNode = entry.getValue();
            if (agvsAtNode.size() >= 2) {
                for (int i = 0; i < agvsAtNode.size(); i++) {
                    for (int j = i + 1; j < agvsAtNode.size(); j++) {
                        Agv agv1 = agvsAtNode.get(i);
                        Agv agv2 = agvsAtNode.get(j);
                        ConflictType conflictType = determineConflictType(agv1, agv2, node);
                        conflicts.add(createConflictRecord(agv1, agv2, conflictType, node));
                    }
                }
            }
        }

        return conflicts;
    }

    /**
     * 检测路口冲突
     * 分析所有工作中AGV的未来路径（向前预测5步），识别即将到达同一路口的多辆AGV，
     * 当发现至少2辆AGV将到达同一路口时，生成路口冲突记录
     *
     * @return 路口冲突记录列表
     */
    private List<ConflictRecord> detectIntersectionConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();
        List<Agv> workingAgvs = agvRepository.findByStatusIn(
                Arrays.asList(com.agv.dispatch.common.enums.AgvStatus.WORKING,
                        com.agv.dispatch.common.enums.AgvStatus.PAUSED));

        Map<String, List<Agv>> intersectionApproach = new HashMap<>();

        for (Agv agv : workingAgvs) {
            Task task = taskRepository.findById(agv.getCurrentTaskId()).orElse(null);
            if (task == null || task.getPath() == null) {
                continue;
            }

            List<String> path = pathPlanningService.decodePath(task.getPath());
            Integer currentStep = task.getCurrentStep();
            if (currentStep == null) {
                currentStep = 0;
            }

            int lookAhead = Math.min(currentStep + 5, path.size());
            for (int i = currentStep; i < lookAhead; i++) {
                if (i >= 0 && i < path.size()) {
                    String node = path.get(i);
                    MapNode mapNode = pathPlanningService.getNodeFromCache(node);
                    if (mapNode != null && Boolean.TRUE.equals(mapNode.getIsIntersection())) {
                        intersectionApproach.computeIfAbsent(node, k -> new ArrayList<>()).add(agv);
                        break;
                    }
                }
                MapNode currentNode = pathPlanningService.getNodeFromCache(node);
                if (currentNode != null && Boolean.TRUE.equals(currentNode.getIsIntersection())) {
                    break;
                }
            }
        }

        for (Map.Entry<String, List<Agv>> entry : intersectionApproach.entrySet()) {
            String intersection = entry.getKey();
            List<Agv> agvs = entry.getValue();
            if (agvs.size() >= 2) {
                for (int i = 0; i < agvs.size(); i++) {
                    for (int j = i + 1; j < agvs.size(); j++) {
                        conflicts.add(createConflictRecord(
                                agvs.get(i), agvs.get(j), ConflictType.RESOURCE,
                                "路口:" + intersection));
                    }
                }
                log.warn("检测到路口冲突: {}, AGV数量: {}", intersection, agvs.size());
            }
        }

        return conflicts;
    }

    private ConflictType determineConflictType(Agv agv1, Agv agv2, String node) {
        Task task1 = taskRepository.findById(agv1.getCurrentTaskId()).orElse(null);
        Task task2 = taskRepository.findById(agv2.getCurrentTaskId()).orElse(null);

        if (task1 == null || task2 == null) {
            return ConflictType.RESOURCE;
        }

        List<String> path1 = pathPlanningService.decodePath(task1.getPath());
        List<String> path2 = pathPlanningService.decodePath(task2.getPath());

        Integer step1 = task1.getCurrentStep() != null ? task1.getCurrentStep() : 0;
        Integer step2 = task2.getCurrentStep() != null ? task2.getCurrentStep() : 0;

        int idx1 = path1.indexOf(node);
        int idx2 = path2.indexOf(node);

        if (idx1 > 0 && idx2 > 0 && idx1 < path1.size() - 1 && idx2 < path2.size() - 1) {
            String prev1 = path1.get(idx1 - 1);
            String prev2 = path2.get(idx2 - 1);
            String next1 = path1.get(idx1 + 1);
            String next2 = path2.get(idx2 + 1);

            if (prev1.equals(next2) && prev2.equals(next1)) {
                return ConflictType.HEAD_ON;
            }

            if (prev1.equals(prev2)) {
                return ConflictType.FOLLOW;
            }

            if (!prev1.equals(prev2) && !next1.equals(next2)) {
                return ConflictType.CROSS;
            }
        }

        return ConflictType.RESOURCE;
    }

    private List<ConflictRecord> detectPositionConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();
        List<Agv> workingAgvs = agvRepository.findByStatusIn(
                Arrays.asList(com.agv.dispatch.common.enums.AgvStatus.WORKING,
                        com.agv.dispatch.common.enums.AgvStatus.PAUSED));

        for (int i = 0; i < workingAgvs.size(); i++) {
            for (int j = i + 1; j < workingAgvs.size(); j++) {
                Agv agv1 = workingAgvs.get(i);
                Agv agv2 = workingAgvs.get(j);

                if (agv1.getXCoord() == null || agv1.getYCoord() == null ||
                    agv2.getXCoord() == null || agv2.getYCoord() == null) {
                    continue;
                }

                double distance = calculateDistance(agv1, agv2);
                if (distance < SAFE_DISTANCE) {
                    log.warn("AGV距离过近: {} 和 {}, 距离: {}m", agv1.getAgvNo(), agv2.getAgvNo(), distance);
                    conflicts.add(createConflictRecord(agv1, agv2, ConflictType.RESOURCE,
                            agv1.getCurrentPosition()));
                }
            }
        }

        return conflicts;
    }

    private List<ConflictRecord> detectResourceConflicts() {
        return new ArrayList<>();
    }

    private ConflictRecord createConflictRecord(Agv agv1, Agv agv2,
                                                ConflictType conflictType, String location) {
        ConflictRecord record = new ConflictRecord();
        record.setAgvId1(agv1.getId());
        record.setAgvId2(agv2.getId());
        record.setConflictType(conflictType);
        record.setLocation(location);
        record.setTaskId1(agv1.getCurrentTaskId());
        record.setTaskId2(agv2.getCurrentTaskId());
        record.setResolved(false);

        ConflictRecord saved = conflictRecordRepository.save(record);
        log.warn("检测到冲突: type={}, agv1={}, agv2={}, location={}",
                conflictType, agv1.getAgvNo(), agv2.getAgvNo(), location);
        return saved;
    }

    /**
     * 解决单个冲突
     * 根据冲突ID查找冲突记录，检查冲突是否已解决，
     * 然后应用相应的解决策略，最后更新冲突记录状态为已解决
     *
     * @param conflictId 冲突记录ID
     * @return 冲突解决结果描述
     * @throws IllegalArgumentException 当冲突记录不存在时抛出
     */
    @Transactional
    public String resolveConflict(Long conflictId) {
        ConflictRecord conflict = conflictRecordRepository.findById(conflictId)
                .orElseThrow(() -> new IllegalArgumentException("冲突记录不存在: " + conflictId));

        if (Boolean.TRUE.equals(conflict.getResolved())) {
            return "冲突已解决";
        }

        String resolution = applyResolutionStrategy(conflict);

        conflict.setResolved(true);
        conflict.setResolvedTime(LocalDateTime.now());
        conflict.setResolution(resolution);
        conflictRecordRepository.save(conflict);

        log.info("冲突已解决: {}, 策略: {}", conflictId, resolution);
        return resolution;
    }

    private String applyResolutionStrategy(ConflictRecord conflict) {
        Task task1 = taskRepository.findById(conflict.getTaskId1()).orElse(null);
        Task task2 = taskRepository.findById(conflict.getTaskId2()).orElse(null);

        if (task1 == null && task2 == null) {
            return "无任务关联，冲突自动解除";
        }

        Agv agv1 = agvRepository.findById(conflict.getAgvId1()).orElse(null);
        Agv agv2 = agvRepository.findById(conflict.getAgvId2()).orElse(null);

        ConflictResolutionStrategy strategy = selectResolutionStrategy(conflict, task1, task2, agv1, agv2);

        switch (strategy) {
            case WAIT:
                return applyWaitStrategy(conflict, task1, task2, agv1, agv2);
            case DETOUR:
                return applyDetourStrategy(conflict, task1, task2, agv1, agv2);
            case YIELD:
                return applyYieldStrategy(conflict, task1, task2, agv1, agv2);
            case REASSIGN:
                return applyReassignStrategy(conflict, task1, task2, agv1, agv2);
            default:
                return applyWaitStrategy(conflict, task1, task2, agv1, agv2);
        }
    }

    /**
     * 选择冲突解决策略
     * 根据冲突类型、任务属性和节点类型智能选择最优解决策略：
     * 1. 对向冲突优先考虑绕行（如果有足够绕行选项），否则等待
     * 2. 交叉冲突在路口采用让行策略，否则等待
     * 3. 路口节点冲突采用让行策略
     * 4. 其他情况默认采用等待策略
     *
     * @param conflict 冲突记录
     * @param task1 AGV1的任务
     * @param task2 AGV2的任务
     * @param agv1 AGV1实体
     * @param agv2 AGV2实体
     * @return 冲突解决策略枚举
     */
    private ConflictResolutionStrategy selectResolutionStrategy(ConflictRecord conflict,
                                                           Task task1, Task task2,
                                                           Agv agv1, Agv agv2) {
        if (conflict.getConflictType() == ConflictType.HEAD_ON) {
            if (task1 != null && task2 != null) {
                int detourCount1 = countDetourOptions(task1);
                int detourCount2 = countDetourOptions(task2);
                if (detourCount1 >= DETOUR_THRESHOLD || detourCount2 >= DETOUR_THRESHOLD) {
                    return ConflictResolutionStrategy.DETOUR;
                }
            }
            return ConflictResolutionStrategy.WAIT;
        }

        if (conflict.getConflictType() == ConflictType.CROSS) {
            String location = conflict.getLocation();
            if (location != null && location.startsWith("路口")) {
                return ConflictResolutionStrategy.YIELD;
            }
            return ConflictResolutionStrategy.WAIT;
        }

        MapNode node = pathPlanningService.getNodeFromCache(conflict.getLocation());
        if (node != null && Boolean.TRUE.equals(node.getIsIntersection())) {
            return ConflictResolutionStrategy.YIELD;
        }

        return ConflictResolutionStrategy.WAIT;
    }

    private int countDetourOptions(Task task) {
        if (task == null || task.getPath() == null) {
            return 0;
        }
        List<String> path = pathPlanningService.decodePath(task.getPath());
        Integer currentStep = task.getCurrentStep();
        if (currentStep == null || currentStep >= path.size() - 1) {
            return 0;
        }
        String currentPos = path.get(currentStep);
        Set<String> avoidNodes = new HashSet<>();
        for (int i = Math.max(0, currentStep - 1); i <= Math.min(currentStep + 2, path.size() - 1); i++) {
            avoidNodes.add(path.get(i));
        }
        PathPlanningResult result = pathPlanningService.planPathWithDetour(
                currentPos, task.getEndPoint(), avoidNodes);
        return result.isSuccess() ? 1 : 0;
    }

    /**
     * 应用等待策略
     * 根据以下优先级顺序决定哪辆AGV需要暂停等待：
     * 1. 任务存在性：无任务的AGV让行
     * 2. 任务优先级：低优先级任务让行
     * 3. 任务截止时间：截止时间晚的任务让行
     * 4. 任务进度：进度慢的AGV让行
     * 暂停的AGV会设置等待关系，等待对方通过后再继续
     *
     * @param conflict 冲突记录
     * @param task1 AGV1的任务
     * @param task2 AGV2的任务
     * @param agv1 AGV1实体
     * @param agv2 AGV2实体
     * @return 等待策略执行结果描述
     */
    private String applyWaitStrategy(ConflictRecord conflict,
                                    Task task1, Task task2,
                                    Agv agv1, Agv agv2) {
        if (task1 == null) {
            pauseAgv(agv2);
            pathPlanningService.setAgvWaitingFor(agv2.getId(), agv1.getId());
            return "AGV2任务暂停，等待AGV1通过";
        }
        if (task2 == null) {
            pauseAgv(agv1);
            pathPlanningService.setAgvWaitingFor(agv1.getId(), agv2.getId());
            return "AGV1任务暂停，等待AGV2通过";
        }

        int priorityCompare = task1.getPriority().getCode() - task2.getPriority().getCode();
        if (priorityCompare > 0) {
            pauseAgv(agv2);
            pathPlanningService.setAgvWaitingFor(agv2.getId(), agv1.getId());
            return String.format("任务1优先级(%s)高于任务2(%s)，AGV2暂停让行",
                    task1.getPriority(), task2.getPriority());
        } else if (priorityCompare < 0) {
            pauseAgv(agv1);
            pathPlanningService.setAgvWaitingFor(agv1.getId(), agv2.getId());
            return String.format("任务2优先级(%s)高于任务1(%s)，AGV1暂停让行",
                    task2.getPriority(), task1.getPriority());
        }

        if (task1.getDeadline() != null && task2.getDeadline() != null) {
            if (task1.getDeadline().isBefore(task2.getDeadline())) {
                pauseAgv(agv2);
                pathPlanningService.setAgvWaitingFor(agv2.getId(), agv1.getId());
                return "任务1截止时间更早，AGV2暂停让行";
            } else {
                pauseAgv(agv1);
                pathPlanningService.setAgvWaitingFor(agv1.getId(), agv2.getId());
                return "任务2截止时间更早，AGV1暂停让行";
            }
        }

        int step1 = task1.getCurrentStep() != null ? task1.getCurrentStep() : 0;
        int step2 = task2.getCurrentStep() != null ? task2.getCurrentStep() : 0;
        if (step1 > step2) {
            pauseAgv(agv2);
            pathPlanningService.setAgvWaitingFor(agv2.getId(), agv1.getId());
            return "AGV1进度更快，AGV2暂停让行";
        } else {
            pauseAgv(agv1);
            pathPlanningService.setAgvWaitingFor(agv1.getId(), agv2.getId());
            return "AGV2进度更快，AGV1暂停让行";
        }
    }

    /**
     * 应用绕行策略
     * 为冲突中的AGV规划避开冲突节点的新路径，选择绕行对象的优先级：
     * 1. 如果两辆AGV都有绕行选项，选择优先级低的AGV绕行
     * 2. 如果只有一辆AGV有绕行选项，让该AGV绕行
     * 3. 如果都没有绕行选项，回退到等待策略
     *
     * @param conflict 冲突记录
     * @param task1 AGV1的任务
     * @param task2 AGV2的任务
     * @param agv1 AGV1实体
     * @param agv2 AGV2实体
     * @return 绕行策略执行结果描述
     */
    private String applyDetourStrategy(ConflictRecord conflict,
                                     Task task1, Task task2,
                                     Agv agv1, Agv agv2) {
        if (task1 == null || task2 == null) {
            return applyWaitStrategy(conflict, task1, task2, agv1, agv2);
        }

        List<String> path1 = pathPlanningService.decodePath(task1.getPath());
        List<String> path2 = pathPlanningService.decodePath(task2.getPath());

        List<String> conflictNodes = pathPlanningService.getConflictNodes(path1, path2);

        int detourCount1 = countDetourOptions(task1);
        int detourCount2 = countDetourOptions(task2);

        if (detourCount1 > 0 && detourCount2 > 0) {
            int priorityCompare = task1.getPriority().getCode() - task2.getPriority().getCode();
            if (priorityCompare >= 0) {
                return performDetour(task2, agv2, conflictNodes, "AGV2");
            } else {
                return performDetour(task1, agv1, conflictNodes, "AGV1");
            }
        } else if (detourCount1 > 0) {
            return performDetour(task1, agv1, conflictNodes, "AGV1");
        } else if (detourCount2 > 0) {
            return performDetour(task2, agv2, conflictNodes, "AGV2");
        }

        return applyWaitStrategy(conflict, task1, task2, agv1, agv2);
    }

    private String performDetour(Task task, Agv agv, List<String> conflictNodes, String agvLabel) {
        Set<String> avoidNodes = new HashSet<>(conflictNodes);
        List<String> currentPath = pathPlanningService.decodePath(task.getPath());
        Integer currentStep = task.getCurrentStep();
        if (currentStep == null) {
            currentStep = 0;
        }

        String currentPosition = currentStep < currentPath.size() ?
                currentPath.get(currentStep) : task.getStartPoint();

        PathPlanningResult detourResult = pathPlanningService.planPathWithDetour(
                currentPosition, task.getEndPoint(), avoidNodes);

        if (detourResult.isSuccess()) {
            List<String> newPath = new ArrayList<>();
            for (int i = 0; i < currentStep; i++) {
                newPath.add(currentPath.get(i));
            }
            newPath.addAll(detourResult.getPath());

            task.setPath(pathPlanningService.encodePath(newPath));
            task.setCurrentStep(currentStep);
            taskRepository.save(task);

            pathPlanningService.releaseAllPath(agv.getId());
            pathPlanningService.occupyPath(agv.getId(), newPath, currentStep);

            pathPlanningService.clearAgvWaitingFor(agv.getId());

            log.info("{} 执行绕行重规划: 原路径长度: {}, 新路径长度: {}",
                    agvLabel, currentPath.size(), newPath.size());
            return String.format("%s绕行重规划成功，冲突节点: %s", agvLabel, conflictNodes);
        }

        return String.format("%s绕行失败，转为等待策略", agvLabel);
    }

    /**
     * 应用让行策略（路口会车协议）
     * 通过分布式路口锁实现先到先得的路口通行机制：
     * 1. 尝试为AGV1获取路口锁，成功则让AGV2等待
     * 2. 尝试为AGV2获取路口锁，成功则让AGV1等待
     * 3. 如果都获取失败，回退到等待策略
     * 获取锁的AGV可以优先通过路口，未获取锁的AGV暂停等待
     *
     * @param conflict 冲突记录
     * @param task1 AGV1的任务
     * @param task2 AGV2的任务
     * @param agv1 AGV1实体
     * @param agv2 AGV2实体
     * @return 让行策略执行结果描述
     */
    private String applyYieldStrategy(ConflictRecord conflict,
                                    Task task1, Task task2,
                                    Agv agv1, Agv agv2) {
        String intersection = conflict.getLocation();
        if (intersection != null && intersection.startsWith("路口:")) {
            intersection = intersection.substring(3);
        }

        if (task1 == null || task2 == null) {
            return applyWaitStrategy(conflict, task1, task2, agv1, agv2);
        }

        boolean agv1HasLock = pathPlanningService.tryLockIntersection(intersection, agv1.getId());
        if (agv1HasLock) {
            pauseAgv(agv2);
            pathPlanningService.setAgvWaitingFor(agv2.getId(), agv1.getId());
            return String.format("路口会车协议: AGV1获得路口锁，AGV2暂停等待AGV1通过路口: %s", intersection);
        }

        boolean agv2HasLock = pathPlanningService.tryLockIntersection(intersection, agv2.getId());
        if (agv2HasLock) {
            pauseAgv(agv1);
            pathPlanningService.setAgvWaitingFor(agv1.getId(), agv2.getId());
            return String.format("路口会车协议: AGV2获得路口锁，AGV1暂停等待AGV2通过路口: %s", intersection);
        }

        return applyWaitStrategy(conflict, task1, task2, agv1, agv2);
    }

    private String applyReassignStrategy(ConflictRecord conflict,
                                         Task task1, Task task2,
                                         Agv agv1, Agv agv2) {
        return applyWaitStrategy(conflict, task1, task2, agv1, agv2);
    }

    private void pauseAgv(Agv agv) {
        if (agv == null) {
            return;
        }
        agv.setStatus(com.agv.dispatch.common.enums.AgvStatus.PAUSED);
        agvRepository.save(agv);
        log.info("AGV已暂停: {}", agv.getAgvNo());
    }

    /**
     * 解决所有未解决的冲突
     * 按创建时间倒序遍历所有未解决的冲突记录，逐个调用resolveConflict方法解决，
     * 最后释放所有过期的分布式锁，避免死锁发生
     */
    public void resolveAllConflicts() {
        List<ConflictRecord> unresolved = conflictRecordRepository.findByResolvedFalseOrderByCreateTimeDesc();
        for (ConflictRecord conflict : unresolved) {
            try {
                resolveConflict(conflict.getId());
            } catch (Exception e) {
                log.error("解决冲突失败: {}", conflict.getId(), e);
            }
        }
        releaseExpiredLocks();
    }

    /**
     * 释放过期的分布式锁
     * 遍历Redis中所有的节点锁和路口锁，检查锁的剩余生存时间（TTL），
     * 清理已过期的锁（TTL <= 0），防止因异常导致的锁永久占用，避免死锁
     */
    public void releaseExpiredLocks() {
        Set<String> nodeKeys = redisTemplate.keys(NODE_LOCK_PREFIX + "*");
        if (nodeKeys != null) {
            for (String key : nodeKeys) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl <= 0) {
                    redisTemplate.delete(key);
                    log.debug("清理过期节点锁: {}", key);
                }
            }
        }

        Set<String> intersectionKeys = redisTemplate.keys(INTERSECTION_LOCK_PREFIX + "*");
        if (intersectionKeys != null) {
            for (String key : intersectionKeys) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl <= 0) {
                    redisTemplate.delete(key);
                    log.debug("清理过期路口锁: {}", key);
                }
            }
        }
    }

    private double calculateDistance(Agv agv1, Agv agv2) {
        double dx = agv1.getXCoord() - agv2.getXCoord();
        double dy = agv1.getYCoord() - agv2.getYCoord();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void cacheConflicts(List<ConflictRecord> conflicts) {
        if (conflicts.isEmpty()) {
            redisTemplate.delete(CONFLICT_KEY);
            return;
        }
        Map<String, String> conflictData = conflicts.stream()
                .collect(Collectors.toMap(
                        c -> c.getId().toString(),
                        c -> c.getConflictType().getDesc() + ":" + c.getLocation()
                ));
        redisTemplate.opsForHash().putAll(CONFLICT_KEY, conflictData);
    }

    public List<ConflictRecord> getUnresolvedConflicts() {
        return conflictRecordRepository.findByResolvedFalseOrderByCreateTimeDesc();
    }

    public boolean hasHighPriorityConflict(String agvId) {
        List<ConflictRecord> unresolved = getUnresolvedConflicts();
        for (ConflictRecord conflict : unresolved) {
            if (agvId.equals(conflict.getAgvId1()) || agvId.equals(conflict.getAgvId2())) {
                Task task1 = taskRepository.findById(conflict.getTaskId1()).orElse(null);
                Task task2 = taskRepository.findById(conflict.getTaskId2()).orElse(null);
                if ((task1 != null && task1.getPriority() == TaskPriority.HIGH) ||
                    (task2 != null && task2.getPriority() == TaskPriority.HIGH)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 尝试获取路口通行权
     * AGV在通过路口前调用此方法尝试获取路口锁，获取成功则可以通过，
     * 获取失败则设置等待关系，等待当前持有者通过后再尝试
     *
     * @param agvId AGV ID
     * @param intersectionCode 路口节点编码
     * @return 是否获得路口通行权
     */
    @Transactional
    public boolean tryIntersectionPass(String agvId, String intersectionCode) {
        Agv agv = agvRepository.findById(agvId).orElse(null);
        if (agv == null) {
            return false;
        }

        Task task = taskRepository.findById(agv.getCurrentTaskId()).orElse(null);
        if (task == null) {
            return false;
        }

        boolean locked = pathPlanningService.tryLockIntersection(intersectionCode, agvId);
        if (locked) {
            log.info("AGV {} 获得路口通行权: {}", agv.getAgvNo(), intersectionCode);
            return true;
        }

        String holder = pathPlanningService.getNodeLockHolder(intersectionCode);
        log.info("AGV {} 等待路口: {}, 当前持有者: {}", agv.getAgvNo(), intersectionCode, holder);
        pathPlanningService.setAgvWaitingFor(agvId, holder);
        return false;
    }

    /**
     * 完成路口通行
     * AGV通过路口后调用此方法释放路口锁，同时清除AGV的等待关系，
     * 让其他等待的AGV可以继续尝试获取路口通行权
     *
     * @param agvId AGV ID
     * @param intersectionCode 路口节点编码
     */
    @Transactional
    public void completeIntersectionPass(String agvId, String intersectionCode) {
        pathPlanningService.unlockIntersection(intersectionCode, agvId);
        pathPlanningService.clearAgvWaitingFor(agvId);
        log.info("AGV {} 已通过路口: {}", agvId, intersectionCode);
    }
}

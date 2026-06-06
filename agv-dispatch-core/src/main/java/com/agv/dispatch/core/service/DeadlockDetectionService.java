package com.agv.dispatch.core.service;

import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.DeadlockRecord;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.enums.ConflictResolutionStrategy;
import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.repository.DeadlockRecordRepository;
import com.agv.dispatch.core.repository.TaskRepository;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.agv.dispatch.common.constant.RedisKeyConstant.*;

/**
 * 死锁检测与恢复服务
 * 负责定时检测AGV调度系统中的死锁情况，通过构建等待图和深度优先搜索算法检测循环等待，
 * 并采用牺牲AGV策略进行死锁恢复，支持绕行重规划、任务重分配和标准恢复三种策略
 *
 * @author agv-dispatch
 * @since 2026-06-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlockDetectionService {

    /**
     * AGV数据访问层
     */
    private final AgvRepository agvRepository;

    /**
     * 任务数据访问层
     */
    private final TaskRepository taskRepository;

    /**
     * 死锁记录数据访问层
     */
    private final DeadlockRecordRepository deadlockRecordRepository;

    /**
     * 路径规划服务，用于路径解码、节点查询、锁管理等
     */
    private final PathPlanningService pathPlanningService;

    /**
     * 任务调度服务，用于死锁恢复时的任务重分配（延迟加载避免循环依赖）
     */
    @Lazy
    private final TaskDispatchService taskDispatchService;

    /**
     * Redis模板，用于分布式锁和缓存
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 死锁检测的最小AGV数量，等待链中AGV数量达到此值才判定为死锁
     */
    private static final int MIN_DEADLOCK_AGVS = 2;

    /**
     * 定时死锁检测任务
     * 每10秒执行一次死锁检测，使用Redis分布式锁保证多实例部署时只有一个实例执行检测，
     * 检测完成后自动释放锁。锁持有者校验确保只有获取锁的实例才能释放锁，避免误删
     */
    @Scheduled(fixedDelay = 10000)
    public void scheduledDeadlockDetection() {
        try {
            String lockValue = UUID.randomUUID().toString();
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(DEADLOCK_DETECTION_LOCK, lockValue, DEADLOCK_LOCK_SECONDS, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(locked)) {
                return;
            }

            try {
                detectAndResolveDeadlocks();
            } finally {
                String currentLock = redisTemplate.opsForValue().get(DEADLOCK_DETECTION_LOCK);
                if (lockValue.equals(currentLock)) {
                    redisTemplate.delete(DEADLOCK_DETECTION_LOCK);
                }
            }
        } catch (Exception e) {
            log.error("死锁检测调度异常", e);
        }
    }

    /**
     * 检测并解决所有死锁
     * 先调用detectDeadlocks检测系统中的所有死锁，然后逐个调用resolveDeadlock解决，
     * 最后将死锁信息缓存到Redis中，整个过程异常隔离，单个死锁处理失败不影响其他死锁
     *
     * @return 死锁记录列表
     */
    public List<DeadlockRecord> detectAndResolveDeadlocks() {
        List<DeadlockRecord> deadlocks = detectDeadlocks();

        for (DeadlockRecord deadlock : deadlocks) {
            try {
                resolveDeadlock(deadlock);
            } catch (Exception e) {
                log.error("处理死锁失败: {}", deadlock.getId(), e);
            }
        }

        cacheDeadlocks(deadlocks);
        return deadlocks;
    }

    /**
     * 检测系统中的死锁
     * 死锁检测算法流程：
     * 1. 构建AGV等待关系图（waitGraph）
     * 2. 使用DFS深度优先搜索算法检测等待图中的循环
     * 3. 对于每个循环，如果包含至少MIN_DEADLOCK_AGVS辆AGV，则判定为死锁
     * 4. 创建死锁记录并返回
     *
     * @return 死锁记录列表
     */
    public List<DeadlockRecord> detectDeadlocks() {
        List<DeadlockRecord> deadlocks = new ArrayList<>();

        Map<String, String> waitGraph = buildWaitGraph();

        if (waitGraph.isEmpty()) {
            return deadlocks;
        }

        List<List<String>> cycles = findCycles(waitGraph);

        for (List<String> cycle : cycles) {
            if (cycle.size() >= MIN_DEADLOCK_AGVS) {
                DeadlockRecord record = createDeadlockRecord(cycle);
                deadlocks.add(record);
                log.warn("检测到死锁: 等待链={}, AGV数量={}", cycle, cycle.size());
            }
        }

        return deadlocks;
    }

    /**
     * 构建AGV等待关系图
     * 遍历所有工作中（WORKING或PAUSED状态）的AGV，从两个维度构建等待关系：
     * 1. 显式等待关系：通过getAgvWaitingFor获取AGV正在等待的目标AGV
     * 2. 隐式等待关系：分析AGV未来路径（向前预测3步），如果路径节点被其他AGV占用，则建立等待关系
     * 等待图结构：key为等待方AGV ID，value为被等待方AGV ID
     *
     * @return 等待关系图
     */
    private Map<String, String> buildWaitGraph() {
        Map<String, String> waitGraph = new HashMap<>();

        List<Agv> workingAgvs = agvRepository.findByStatusIn(
                Arrays.asList(com.agv.dispatch.common.enums.AgvStatus.WORKING,
                        com.agv.dispatch.common.enums.AgvStatus.PAUSED));

        for (Agv agv : workingAgvs) {
            String waitingFor = pathPlanningService.getAgvWaitingFor(agv.getId());
            if (waitingFor != null && !waitingFor.isEmpty()) {
                waitGraph.put(agv.getId(), waitingFor);
                log.debug("等待关系: {} -> {}", agv.getAgvNo(), waitingFor);
            }

            Task task = taskRepository.findById(agv.getCurrentTaskId()).orElse(null);
            if (task != null && task.getPath() != null) {
                List<String> path = pathPlanningService.decodePath(task.getPath());
                Integer currentStep = task.getCurrentStep();
                if (currentStep == null) {
                    currentStep = 0;
                }

                int lookAhead = Math.min(currentStep + 3, path.size());
                for (int i = currentStep; i < lookAhead; i++) {
                    if (i >= 0 && i < path.size()) {
                        String node = path.get(i);
                        String occupant = pathPlanningService.getNodeLockHolder(node);
                        if (occupant != null && !occupant.equals(agv.getId())) {
                            if (!waitGraph.containsKey(agv.getId())) {
                                waitGraph.put(agv.getId(), occupant);
                            }
                            break;
                        }
                    }
                }
            }
        }

        return waitGraph;
    }

    /**
     * 检测等待图中的循环
     * 使用深度优先搜索（DFS）算法检测有向图中的环，通过三个辅助集合实现：
     * - visited：记录已访问过的所有节点，避免重复处理
     * - inStack：记录当前递归栈中的节点，用于检测环
     * - path：记录当前搜索路径，用于提取环
     * 对每个未访问的节点启动一次DFS搜索
     *
     * @param waitGraph 等待关系图
     * @return 所有循环的列表，每个循环是一个AGV ID列表
     */
    private List<List<String>> findCycles(Map<String, String> waitGraph) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        List<String> path = new ArrayList<>();

        for (String node : waitGraph.keySet()) {
            if (!visited.contains(node)) {
                dfsForCycle(node, waitGraph, visited, inStack, path, cycles);
            }
        }

        return cycles;
    }

    private void dfsForCycle(String current, Map<String, String> waitGraph,
                            Set<String> visited, Set<String> inStack,
                            List<String> path, List<List<String>> cycles) {
        if (inStack.contains(current)) {
            int idx = path.indexOf(current);
            if (idx != -1) {
                List<String> cycle = new ArrayList<>(path.subList(idx, path.size()));
                cycle.add(current);
                if (!isDuplicateCycle(cycles, cycle)) {
                    cycles.add(cycle);
                }
            }
            return;
        }

        if (visited.contains(current)) {
            return;
        }

        visited.add(current);
        inStack.add(current);
        path.add(current);

        String next = waitGraph.get(current);
        if (next != null) {
            dfsForCycle(next, waitGraph, visited, inStack, path, cycles);
        }

        inStack.remove(current);
        path.remove(path.size() - 1);
    }

    private boolean isDuplicateCycle(List<List<String>> cycles, List<String> newCycle) {
        for (List<String> existing : cycles) {
            if (existing.size() != newCycle.size()) {
                continue;
            }
            Set<String> existingSet = new HashSet<>(existing);
            Set<String> newSet = new HashSet<>(newCycle);
            if (existingSet.equals(newSet)) {
                return true;
            }
        }
        return false;
    }

    private DeadlockRecord createDeadlockRecord(List<String> cycle) {
        DeadlockRecord record = new DeadlockRecord();
        record.setWaitChain(JSON.toJSONString(cycle));
        record.setAgvCount(cycle.size() - 1);
        record.setResolved(false);
        return deadlockRecordRepository.save(record);
    }

    /**
     * 手动解决单个死锁
     * 根据死锁ID查找死锁记录，检查死锁是否已解决，
     * 然后调用resolveDeadlock(DeadlockRecord)方法执行死锁恢复
     *
     * @param deadlockId 死锁记录ID
     * @return 死锁解决结果描述
     * @throws IllegalArgumentException 当死锁记录不存在时抛出
     */
    @Transactional
    public String resolveDeadlock(Long deadlockId) {
        DeadlockRecord deadlock = deadlockRecordRepository.findById(deadlockId)
                .orElseThrow(() -> new IllegalArgumentException("死锁记录不存在: " + deadlockId));

        if (Boolean.TRUE.equals(deadlock.getResolved())) {
            return "死锁已解决";
        }

        return resolveDeadlock(deadlock);
    }

    private String resolveDeadlock(DeadlockRecord deadlock) {
        List<String> waitChain = parseWaitChain(deadlock.getWaitChain());
        if (waitChain.isEmpty()) {
            return "无效的等待链";
        }

        String selectedAgvId = selectVictimAgv(waitChain);
        if (selectedAgvId == null) {
            return "无法选择牺牲AGV";
        }

        deadlock.setSelectedAgvId(selectedAgvId);

        ConflictResolutionStrategy strategy = selectResolutionStrategy(selectedAgvId, waitChain);
        deadlock.setResolutionStrategy(strategy);

        String result;
        switch (strategy) {
            case DETOUR:
                result = performDetourRecovery(selectedAgvId, waitChain);
                break;
            case REASSIGN:
                result = performReassignRecovery(selectedAgvId);
                break;
            case DEADLOCK_RECOVERY:
            default:
                result = performStandardRecovery(selectedAgvId, waitChain);
        }

        deadlock.setResolved(true);
        deadlock.setResolvedTime(LocalDateTime.now());
        deadlock.setResolutionDetail(result);
        deadlockRecordRepository.save(deadlock);

        log.info("死锁已解决: {}, 策略: {}, 结果: {}", deadlock.getId(), strategy, result);
        return result;
    }

    private List<String> parseWaitChain(String waitChainJson) {
        try {
            return JSON.parseObject(waitChainJson, List.class);
        } catch (Exception e) {
            log.error("解析等待链失败: {}", waitChainJson, e);
            return Collections.emptyList();
        }
    }

    /**
     * 选择牺牲AGV
     * 采用评分机制选择死锁中需要被牺牲的AGV，评分越低越容易被选中。
     * 评分维度包括：
     * - 任务优先级：高优先级任务加100分，中优先级加50分
     * - 任务截止时间：30分钟内截止加200分，60分钟内截止加100分
     * - 任务进度：进度百分比转换为分数（0-100分）
     * 无任务的AGV直接获得最低分（Integer.MIN_VALUE），优先被牺牲
     *
     * @param waitChain 等待链中的AGV ID列表
     * @return 被选中的牺牲AGV ID
     */
    private String selectVictimAgv(List<String> waitChain) {
        String bestCandidate = null;
        int bestScore = Integer.MAX_VALUE;

        for (String agvId : waitChain) {
            if (agvId == null || agvId.isEmpty()) {
                continue;
            }

            int score = calculateVictimScore(agvId);
            if (score < bestScore) {
                bestScore = score;
                bestCandidate = agvId;
            }
        }

        return bestCandidate;
    }

    private int calculateVictimScore(String agvId) {
        int score = 0;

        Task task = taskRepository.findFirstByAgvIdAndStatusIn(agvId,
                Arrays.asList(com.agv.dispatch.common.enums.TaskStatus.EXECUTING,
                        com.agv.dispatch.common.enums.TaskStatus.ASSIGNED)).orElse(null);

        if (task == null) {
            return Integer.MIN_VALUE;
        }

        if (task.getPriority() == TaskPriority.HIGH) {
            score += 100;
        } else if (task.getPriority() == TaskPriority.MEDIUM) {
            score += 50;
        }

        if (task.getDeadline() != null) {
            long minutesToDeadline = java.time.Duration.between(
                    LocalDateTime.now(), task.getDeadline()).toMinutes();
            if (minutesToDeadline < 30) {
                score += 200;
            } else if (minutesToDeadline < 60) {
                score += 100;
            }
        }

        Integer currentStep = task.getCurrentStep();
        if (currentStep != null) {
            List<String> path = pathPlanningService.decodePath(task.getPath());
            if (!path.isEmpty()) {
                double progress = (double) currentStep / path.size();
                score += (int) (progress * 100);
            }
        }

        return score;
    }

    private ConflictResolutionStrategy selectResolutionStrategy(String agvId, List<String> waitChain) {
        Task task = taskRepository.findFirstByAgvIdAndStatusIn(agvId,
                Arrays.asList(com.agv.dispatch.common.enums.TaskStatus.EXECUTING,
                        com.agv.dispatch.common.enums.TaskStatus.ASSIGNED)).orElse(null);

        if (task != null) {
            List<String> path = pathPlanningService.decodePath(task.getPath());
            Integer currentStep = task.getCurrentStep();
            if (currentStep == null) {
                currentStep = 0;
            }

            if (currentStep < path.size() - 1) {
                String currentPosition = path.get(currentStep);
                Set<String> avoidNodes = new HashSet<>(waitChain);

                for (String otherAgvId : waitChain) {
                    if (!otherAgvId.equals(agvId)) {
                        Task otherTask = taskRepository.findById(
                                otherAgvId.equals(path.get(currentStep)) ?
                                        task.getId() : otherAgvId).orElse(null);
                        if (otherTask != null) {
                            List<String> otherPath = pathPlanningService.decodePath(otherTask.getPath());
                            avoidNodes.addAll(otherPath);
                        }
                    }
                }

                var detourResult = pathPlanningService.planPathWithDetour(
                        currentPosition, task.getEndPoint(), avoidNodes);
                if (detourResult.isSuccess()) {
                    return ConflictResolutionStrategy.DETOUR;
                }
            }
        }

        if (task != null && task.getPriority() == TaskPriority.LOW) {
            return ConflictResolutionStrategy.REASSIGN;
        }

        return ConflictResolutionStrategy.DEADLOCK_RECOVERY;
    }

    /**
     * 执行绕行死锁恢复
     * 为牺牲AGV规划一条避开死锁等待链中其他AGV路径节点的新路径，
     * 绕行时会避开其他AGV当前位置前后的节点（otherStep-1到otherStep+3），
     * 如果绕行失败则回退到任务重分配策略。绕行成功后：
     * 1. 更新任务路径和当前步骤
     * 2. 释放旧路径锁并占用新路径
     * 3. 清除等待关系并恢复AGV为WORKING状态
     *
     * @param agvId 牺牲AGV的ID
     * @param waitChain 死锁等待链
     * @return 绕行恢复结果描述
     */
    private String performDetourRecovery(String agvId, List<String> waitChain) {
        Agv agv = agvRepository.findById(agvId).orElse(null);
        if (agv == null) {
            return "AGV不存在: " + agvId;
        }

        Task task = taskRepository.findById(agv.getCurrentTaskId()).orElse(null);
        if (task == null) {
            return "AGV无当前任务: " + agvId;
        }

        List<String> currentPath = pathPlanningService.decodePath(task.getPath());
        Integer currentStep = task.getCurrentStep();
        if (currentStep == null) {
            currentStep = 0;
        }

        String currentPosition = currentStep < currentPath.size() ?
                currentPath.get(currentStep) : task.getStartPoint();

        Set<String> avoidNodes = new HashSet<>();
        for (String otherAgvId : waitChain) {
            if (!otherAgvId.equals(agvId)) {
                Task otherTask = taskRepository.findById(
                        otherAgvId != null && !otherAgvId.isEmpty() ?
                                otherAgvId : "").orElse(null);
                if (otherTask != null) {
                    List<String> otherPath = pathPlanningService.decodePath(otherTask.getPath());
                    Integer otherStep = otherTask.getCurrentStep();
                    if (otherStep == null) {
                        otherStep = 0;
                    }
                    for (int i = Math.max(0, otherStep - 1);
                         i <= Math.min(otherStep + 3, otherPath.size() - 1); i++) {
                        avoidNodes.add(otherPath.get(i));
                    }
                }
            }
        }

        var detourResult = pathPlanningService.planPathWithDetour(
                currentPosition, task.getEndPoint(), avoidNodes);

        if (!detourResult.isSuccess()) {
            return performReassignRecovery(agvId);
        }

        List<String> newPath = new ArrayList<>();
        for (int i = 0; i < currentStep; i++) {
            newPath.add(currentPath.get(i));
        }
        newPath.addAll(detourResult.getPath());

        task.setPath(pathPlanningService.encodePath(newPath));
        task.setCurrentStep(currentStep);
        taskRepository.save(task);

        pathPlanningService.releaseAllPath(agvId);
        pathPlanningService.occupyPath(agvId, newPath, currentStep);
        pathPlanningService.clearAgvWaitingFor(agvId);

        agv.setStatus(com.agv.dispatch.common.enums.AgvStatus.WORKING);
        agvRepository.save(agv);

        log.info("死锁恢复-绕行重规划: AGV={}, 原路径长度={}, 新路径长度={}",
                agv.getAgvNo(), currentPath.size(), newPath.size());

        return String.format("AGV %s 绕行重规划成功，打破死锁", agv.getAgvNo());
    }

    /**
     * 执行任务重分配死锁恢复
     * 将牺牲AGV的当前任务重新分配给其他可用AGV，从而打破死锁循环。
     * 执行步骤：
     * 1. 释放牺牲AGV占用的所有路径锁和等待关系
     * 2. 调用任务调度服务将任务重新分配给其他AGV
     * 3. 将牺牲AGV状态设置为IDLE，清空当前任务ID
     *
     * @param agvId 牺牲AGV的ID
     * @return 任务重分配恢复结果描述
     */
    private String performReassignRecovery(String agvId) {
        Agv agv = agvRepository.findById(agvId).orElse(null);
        if (agv == null) {
            return "AGV不存在: " + agvId;
        }

        String taskId = agv.getCurrentTaskId();
        if (taskId == null) {
            return "AGV无当前任务: " + agvId;
        }

        pathPlanningService.releaseAllPath(agvId);
        pathPlanningService.clearAgvWaitingFor(agvId);

        try {
            taskDispatchService.reassignTask(taskId, null, "死锁恢复，任务重分配", "system");
        } catch (Exception e) {
            log.error("死锁恢复-重分配任务失败: taskId={}", taskId, e);
            return "任务重分配失败: " + e.getMessage();
        }

        agv.setStatus(com.agv.dispatch.common.enums.AgvStatus.IDLE);
        agv.setCurrentTaskId(null);
        agvRepository.save(agv);

        log.info("死锁恢复-任务重分配: AGV={}, taskId={}", agv.getAgvNo(), taskId);

        return String.format("AGV %s 任务已重分配，打破死锁", agv.getAgvNo());
    }

    private String performStandardRecovery(String agvId, List<String> waitChain) {
        Agv agv = agvRepository.findById(agvId).orElse(null);
        if (agv == null) {
            return "AGV不存在: " + agvId;
        }

        pathPlanningService.releaseAllPath(agvId);
        pathPlanningService.clearAgvWaitingFor(agvId);

        agv.setStatus(com.agv.dispatch.common.enums.AgvStatus.PAUSED);
        agvRepository.save(agv);

        for (String otherAgvId : waitChain) {
            if (!otherAgvId.equals(agvId)) {
                pathPlanningService.clearAgvWaitingFor(otherAgvId);
                Agv otherAgv = agvRepository.findById(otherAgvId).orElse(null);
                if (otherAgv != null &&
                        otherAgv.getStatus() == com.agv.dispatch.common.enums.AgvStatus.PAUSED) {
                    otherAgv.setStatus(com.agv.dispatch.common.enums.AgvStatus.WORKING);
                    agvRepository.save(otherAgv);
                }
            }
        }

        log.info("死锁恢复-标准策略: 暂停AGV={}, 释放其他AGV", agv.getAgvNo());

        return String.format("暂停AGV %s，其他AGV恢复运行，打破死锁", agv.getAgvNo());
    }

    private void cacheDeadlocks(List<DeadlockRecord> deadlocks) {
        if (deadlocks.isEmpty()) {
            redisTemplate.delete(DEADLOCK_KEY);
            return;
        }

        Map<String, String> deadlockData = deadlocks.stream()
                .collect(Collectors.toMap(
                        d -> d.getId().toString(),
                        d -> "等待链:" + d.getWaitChain() + ",AGV数:" + d.getAgvCount()
                ));
        redisTemplate.opsForHash().putAll(DEADLOCK_KEY, deadlockData);
    }

    /**
     * 获取所有未解决的死锁记录
     * 按创建时间倒序查询所有未解决的死锁记录，用于监控和人工干预
     *
     * @return 未解决的死锁记录列表
     */
    public List<DeadlockRecord> getUnresolvedDeadlocks() {
        return deadlockRecordRepository.findByResolvedFalseOrderByCreateTimeDesc();
    }

    /**
     * 强制解决所有未解决的死锁
     * 遍历所有未解决的死锁记录，逐个调用resolveDeadlock方法解决，
     * 异常隔离机制确保单个死锁处理失败不影响其他死锁的处理
     */
    public void resolveAllDeadlocks() {
        List<DeadlockRecord> unresolved = getUnresolvedDeadlocks();
        for (DeadlockRecord deadlock : unresolved) {
            try {
                resolveDeadlock(deadlock);
            } catch (Exception e) {
                log.error("处理死锁失败: {}", deadlock.getId(), e);
            }
        }
    }
}

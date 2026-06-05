package com.agv.dispatch.core.service;

import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.ConflictRecord;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.enums.ConflictType;
import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.repository.ConflictRecordRepository;
import com.agv.dispatch.core.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.agv.dispatch.common.constant.RedisKeyConstant.CONFLICT_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionService {

    private final AgvRepository agvRepository;
    private final TaskRepository taskRepository;
    private final ConflictRecordRepository conflictRecordRepository;
    private final PathPlanningService pathPlanningService;
    private final StringRedisTemplate redisTemplate;

    private static final double SAFE_DISTANCE = 2.0;

    public List<ConflictRecord> detectConflicts() {
        List<ConflictRecord> conflicts = new ArrayList<>();

        conflicts.addAll(detectPathConflicts());
        conflicts.addAll(detectPositionConflicts());
        conflicts.addAll(detectResourceConflicts());

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
        List<ConflictRecord> conflicts = new ArrayList<>();
        return conflicts;
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

        if (task1 == null) {
            pauseAgv(agv2);
            return "AGV2任务暂停，等待AGV1通过";
        }
        if (task2 == null) {
            pauseAgv(agv1);
            return "AGV1任务暂停，等待AGV2通过";
        }

        int priorityCompare = task1.getPriority().getCode() - task2.getPriority().getCode();
        if (priorityCompare > 0) {
            pauseAgv(agv2);
            return String.format("任务1优先级(%s)高于任务2(%s)，AGV2暂停让行",
                    task1.getPriority(), task2.getPriority());
        } else if (priorityCompare < 0) {
            pauseAgv(agv1);
            return String.format("任务2优先级(%s)高于任务1(%s)，AGV1暂停让行",
                    task2.getPriority(), task1.getPriority());
        }

        if (task1.getDeadline() != null && task2.getDeadline() != null) {
            if (task1.getDeadline().isBefore(task2.getDeadline())) {
                pauseAgv(agv2);
                return "任务1截止时间更早，AGV2暂停让行";
            } else {
                pauseAgv(agv1);
                return "任务2截止时间更早，AGV1暂停让行";
            }
        }

        int step1 = task1.getCurrentStep() != null ? task1.getCurrentStep() : 0;
        int step2 = task2.getCurrentStep() != null ? task2.getCurrentStep() : 0;
        if (step1 > step2) {
            pauseAgv(agv2);
            return "AGV1进度更快，AGV2暂停让行";
        } else {
            pauseAgv(agv1);
            return "AGV2进度更快，AGV1暂停让行";
        }
    }

    private void pauseAgv(Agv agv) {
        if (agv == null) {
            return;
        }
        agv.setStatus(com.agv.dispatch.common.enums.AgvStatus.PAUSED);
        agvRepository.save(agv);
        log.info("AGV已暂停: {}", agv.getAgvNo());
    }

    public void resolveAllConflicts() {
        List<ConflictRecord> unresolved = conflictRecordRepository.findByResolvedFalseOrderByCreateTimeDesc();
        for (ConflictRecord conflict : unresolved) {
            try {
                resolveConflict(conflict.getId());
            } catch (Exception e) {
                log.error("解决冲突失败: {}", conflict.getId(), e);
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
}

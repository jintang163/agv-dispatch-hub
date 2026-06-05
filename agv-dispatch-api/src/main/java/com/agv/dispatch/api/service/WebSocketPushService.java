package com.agv.dispatch.api.service;

import com.agv.dispatch.common.dto.TaskStatisticsVO;
import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.core.repository.AgvRepository;
import com.agv.dispatch.core.repository.TaskRepository;
import com.agv.dispatch.core.service.TaskDispatchService;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final TaskDispatchService taskDispatchService;
    private final AgvRepository agvRepository;
    private final TaskRepository taskRepository;

    @Scheduled(fixedDelay = 2000)
    public void pushTaskStatistics() {
        try {
            TaskStatisticsVO stats = taskDispatchService.getStatistics();
            messagingTemplate.convertAndSend("/topic/task/statistics", stats);
        } catch (Exception e) {
            log.debug("推送任务统计失败", e);
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void pushAgvStatus() {
        try {
            List<Agv> agvs = agvRepository.findAll();
            messagingTemplate.convertAndSend("/topic/agv/status", agvs);
        } catch (Exception e) {
            log.debug("推送AGV状态失败", e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void pushTaskQueue() {
        try {
            List<Task> queue = taskDispatchService.getPendingQueue();
            messagingTemplate.convertAndSend("/topic/task/queue", queue);
        } catch (Exception e) {
            log.debug("推送任务队列失败", e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void pushActiveTasks() {
        try {
            List<Task> activeTasks = taskRepository.findByStatusIn(
                    List.of(com.agv.dispatch.common.enums.TaskStatus.ASSIGNED,
                    com.agv.dispatch.common.enums.TaskStatus.EXECUTING));
            messagingTemplate.convertAndSend("/topic/task/active", activeTasks);
        } catch (Exception e) {
            log.debug("推送活跃任务失败", e);
        }
    }

    public void pushTaskUpdate(Task task) {
        try {
            messagingTemplate.convertAndSend("/topic/task/update", task);
        } catch (Exception e) {
            log.debug("推送任务更新失败", e);
        }
    }

    public void pushAgvUpdate(Agv agv) {
        try {
            messagingTemplate.convertAndSend("/topic/agv/update", agv);
        } catch (Exception e) {
            log.debug("推送AGV更新失败", e);
        }
    }

    public void pushAlert(String level, String message) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("level", level);
            alert.put("message", message);
            alert.put("timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/alert", JSON.toJSONString(alert));
        } catch (Exception e) {
            log.debug("推送告警失败", e);
        }
    }
}

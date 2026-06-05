package com.agv.dispatch.mqtt.scheduler;

import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.enums.AgvStatus;
import com.agv.dispatch.core.repository.AgvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatCheckScheduler {

    private final AgvRepository agvRepository;

    private static final int HEARTBEAT_TIMEOUT_MINUTES = 2;

    @Scheduled(fixedDelay = 60000)
    public void checkHeartbeat() {
        try {
            List<Agv> onlineAgvs = agvRepository.findByStatusIn(
                    List.of(AgvStatus.IDLE, AgvStatus.WORKING,
                            AgvStatus.CHARGING, AgvStatus.PAUSED));

            LocalDateTime timeoutThreshold = LocalDateTime.now()
                    .minusMinutes(HEARTBEAT_TIMEOUT_MINUTES);

            for (Agv agv : onlineAgvs) {
                if (agv.getLastHeartbeat() == null ||
                    agv.getLastHeartbeat().isBefore(timeoutThreshold)) {
                    agv.setStatus(AgvStatus.OFFLINE);
                    agvRepository.save(agv);
                    log.warn("AGV离线: agvNo={}, lastHeartbeat={}",
                            agv.getAgvNo(), agv.getLastHeartbeat());
                }
            }

        } catch (Exception e) {
            log.error("心跳检查异常", e);
        }
    }
}

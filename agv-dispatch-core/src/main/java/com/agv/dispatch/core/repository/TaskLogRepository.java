package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.TaskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskLogRepository extends JpaRepository<TaskLog, Long> {

    List<TaskLog> findByTaskIdOrderByCreateTimeDesc(String taskId);

    List<TaskLog> findByAgvIdOrderByCreateTimeDesc(String agvId);
}

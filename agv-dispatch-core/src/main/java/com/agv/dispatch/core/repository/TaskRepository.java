package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.Task;
import com.agv.dispatch.common.enums.TaskPriority;
import com.agv.dispatch.common.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    Optional<Task> findByTaskNo(String taskNo);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByAgvIdAndStatusIn(String agvId, List<TaskStatus> statuses);

    List<Task> findByStatusOrderByPriorityCodeDescDeadlineAsc(TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.status = :status " +
           "ORDER BY t.priority DESC, " +
           "CASE WHEN t.deadline IS NULL THEN 1 ELSE 0 END, " +
           "t.deadline ASC, t.createTime ASC")
    List<Task> findPendingTasksOrdered(@Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status")
    long countByStatus(@Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status " +
           "AND t.createTime >= :startTime")
    long countByStatusAndCreateTimeAfter(@Param("status") TaskStatus status,
                                         @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status " +
           "AND t.priority = :priority")
    long countByStatusAndPriority(@Param("status") TaskStatus status,
                                  @Param("priority") TaskPriority priority);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.agvId = :agvId " +
           "AND t.status IN (:statuses)")
    long countActiveTasksByAgvId(@Param("agvId") String agvId,
                                 @Param("statuses") List<TaskStatus> statuses);

    @Query("SELECT AVG(TIMESTAMPDIFF(MINUTE, t.startTime, t.completedTime)) " +
           "FROM Task t WHERE t.status = 'COMPLETED' " +
           "AND t.startTime >= :startTime")
    Double calculateAvgCompletionTime(@Param("startTime") LocalDateTime startTime);

    boolean existsByWmsOrderNo(String wmsOrderNo);

    @Query("SELECT t FROM Task t WHERE t.agvId = :agvId " +
           "AND t.status IN (:statuses) " +
           "ORDER BY t.createTime DESC")
    java.util.Optional<Task> findFirstByAgvIdAndStatusIn(@Param("agvId") String agvId,
                                                          @Param("statuses") java.util.List<com.agv.dispatch.common.enums.TaskStatus> statuses);
}

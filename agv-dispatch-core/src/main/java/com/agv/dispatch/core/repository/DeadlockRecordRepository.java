package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.DeadlockRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeadlockRecordRepository extends JpaRepository<DeadlockRecord, Long> {

    List<DeadlockRecord> findByResolvedFalseOrderByCreateTimeDesc();

    @Query("SELECT COUNT(d) FROM DeadlockRecord d WHERE d.createTime >= :startTime")
    long countByCreateTimeAfter(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(d) FROM DeadlockRecord d WHERE d.resolved = false")
    long countUnresolved();

    List<DeadlockRecord> findByCreateTimeAfterOrderByCreateTimeDesc(LocalDateTime startTime);
}

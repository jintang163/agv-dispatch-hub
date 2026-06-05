package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.ConflictRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConflictRecordRepository extends JpaRepository<ConflictRecord, Long> {

    List<ConflictRecord> findByResolvedFalseOrderByCreateTimeDesc();

    @Query("SELECT COUNT(c) FROM ConflictRecord c WHERE c.createTime >= :startTime")
    long countByCreateTimeAfter(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(c) FROM ConflictRecord c WHERE c.resolved = false")
    long countUnresolved();
}

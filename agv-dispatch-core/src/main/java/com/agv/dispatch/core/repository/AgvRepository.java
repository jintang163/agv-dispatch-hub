package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.Agv;
import com.agv.dispatch.common.enums.AgvStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgvRepository extends JpaRepository<Agv, String> {

    Optional<Agv> findByAgvNo(String agvNo);

    List<Agv> findByStatus(AgvStatus status);

    List<Agv> findByStatusIn(List<AgvStatus> statuses);

    @Query("SELECT a FROM Agv a WHERE a.status = 'IDLE' " +
           "ORDER BY a.batteryLevel DESC, a.createTime ASC")
    List<Agv> findAvailableAgvsOrdered();

    @Query("SELECT COUNT(a) FROM Agv a WHERE a.status = :status")
    long countByStatus(@Param("status") AgvStatus status);

    @Query("SELECT a FROM Agv a WHERE a.status = 'IDLE' " +
           "AND (:loadWeight IS NULL OR a.maxLoad >= :loadWeight) " +
           "ORDER BY a.batteryLevel DESC, " +
           "ABS(a.xCoord - :startX) + ABS(a.yCoord - :startY) ASC")
    List<Agv> findSuitableAgvs(@Param("loadWeight") Double loadWeight,
                               @Param("startX") Double startX,
                               @Param("startY") Double startY);

    boolean existsByAgvNo(String agvNo);
}

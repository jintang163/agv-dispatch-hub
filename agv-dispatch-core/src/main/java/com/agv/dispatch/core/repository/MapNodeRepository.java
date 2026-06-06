package com.agv.dispatch.core.repository;

import com.agv.dispatch.common.entity.MapNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MapNodeRepository extends JpaRepository<MapNode, String> {

    Optional<MapNode> findByNodeCode(String nodeCode);

    @Query("SELECT n FROM MapNode n WHERE n.isChargingStation = true")
    List<MapNode> findChargingStations();

    @Query("SELECT n FROM MapNode n WHERE n.isTransferStation = true")
    List<MapNode> findTransferStations();

    @Query("SELECT n FROM MapNode n WHERE n.isIntersection = true")
    List<MapNode> findIntersections();

    @Query("SELECT n FROM MapNode n WHERE n.isPassage = true")
    List<MapNode> findPassages();

    @Query("SELECT n FROM MapNode n WHERE n.isCriticalPoint = true")
    List<MapNode> findCriticalPoints();
}

package com.military.combat.repository;

import com.military.combat.entity.GroupCommandOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GroupCommandOrderRepository extends MongoRepository<GroupCommandOrder, String> {
    List<GroupCommandOrder> findByScenarioId(String scenarioId);
    List<GroupCommandOrder> findByScenarioIdAndStatus(String scenarioId, String status);
}

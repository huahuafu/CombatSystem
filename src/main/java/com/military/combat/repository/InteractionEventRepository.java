package com.military.combat.repository;

import com.military.combat.entity.InteractionEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InteractionEventRepository extends MongoRepository<InteractionEvent, String> {
    List<InteractionEvent> findByRound(int round);
    List<InteractionEvent> findByType(String type);
    List<InteractionEvent> findBySourceUnitId(String unitId);
    List<InteractionEvent> findByRuleId(String ruleId);
}


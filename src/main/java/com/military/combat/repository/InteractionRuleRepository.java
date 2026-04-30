package com.military.combat.repository;

import com.military.combat.entity.InteractionRule;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InteractionRuleRepository extends MongoRepository<InteractionRule, String> {

    List<InteractionRule> findByScenarioId(String scenarioId);

    List<InteractionRule> findByEnabled(boolean enabled);
    List<InteractionRule> findByType(String type);
    List<InteractionRule> findBySourceSide(String side);
    List<InteractionRule> findByEnabledTrueOrderByPriorityDesc();
}

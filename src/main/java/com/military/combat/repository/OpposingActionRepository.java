package com.military.combat.repository;

import com.military.combat.entity.OpposingAction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OpposingActionRepository extends MongoRepository<OpposingAction, String> {
    List<OpposingAction> findByScenarioId(String scenarioId);
}

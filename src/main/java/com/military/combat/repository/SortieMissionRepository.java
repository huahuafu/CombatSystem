package com.military.combat.repository;

import com.military.combat.entity.SortieMission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SortieMissionRepository extends MongoRepository<SortieMission, String> {
    List<SortieMission> findByScenarioId(String scenarioId);
    List<SortieMission> findByScenarioIdAndStatus(String scenarioId, String status);
}

package com.military.combat.repository;

import com.military.combat.simulation.commander.CommanderRunRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommanderRunRepository extends MongoRepository<CommanderRunRecord, String> {
    List<CommanderRunRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<CommanderRunRecord> findByScenarioIdOrderByCreatedAtDesc(String scenarioId, Pageable pageable);
}


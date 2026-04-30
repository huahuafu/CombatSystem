package com.military.combat.repository;

import com.military.combat.entity.CombatObjective;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CombatObjectiveRepository extends MongoRepository<CombatObjective, String> {
    List<CombatObjective> findBySide(String side);
    List<CombatObjective> findByCompleted(boolean completed);
}


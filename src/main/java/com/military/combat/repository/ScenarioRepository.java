package com.military.combat.repository;

import com.military.combat.entity.Scenario;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScenarioRepository extends MongoRepository<Scenario, String> {
    // Spring Data MongoDB 会自动实现增删改查
}
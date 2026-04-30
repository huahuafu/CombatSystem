package com.military.combat.repository;

import com.military.combat.entity.CombatUnit;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

/**
 * 数据库接口，继承 MongoRepository 即可获得增删改查功能
 */
public interface CombatUnitRepository extends MongoRepository<CombatUnit, String> {

    List<CombatUnit> findBySideAndStatus(String side, String status);

    List<CombatUnit> findByScenarioId(String scenarioId);

    void deleteByScenarioId(String scenarioId);
}

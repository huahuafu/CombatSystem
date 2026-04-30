package com.military.combat.repository;

import com.military.combat.entity.ScenarioData;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ScenarioDataRepository extends MongoRepository<ScenarioData, String> {
    List<ScenarioData> findByCampaignId(String campaignId);
}


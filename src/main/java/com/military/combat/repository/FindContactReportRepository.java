package com.military.combat.repository;

import com.military.combat.entity.FindContactReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FindContactReportRepository extends MongoRepository<FindContactReport, String> {
    List<FindContactReport> findByScenarioId(String scenarioId);
    List<FindContactReport> findByScenarioIdAndRound(String scenarioId, int round);
    List<FindContactReport> findTop200ByScenarioIdOrderByTimestampDesc(String scenarioId);
}

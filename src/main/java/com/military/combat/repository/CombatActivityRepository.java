package com.military.combat.repository;

import com.military.combat.entity.CombatActivity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Date;
import java.util.List;

public interface CombatActivityRepository extends MongoRepository<CombatActivity, String> {
    List<CombatActivity> findByScenarioId(String scenarioId);

    // 基本查询方法
    List<CombatActivity> findBySide(String side);
    List<CombatActivity> findByStatus(String status);
    List<CombatActivity> findByType(String type);
    
    // 按战役查询
    List<CombatActivity> findByCampaignId(String campaignId);
    
    // 按父子活动关系查询
    List<CombatActivity> findByParentActivityId(String parentActivityId);
    
    // 按目标查询
    List<CombatActivity> findByObjectiveId(String objectiveId);
    
    // 按时间范围查询
    @Query("{ 'startRound' : { $lte: ?0 }, 'endRound' : { $gte: ?0 } }")
    List<CombatActivity> findActiveActivitiesByRound(int currentRound);
    
    @Query("{ 'startTime' : { $lte: ?1 }, 'endTime' : { $gte: ?0 } }")
    List<CombatActivity> findActivitiesByTimeRange(Date startTime, Date endTime);
    
    // 按状态和阵营查询
    List<CombatActivity> findByStatusAndSide(String status, String side);
    
    // 按类型和阵营查询
    List<CombatActivity> findByTypeAndSide(String type, String side);
    
    // 按状态和战役查询
    List<CombatActivity> findByStatusAndCampaignId(String status, String campaignId);
    
    // 按类型和战役查询
    List<CombatActivity> findByTypeAndCampaignId(String type, String campaignId);
    
    // 复杂查询：按状态、类型和阵营
    List<CombatActivity> findByStatusAndTypeAndSide(String status, String type, String side);
    
    // 复杂查询：按战役、状态和类型
    List<CombatActivity> findByCampaignIdAndStatusAndType(String campaignId, String status, String type);
    
    // 按创建时间排序
    List<CombatActivity> findAllByOrderByCreatedAtDesc();
    
    // 按更新时间排序
    List<CombatActivity> findAllByOrderByUpdatedAtDesc();
    
    // 按开始回合排序
    List<CombatActivity> findAllByOrderByStartRoundAsc();
    
    // 按成功率排序
    List<CombatActivity> findAllByOrderBySuccessRateDesc();
    
    // 按资源消耗排序
    @Query("{}")
    List<CombatActivity> findAllByOrderByResourceConsumptionDesc();
    
    // 按单位数量查询
    @Query("{ 'unitIds.0' : { $exists: true } }")
    List<CombatActivity> findActivitiesWithUnits();
    
    // 按步骤数量查询
    @Query("{ 'steps.0' : { $exists: true } }")
    List<CombatActivity> findActivitiesWithSteps();
}


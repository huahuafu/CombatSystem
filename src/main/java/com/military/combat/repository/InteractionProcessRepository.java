package com.military.combat.repository;

import com.military.combat.entity.InteractionProcess;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 交互过程数据访问接口
 */
public interface InteractionProcessRepository extends MongoRepository<InteractionProcess, String> {
    
    /**
     * 根据规则ID查询交互过程
     */
    List<InteractionProcess> findByRuleId(String ruleId);
    
    /**
     * 根据状态查询交互过程
     */
    List<InteractionProcess> findByStatus(String status);
    
    /**
     * 根据回合查询交互过程
     */
    List<InteractionProcess> findByRound(int round);
    
    /**
     * 根据发起方查询交互过程
     */
    List<InteractionProcess> findBySourceSide(String sourceSide);
    
    /**
     * 根据目标方查询交互过程
     */
    List<InteractionProcess> findByTargetSide(String targetSide);
    
    /**
     * 根据活动ID查询交互过程
     */
    List<InteractionProcess> findByActivityId(String activityId);
    
    /**
     * 根据目标ID查询交互过程
     */
    List<InteractionProcess> findByObjectiveId(String objectiveId);
    
    /**
     * 根据规则ID和状态查询交互过程
     */
    List<InteractionProcess> findByRuleIdAndStatus(String ruleId, String status);
    
    /**
     * 根据回合和状态查询交互过程
     */
    List<InteractionProcess> findByRoundAndStatus(int round, String status);

    List<InteractionProcess> findByStatusIn(List<String> statuses);

    List<InteractionProcess> findByScenarioIdAndStatusIn(String scenarioId, List<String> statuses);
}

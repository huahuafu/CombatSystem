package com.military.combat.service;

import com.military.combat.entity.Campaign;
import com.military.combat.entity.CampaignEvent;
import com.military.combat.entity.CampaignObjective;
import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.RoundStat;
import com.military.combat.entity.ScenarioData;
import com.military.combat.repository.ScenarioDataRepository;
import com.military.combat.util.NavalDomainValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class ScenarioService {

    @Autowired
    private ScenarioDataRepository scenarioDataRepository;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CampaignConditionTemplateService conditionTemplateService;

    // 内存中的统计数据
    private List<RoundStat> statHistory = new ArrayList<>();
    private int currentRound = 0;
    private String winner;
    private String winReason;

    /** 当前工作想定（活动/规则/推演过滤与目标列表） */
    private String activeScenarioId;

    /**
     * 推演随机种子（内存会话级）。
     * <p>用于批量重放、指挥官执行等需要“同输入可复现”的场景。</p>
     */
    private Long simulationSeed;

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public List<RoundStat> getStats() {
        return statHistory;
    }

    public void setStats(List<RoundStat> stats) {
        this.statHistory = stats;
    }

    public String getActiveScenarioId() {
        return activeScenarioId;
    }

    public void setActiveScenarioId(String activeScenarioId) {
        this.activeScenarioId = activeScenarioId;
        if (activeScenarioId == null || activeScenarioId.isEmpty()) {
            this.winner = null;
            this.winReason = null;
            return;
        }
        ScenarioData data = scenarioDataRepository.findById(activeScenarioId).orElse(null);
        if (data != null) {
            this.winner = data.getWinner();
            this.winReason = data.getWinReason();
        } else {
            this.winner = null;
            this.winReason = null;
        }
    }

    public Long getSimulationSeed() {
        return simulationSeed;
    }

    public void setSimulationSeed(Long simulationSeed) {
        this.simulationSeed = simulationSeed;
    }

    public String getWinner() {
        return winner;
    }

    public String getWinReason() {
        return winReason;
    }

    public void updateWinner(String winner, String winReason) {
        this.winner = winner;
        this.winReason = winReason;
        if (activeScenarioId == null || activeScenarioId.isEmpty()) {
            return;
        }
        ScenarioData data = scenarioDataRepository.findById(activeScenarioId).orElse(null);
        if (data == null) {
            return;
        }
        data.setWinner(winner);
        data.setWinReason(winReason);
        scenarioDataRepository.save(data);
    }

    public List<CombatObjective> getObjectivesForScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.isEmpty()) {
            return new ArrayList<>();
        }
        ScenarioData data = scenarioDataRepository.findById(scenarioId).orElse(null);
        if (data == null || data.getObjectives() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(data.getObjectives());
    }

    public ScenarioData addObjectiveToScenario(String scenarioId, CombatObjective objective) {
        ScenarioData data = scenarioDataRepository.findById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("想定不存在: " + scenarioId));
        if (data.getObjectives() == null) {
            data.setObjectives(new ArrayList<>());
        }
        if (objective != null) {
            NavalDomainValidator.normalizeAndValidateObjective(objective);
            objective.setScenarioId(null);
            data.getObjectives().add(objective);
        }
        return scenarioDataRepository.save(data);
    }

    public ScenarioData removeObjectiveFromScenario(String scenarioId, String objectiveId) {
        ScenarioData data = scenarioDataRepository.findById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("想定不存在: " + scenarioId));
        if (data.getObjectives() != null && objectiveId != null) {
            data.getObjectives().removeIf(o -> objectiveId.equals(o.getId()));
        }
        return scenarioDataRepository.save(data);
    }

    /**
     * 将当前活跃想定内指定作战目标标为已完成（用于推演内占领等判定）。
     */
    public void markObjectiveCompletedInActiveScenario(String objectiveId) {
        if (objectiveId == null || objectiveId.isEmpty() || activeScenarioId == null || activeScenarioId.isEmpty()) {
            return;
        }
        ScenarioData data = scenarioDataRepository.findById(activeScenarioId).orElse(null);
        if (data == null || data.getObjectives() == null) {
            return;
        }
        for (CombatObjective o : data.getObjectives()) {
            if (objectiveId.equals(o.getId())) {
                o.setCompleted(true);
                scenarioDataRepository.save(data);
                return;
            }
        }
    }

    // --- 作战想定数据管理（合并存档功能） ---
    public ScenarioData saveScenarioData(ScenarioData data) {
        NavalDomainValidator.normalizeAndValidateScenario(data);
        if (data.getId() == null || data.getId().isEmpty()) {
            data.setId(UUID.randomUUID().toString());
        }
        if (data.getUnits() != null) {
            for (CombatUnit u : data.getUnits()) {
                if (u != null) {
                    if (u.getId() == null || u.getId().isEmpty()) {
                        u.setId(UUID.randomUUID().toString());
                    }
                    NavalDomainValidator.normalizeAndValidateUnit(u);
                    u.setScenarioId(data.getId());
                }
            }
        }
        if (data.getObjectives() != null) {
            for (CombatObjective o : data.getObjectives()) {
                if (o != null) {
                    if (o.getId() == null || o.getId().isEmpty()) {
                        o.setId(UUID.randomUUID().toString());
                    }
                    NavalDomainValidator.normalizeAndValidateObjective(o);
                }
            }
        }
        if (data.getCreateTime() == null) {
            data.setCreateTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        }
        
        // 如果保存类型为PROGRESS（进度），保存当前回合数和统计数据
        if ("PROGRESS".equals(data.getSaveType())) {
            data.setCurrentRound(this.currentRound);
            data.setStats(new ArrayList<>(this.statHistory));
            data.setWinner(this.winner);
            data.setWinReason(this.winReason);
        } else {
            // TEMPLATE（模板）和CAMPAIGN（战役模板）不保存进度数据
            data.setCurrentRound(null);
            data.setStats(null);
        }

        Campaign embedded = data.getCampaign();
        if (embedded != null) {
            campaignService.normalizeCampaign(embedded);
            validateEmbeddedCampaignConditions(embedded);
            data.setCampaignId(embedded.getId());
        }
        // 仅内嵌战役为空且未显式保留 campaignId 时清空
        if (embedded == null && (data.getCampaignId() == null || data.getCampaignId().isEmpty())) {
            data.setCampaignId(null);
        }

        return scenarioDataRepository.save(data);
    }

    private void validateEmbeddedCampaignConditions(Campaign campaign) {
        if (campaign == null) {
            return;
        }
        if (campaign.getEvents() != null) {
            for (int i = 0; i < campaign.getEvents().size(); i++) {
                CampaignEvent event = campaign.getEvents().get(i);
                if (event == null) {
                    continue;
                }
                String expr = event.getTriggerCondition();
                if (expr == null || expr.trim().isEmpty()) {
                    continue;
                }
                CampaignConditionTemplateService.ValidationResult result = conditionTemplateService.validateExpression(expr);
                if (!result.isValid()) {
                    String name = event.getName() == null ? ("#" + i) : event.getName();
                    throw new IllegalArgumentException("战役事件条件非法 [" + name + "]: " + result.getMessage());
                }
            }
        }
        if (campaign.getObjectives() != null) {
            for (int i = 0; i < campaign.getObjectives().size(); i++) {
                CampaignObjective objective = campaign.getObjectives().get(i);
                if (objective == null || objective.getTargetId() == null || objective.getTargetId().trim().isEmpty()) {
                    continue;
                }
                String tid = objective.getTargetId().trim();
                if (!conditionTemplateService.looksLikeExpression(tid)) {
                    continue;
                }
                CampaignConditionTemplateService.ValidationResult result = conditionTemplateService.validateExpression(tid);
                if (!result.isValid()) {
                    String name = objective.getName() == null ? ("#" + i) : objective.getName();
                    throw new IllegalArgumentException("战役目标条件非法 [" + name + "]: " + result.getMessage());
                }
            }
        }
    }

    /** 按战役 ID 查找其下想定（依赖 {@link com.military.combat.entity.ScenarioData#getCampaignId()} 已同步）。 */
    public List<ScenarioData> findScenariosByCampaignId(String campaignId) {
        if (campaignId == null || campaignId.isEmpty()) {
            return new ArrayList<>();
        }
        return scenarioDataRepository.findByCampaignId(campaignId);
    }
    
    public List<ScenarioData> getAllScenarioData() {
        return scenarioDataRepository.findAll();
    }
    
    public ScenarioData getScenarioDataById(String id) {
        return scenarioDataRepository.findById(id).orElse(null);
    }
    
    public void deleteScenarioData(String id) {
        scenarioDataRepository.deleteById(id);
    }
    
    // 恢复进度数据（加载PROGRESS类型的想定时调用）
    public void restoreProgress(String id) {
        ScenarioData data = scenarioDataRepository.findById(id).orElse(null);
        if (data != null && "PROGRESS".equals(data.getSaveType())) {
            if (data.getCurrentRound() != null) {
                this.currentRound = data.getCurrentRound();
            }
            if (data.getStats() != null) {
                this.statHistory = new ArrayList<>(data.getStats());
            }
            this.winner = data.getWinner();
            this.winReason = data.getWinReason();
        }
    }

    /**
     * 记录统计数据
     */
    public void recordStat(int redHp, int blueHp, int redCnt, int blueCnt) {
        statHistory.add(new RoundStat(currentRound, redHp, blueHp, redCnt, blueCnt));
    }

    /**
     * 重置统计数据
     */
    public void resetStats() {
        statHistory.clear();
        currentRound = 0;
        activeScenarioId = null;
        winner = null;
        winReason = null;
    }

    /**
     * 仅清空回合与战报统计，不修改当前工作想定（供多次连续「同想定重放」使用）。
     */
    public void clearRoundAndStatsKeepActive() {
        statHistory.clear();
        currentRound = 0;
        winner = null;
        winReason = null;
    }

    /**
     * 将想定内所有作战目标标为未完成，避免多轮批量推演中上一轮结果污染 Mongo 文档。
     */
    public void resetObjectiveCompletionFlags(String scenarioId) {
        if (scenarioId == null || scenarioId.isEmpty()) {
            return;
        }
        ScenarioData data = scenarioDataRepository.findById(scenarioId).orElse(null);
        if (data == null || data.getObjectives() == null) {
            return;
        }
        boolean dirty = false;
        for (CombatObjective o : data.getObjectives()) {
            if (o != null && o.isCompleted()) {
                o.setCompleted(false);
                dirty = true;
            }
        }
        if (dirty) {
            scenarioDataRepository.save(data);
        }
    }

    /**
     * 清除想定文档中的胜负结论（推演结束后会写入 Mongo，不清理则再次激活仍会读到旧结果）。
     */
    public void clearWinnerAndReasonInScenarioDocument(String scenarioId) {
        if (scenarioId == null || scenarioId.isEmpty()) {
            return;
        }
        ScenarioData data = scenarioDataRepository.findById(scenarioId).orElse(null);
        if (data == null) {
            return;
        }
        boolean dirty = false;
        if (data.getWinner() != null && !data.getWinner().isEmpty()) {
            data.setWinner(null);
            dirty = true;
        }
        if (data.getWinReason() != null && !data.getWinReason().isEmpty()) {
            data.setWinReason(null);
            dirty = true;
        }
        if (dirty) {
            scenarioDataRepository.save(data);
        }
    }
}

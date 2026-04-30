package com.military.combat.service;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.InteractionRule;
import com.military.combat.entity.ScenarioData;
import com.military.combat.repository.InteractionRuleRepository;
import com.military.combat.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InteractionRuleService {

    @Autowired
    private InteractionRuleRepository interactionRuleRepository;

    @Autowired
    private ScenarioService scenarioService;

    // --- 交互规则管理 ---
    public InteractionRule addInteractionRule(InteractionRule rule) {
        if (rule.getId() == null) {
            rule.setId(java.util.UUID.randomUUID().toString());
        }
        if (!rule.isEnabled()) {
            rule.setEnabled(true);
        }
        if ((rule.getScenarioId() == null || rule.getScenarioId().isEmpty())
                && scenarioService.getActiveScenarioId() != null
                && !scenarioService.getActiveScenarioId().isEmpty()) {
            rule.setScenarioId(scenarioService.getActiveScenarioId());
        }
        syncRuleCampaignIdFromScenario(rule);
        return interactionRuleRepository.save(rule);
    }

    private void syncRuleCampaignIdFromScenario(InteractionRule rule) {
        if (rule.getScenarioId() == null || rule.getScenarioId().isEmpty()) {
            return;
        }
        ScenarioData sd = scenarioService.getScenarioDataById(rule.getScenarioId());
        if (sd == null) {
            return;
        }
        if (sd.getCampaign() != null && sd.getCampaign().getId() != null) {
            rule.setCampaignId(sd.getCampaign().getId());
        } else if (sd.getCampaignId() != null) {
            rule.setCampaignId(sd.getCampaignId());
        }
    }
    
    public List<InteractionRule> getAllInteractionRules() {
        return interactionRuleRepository.findAll();
    }

    /** 编辑器列表：按想定严格过滤。 */
    public List<InteractionRule> getInteractionRulesForEditor(String scenarioId) {
        List<InteractionRule> all = interactionRuleRepository.findAll();
        if (scenarioId == null || scenarioId.isEmpty()) {
            return all;
        }
        List<InteractionRule> out = new ArrayList<>();
        for (InteractionRule r : all) {
            String sid = r.getScenarioId();
            if (scenarioId.equals(sid)) {
                out.add(r);
            }
        }
        return out;
    }
    
    public List<InteractionRule> getEnabledInteractionRules() {
        return interactionRuleRepository.findByEnabledTrueOrderByPriorityDesc();
    }

    /** 推演引擎使用：已启用且属于当前工作想定。 */
    public List<InteractionRule> getEnabledInteractionRulesForEngine() {
        List<InteractionRule> enabled = getEnabledInteractionRules();
        String active = scenarioService.getActiveScenarioId();
        if (active == null || active.isEmpty()) {
            return new ArrayList<>();
        }
        List<InteractionRule> out = new ArrayList<>();
        for (InteractionRule r : enabled) {
            String sid = r.getScenarioId();
            if (active.equals(sid)) {
                out.add(r);
            }
        }
        return out;
    }

    public InteractionRule getInteractionRuleById(String id) {
        return id == null ? null : interactionRuleRepository.findById(id).orElse(null);
    }
    
    public List<InteractionRule> getInteractionRulesByType(String type) {
        return interactionRuleRepository.findByType(type);
    }
    
    public void deleteInteractionRule(String id) {
        interactionRuleRepository.deleteById(id);
    }

    /**
     * 删除指定想定下、名称以某前缀开头的交互规则（与 {@link com.military.combat.service.AiAutoModelingService} 的 [AI] 落库策略配合）。
     */
    public int deleteRulesWithNamePrefixForScenario(String scenarioId, String namePrefix) {
        if (scenarioId == null || scenarioId.isEmpty() || namePrefix == null || namePrefix.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (InteractionRule r : interactionRuleRepository.findByScenarioId(scenarioId)) {
            if (r == null || r.getName() == null) {
                continue;
            }
            if (r.getName().startsWith(namePrefix)) {
                interactionRuleRepository.deleteById(r.getId());
                n++;
            }
        }
        return n;
    }
    
    /**
     * 应用交互规则（对启用且适用于当前想定、距离与回合、单位过滤的规则逐个生效）
     */
    public void applyInteractionRules(CombatUnit source, CombatUnit target, int currentRound) {
        for (InteractionRule rule : getEnabledInteractionRulesForEngine()) {
            if (ruleAppliesToPair(rule, source, target, currentRound)) {
                applyRuleEffect(rule, source, target);
            }
        }
    }

    /** 单位 ID 白名单：非空则仅当来源/目标在列表中才匹配 */
    private static boolean unitListsAllowPair(InteractionRule rule, CombatUnit source, CombatUnit target) {
        List<String> srcIds = rule.getSourceUnitIds();
        List<String> tgtIds = rule.getTargetUnitIds();
        boolean srcRestricted = srcIds != null && !srcIds.isEmpty();
        boolean tgtRestricted = tgtIds != null && !tgtIds.isEmpty();
        if (srcRestricted && (source.getId() == null || !srcIds.contains(source.getId()))) {
            return false;
        }
        if (tgtRestricted && (target.getId() == null || !tgtIds.contains(target.getId()))) {
            return false;
        }
        return true;
    }

    /**
     * 单条规则是否对 (source, target) 在当前回合成立（距离、阵营、回合窗、单位白名单）
     */
    public boolean ruleAppliesToPair(InteractionRule rule, CombatUnit source, CombatUnit target, int currentRound) {
        if (!unitListsAllowPair(rule, source, target)) {
            return false;
        }
        if (rule.getSourceSide() != null && !rule.getSourceSide().equals(source.getSide())) {
            return false;
        }
        if (rule.getTargetSide() != null && !rule.getTargetSide().equals(target.getSide())) {
            return false;
        }

        double distance = calculateDistance(source, target);
        if (rule.getMinDistance() > 0 && distance < rule.getMinDistance()) {
            return false;
        }
        if (rule.getMaxDistance() > 0 && distance > rule.getMaxDistance()) {
            return false;
        }

        if (currentRound < rule.getStartRound()) {
            return false;
        }
        if (rule.getEndRound() > 0 && currentRound > rule.getEndRound()) {
            return false;
        }

        return true;
    }

    public void applyMatchedRule(InteractionRule rule, CombatUnit source, CombatUnit target) {
        applyRuleEffect(rule, source, target);
    }
    
    private void applyRuleEffect(InteractionRule rule, CombatUnit source, CombatUnit target) {
        String effectType = rule.getEffectType();
        double effectValue = rule.getEffectValue();
        
        switch (effectType) {
            case "DAMAGE_BOOST":
                // 伤害加成在战斗引擎中处理
                break;
            case "SPEED_BOOST":
                source.setSpeed(source.getSpeed() * (1 + effectValue));
                break;
            case "DEFENSE_BOOST":
                // 防御加成在战斗引擎中处理
                break;
        }
    }

    /**
     * 获取交互规则模板
     */
    public List<InteractionRule> getInteractionRuleTemplates() {
        List<InteractionRule> templates = new ArrayList<>();
        
        InteractionRule coordinateAttack = new InteractionRule();
        coordinateAttack.setName("协同攻击");
        coordinateAttack.setType("COORDINATE");
        coordinateAttack.setDescription("多个单位协同攻击时，伤害提升20%");
        coordinateAttack.setEffectType("DAMAGE_BOOST");
        coordinateAttack.setEffectValue(1.2);
        coordinateAttack.setMinDistance(0);
        coordinateAttack.setMaxDistance(1000);
        coordinateAttack.setPriority(5);
        templates.add(coordinateAttack);
        
        InteractionRule flankAttack = new InteractionRule();
        flankAttack.setName("侧翼攻击");
        flankAttack.setType("COORDINATE");
        flankAttack.setDescription("从侧翼攻击时，伤害提升30%");
        flankAttack.setEffectType("DAMAGE_BOOST");
        flankAttack.setEffectValue(1.3);
        flankAttack.setMinDistance(0);
        flankAttack.setMaxDistance(500);
        flankAttack.setPriority(6);
        templates.add(flankAttack);
        
        InteractionRule fireSupport = new InteractionRule();
        fireSupport.setName("火力支援");
        fireSupport.setType("SUPPORT");
        fireSupport.setDescription("远程单位提供火力支援，友军伤害提升15%");
        fireSupport.setEffectType("DAMAGE_BOOST");
        fireSupport.setEffectValue(1.15);
        fireSupport.setMinDistance(500);
        fireSupport.setMaxDistance(2000);
        fireSupport.setPriority(4);
        templates.add(fireSupport);
        
        return templates;
    }

    private double calculateDistance(CombatUnit a, CombatUnit b) {
        // 优先使用真实地理坐标
        if (a.getLatitude() != null && a.getLongitude() != null && 
            b.getLatitude() != null && b.getLongitude() != null) {
            return GeoUtils.calculateDistance(
                a.getLatitude(), a.getLongitude(),
                b.getLatitude(), b.getLongitude()
            );
        }
        // 回退到百分比坐标系统
        return Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
    }
}

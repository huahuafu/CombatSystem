package com.military.combat.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 战役事件/目标条件模板与表达式校验服务。
 */
@Service
public class CampaignConditionTemplateService {

    private static final Set<String> SUPPORTED_OPS = new LinkedHashSet<>();

    static {
        SUPPORTED_OPS.add("SIDE_ALIVE_LE");
        SUPPORTED_OPS.add("SIDE_ALIVE_GE");
        SUPPORTED_OPS.add("UNIT_HP_BELOW");
        SUPPORTED_OPS.add("UNIT_EXISTS");
        SUPPORTED_OPS.add("UNIT_ELIMINATED");
        SUPPORTED_OPS.add("UNIT_IN_AREA");
    }

    public List<ConditionTemplateItem> listTemplates() {
        List<ConditionTemplateItem> out = new ArrayList<>();
        out.add(new ConditionTemplateItem("tpl-1", "红方在场（作战能力未崩溃）",
                "SIDE_ALIVE_GE:RED:3", "目标/事件", "阵营存活单位数 >= 3"));
        out.add(new ConditionTemplateItem("tpl-2", "蓝方减员到临界（阶段推进）",
                "SIDE_ALIVE_LE:BLUE:5", "目标/事件", "阵营存活单位数 <= 5"));
        out.add(new ConditionTemplateItem("tpl-3", "红方关键突击群受损（触发预警）",
                "UNIT_HP_BELOW:name:红方突击群:60", "事件", "指定单位血量低于阈值"));
        out.add(new ConditionTemplateItem("tpl-4", "蓝方防御核心仍在（防御目标未失）",
                "UNIT_EXISTS:name:蓝方防御核心", "目标", "指定单位仍存活"));
        out.add(new ConditionTemplateItem("tpl-5", "蓝方防御核心被歼灭（突破成功）",
                "UNIT_ELIMINATED:name:蓝方防御核心", "目标", "指定单位被歼灭"));
        out.add(new ConditionTemplateItem("tpl-6", "红方进入桥头堡区域（占领开始）",
                "UNIT_IN_AREA:RED:31.120:31.180:121.440:121.520", "事件/目标", "阵营单位进入区域"));
        out.add(new ConditionTemplateItem("tpl-7", "蓝方反突击进入争夺区（交火升级）",
                "UNIT_IN_AREA:BLUE:31.120:31.180:121.440:121.520", "事件", "阵营单位进入区域"));
        out.add(new ConditionTemplateItem("tpl-8", "蓝方机动兵力低于阈值（转入追歼）",
                "SIDE_ALIVE_LE:BLUE:2", "目标", "阵营存活单位数 <= 2"));
        out.add(new ConditionTemplateItem("tpl-9", "红方主力存活不足（失败条件）",
                "SIDE_ALIVE_LE:RED:1", "目标", "阵营存活单位数 <= 1"));
        out.add(new ConditionTemplateItem("tpl-10", "指定编号单位被摧毁（精确目标）",
                "UNIT_ELIMINATED:id:unit-001", "目标", "指定 ID 单位被歼灭"));
        return out;
    }

    public Set<String> supportedOps() {
        return SUPPORTED_OPS;
    }

    public boolean looksLikeExpression(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            return false;
        }
        String op = expr.split(":")[0].trim();
        return SUPPORTED_OPS.contains(op);
    }

    public ValidationResult validateExpression(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            return ValidationResult.invalid("表达式为空");
        }
        String[] p = expr.trim().split(":");
        String op = p[0].trim();
        if (!SUPPORTED_OPS.contains(op)) {
            return ValidationResult.invalid("不支持的操作符: " + op);
        }
        try {
            if ("SIDE_ALIVE_LE".equals(op) || "SIDE_ALIVE_GE".equals(op)) {
                if (p.length != 3) return ValidationResult.invalid(op + " 需要 3 段");
                Integer.parseInt(p[2]);
                return ValidationResult.valid();
            }
            if ("UNIT_HP_BELOW".equals(op)) {
                if (p.length != 4) return ValidationResult.invalid(op + " 需要 4 段");
                if (!isSelectorType(p[1])) return ValidationResult.invalid("selectorType 仅支持 id/name/side");
                Integer.parseInt(p[3]);
                return ValidationResult.valid();
            }
            if ("UNIT_EXISTS".equals(op) || "UNIT_ELIMINATED".equals(op)) {
                if (p.length != 3) return ValidationResult.invalid(op + " 需要 3 段");
                if (!isSelectorType(p[1])) return ValidationResult.invalid("selectorType 仅支持 id/name/side");
                return ValidationResult.valid();
            }
            if ("UNIT_IN_AREA".equals(op)) {
                if (p.length != 6) return ValidationResult.invalid(op + " 需要 6 段");
                Double.parseDouble(p[2]);
                Double.parseDouble(p[3]);
                Double.parseDouble(p[4]);
                Double.parseDouble(p[5]);
                return ValidationResult.valid();
            }
            return ValidationResult.invalid("未识别表达式");
        } catch (Exception ex) {
            return ValidationResult.invalid("参数格式错误: " + ex.getMessage());
        }
    }

    private boolean isSelectorType(String selectorType) {
        return "id".equals(selectorType) || "name".equals(selectorType) || "side".equals(selectorType);
    }

    public static class ConditionTemplateItem {
        private String id;
        private String name;
        private String expression;
        private String usage;
        private String description;

        public ConditionTemplateItem(String id, String name, String expression, String usage, String description) {
            this.id = id;
            this.name = name;
            this.expression = expression;
            this.usage = usage;
            this.description = description;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getExpression() { return expression; }
        public String getUsage() { return usage; }
        public String getDescription() { return description; }
    }

    public static class ValidationResult {
        private boolean valid;
        private String message;

        public static ValidationResult valid() {
            ValidationResult r = new ValidationResult();
            r.valid = true;
            r.message = "OK";
            return r;
        }

        public static ValidationResult invalid(String message) {
            ValidationResult r = new ValidationResult();
            r.valid = false;
            r.message = message;
            return r;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}

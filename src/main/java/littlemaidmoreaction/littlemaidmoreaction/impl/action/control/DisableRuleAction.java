package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 禁用指定规则 (v11 P5) — 规则间互控核心。
 *
 * <p>与 enable_rule 配对使用:
 *   规则 A 触发 → 禁用自身 → 启用规则 B, 实现链式任务编排。
 */
@RuleAction
public final class DisableRuleAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("rule_id", "规则ID", 0)
    );

    @Override public String id() { return "disable_rule"; }
    @Override public String displayName() { return "禁用规则"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> rawParams) {
        int ruleId = parseInt(rawParams.get("rule_id"), -1);
        if (ruleId < 0) return;

        var rules = new ArrayList<>(RuleActionStorage.getRules());
        for (int i = 0; i < rules.size(); i++) {
            RuleDef r = rules.get(i);
            if (r.id() == ruleId && r.enabled()) {
                rules.set(i, new RuleDef(r.id(), r.name(), false, r.eventId(),
                    r.chance(), r.cooldown(), r.priority(), r.matchMode(),
                    r.conditions(), r.actions(), r.compat()));
                RuleActionStorage.replaceRules(rules);
                LittleMaidMoreAction.LOGGER.info("[Rule] 规则 #{} '{}' 已禁用 (由规则引擎触发)", ruleId, r.name());
                return;
            }
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}

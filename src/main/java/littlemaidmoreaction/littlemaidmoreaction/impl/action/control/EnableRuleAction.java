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
 * 启用指定规则 (v11 P5) — 规则间互控核心。
 *
 * <p>从规则引擎内部启用/禁用其他规则，实现:
 *   "规则 A 触发 → 启用规则 B → 规则 B 接管下一步"
 *   或 "任务完成 → 禁用自身 + 启用下一个任务规则"
 */
@RuleAction
public final class EnableRuleAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("rule_id", "规则ID", 0)
    );

    @Override public String id() { return "enable_rule"; }
    @Override public String displayName() { return "启用规则"; }
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
            if (r.id() == ruleId && !r.enabled()) {
                rules.set(i, new RuleDef(r.id(), r.name(), true, r.eventId(),
                    r.chance(), r.cooldown(), r.priority(), r.matchMode(),
                    r.conditions(), r.actions(), r.compat()));
                RuleActionStorage.replaceRules(rules);
                LittleMaidMoreAction.LOGGER.info("[Rule] 规则 #{} '{}' 已启用 (由规则引擎触发)", ruleId, r.name());
                return;
            }
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}

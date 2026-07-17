package littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 任务超时检测 — 任务卡在某个步骤超过指定 tick 数时返回 true (v10)。
 *
 * <p>从 PersistentData 读取 lmma_flow_tick (最后更新时间戳)，
 * 与当前 gameTime 比较。超时后可触发 fail_task 或重试逻辑。</p>
 */
@RuleCondition
public final class TaskTimeoutCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("timeout_ticks", "超时tick", 200),
        new TypedParam.StringParam("task_type", "任务类型", "craft_chain")
    );

    @Override public String key() { return "task_timeout"; }
    @Override public String displayName() { return "任务超时"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        var data = ctx.maid().getPersistentData();
        String currentTask = data.getString("lma_flow_task");
        String expected = rawParams.getOrDefault("task_type", "craft_chain");
        if (!currentTask.equals(expected)) return "false";

        long lastTick = data.getLong("lma_flow_tick");
        long currentTick = ctx.maid().level().getGameTime();
        int timeout = parseInt(rawParams.get("timeout_ticks"), 200);

        return (currentTick - lastTick > timeout) ? "true" : "false";
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}

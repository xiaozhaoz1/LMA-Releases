package littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskKeys;

import java.util.List;
import java.util.Map;

/**
 * 检查流程任务当前状态 (v44: TaskKeys 常量化)。
 */
@RuleCondition
public final class FlowTaskStateCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.SelectParam("state", "期望状态", "in_progress",
            List.of("queued", "in_progress", "completed", "failed"))
    );

    @Override public String key() { return "flow_task_state"; }
    @Override public String displayName() { return "流程任务状态"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return ctx.maid().getPersistentData().getString(TaskKeys.FLOW_STATE);
    }
}

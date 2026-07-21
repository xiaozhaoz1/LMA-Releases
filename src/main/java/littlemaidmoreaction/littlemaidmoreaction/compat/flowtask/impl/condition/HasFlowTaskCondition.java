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
 * 女仆有指定类型的流程任务 (v44: TaskKeys 常量化)。
 */
@RuleCondition
public final class HasFlowTaskCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("task_type", "任务类型", "craft_chain")
    );

    @Override public String key() { return "has_flow_task"; }
    @Override public String displayName() { return "有流程任务"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        var data = ctx.maid().getPersistentData();
        String currentTask = data.getString(TaskKeys.FLOW_TASK);
        String expected = rawParams.getOrDefault("task_type", "craft_chain");
        return currentTask.equals(expected) ? "true" : "false";
    }
}

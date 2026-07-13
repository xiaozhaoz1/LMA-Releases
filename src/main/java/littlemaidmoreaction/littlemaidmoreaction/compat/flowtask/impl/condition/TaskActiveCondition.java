package littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.api.AbstractTaskCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;

/**
 * 检查指定任务是否活跃 (v10)。AI 通过 task_type 参数指定任务类型。
 * 等价于 has_flow_task 但支持 expected_state + expected_step 参数。
 */
@RuleCondition
public final class TaskActiveCondition extends AbstractTaskCondition {
    @Override public String key() { return "task_active"; }
    @Override public String displayName() { return "任务活跃"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
}

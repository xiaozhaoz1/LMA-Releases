package littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.api;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 流程任务条件抽象基类 (v10 系统三)。
 *
 * <h3>设计目的</h3>
 * 封装 PersistentData 读写，子类只需声明 task_type 即可创建新任务条件。
 * AI 通过 lma_start_task + 指定 task_type + target 即可创建新任务。
 *
 * <h3>PersistentData Schema</h3>
 * <pre>
 *   lmma_flow_task   (String)  — 当前活跃任务类型
 *   lmma_flow_state  (String)  — queued / in_progress / completed / failed
 *   lmma_flow_step   (int)     — 当前步骤编号 (0-based)
 *   lmma_flow_tick   (long)    — 最后更新时间戳
 * </pre>
 *
 * <h3>快速创建新任务条件</h3>
 * <pre>{@code
 * @RuleCondition
 * public class MyTaskCondition extends AbstractTaskCondition {
 *     @Override public String key() { return "my_task_active"; }
 *     @Override public String displayName() { return "我的任务进行中"; }
 * }
 * // AI 使用时: {"key":"my_task_active", "params":{"task_type":"my_task"}}
 * }</pre>
 */
public abstract class AbstractTaskCondition implements ICondition {

    protected static final List<TypedParam<?>> TASK_PARAMS = List.of(
        new TypedParam.StringParam("task_type", "任务类型", "altar_craft"),
        new TypedParam.StringParam("task_id", "任务ID", "0"),
        new TypedParam.SelectParam("expected_state", "期望状态", "in_progress",
            List.of("any", "queued", "in_progress", "completed", "failed", "stopped")),
        new TypedParam.IntParam("expected_step", "期望步骤", -1)
    );

    @Override
    public final ConditionValueType valueType() { return ConditionValueType.BOOL; }

    @Override
    public List<TypedParam<?>> params() { return TASK_PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        var data = ctx.maid().getPersistentData();
        String currentTask = data.getString("lma_flow_task");
        String expected = rawParams.getOrDefault("task_type", "altar_craft");

        // 任务类型不匹配
        if (!currentTask.equals(expected)) return "false";

        // 检查任务 ID
        String expectedId = rawParams.getOrDefault("task_id", "0");
        String currentId = data.getString("lma_flow_task_id");
        if (!currentId.equals(expectedId)) return "false";

        // 检查期望状态
        String expectedState = rawParams.getOrDefault("expected_state", "in_progress");
        if (!"any".equals(expectedState)) {
            String currentState = data.getString("lma_flow_state");
            if (!currentState.equals(expectedState)) return "false";
        }

        // 检查期望步骤 (-1 = 不检查)
        int expectedStep = parseInt(rawParams.get("expected_step"), -1);
        if (expectedStep >= 0) {
            int currentStep = data.getInt("lma_flow_step");
            if (currentStep != expectedStep) return "false";
        }

        return "true";
    }

    protected static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}

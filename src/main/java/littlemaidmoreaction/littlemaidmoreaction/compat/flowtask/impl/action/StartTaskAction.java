package littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.impl.action;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaFlowTask;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskDispatcher;

import java.util.List;
import java.util.Map;

/**
 * v18: 规则动作启动任务 — 与 AI lma_start_task 等价。
 *
 * <p>GUI 用法: 规则中添加此动作，设置 task_type + target，
 * 规则触发时直接写入 PersistentData 并切换 Brain 执行。</p>
 *
 * <pre>
 * event: maid_interact
 * condition: owner_holding_item :=: minecraft:stick
 * action: start_task(task_type=craft_chain, target=minecraft:stick)
 * </pre>
 */
@RuleAction
public final class StartTaskAction implements IAction {

    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.SelectParam("task_type", "任务类型", "craft_chain",
            List.of("craft_chain", "furnace", "jukebox", "bell_ring")),
        new TypedParam.StringParam("target", "目标物品ID", "minecraft:stick"),
        new TypedParam.IntParam("target_count", "目标数量(-1=无限)", -1)
    );

    @Override public String id() { return "start_task"; }
    @Override public String displayName() { return "开始任务(v18)"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> params) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return;

        String taskType = params.getOrDefault("task_type", "craft_chain");
        String target = params.getOrDefault("target", "");
        int targetCount = parseInt(params.getOrDefault("target_count", "-1"), -1);

        // v43: 通过中央调度器提交 (替代手动8字段NBT写入)
        LmaFlowTask.savePreviousTask(maid);
        if (!TaskDispatcher.submit(maid, taskType, target, targetCount)) {
            LmaFlowTask.restorePreviousTask(maid);
            LittleMaidMoreAction.LOGGER.warn("[V18] [StartTaskAction] validation failed for {}", taskType);
            return;
        }
        LittleMaidMoreAction.LOGGER.info("[V18] [StartTaskAction] task '{}' started via rule, target={}", taskType, target);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}

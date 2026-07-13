package littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.impl.action;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.LmaFlowTask;
import littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskOrchestrator;

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
            List.of("craft_chain", "furnace", "jukebox", "bell_ring", "altar_craft")),
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

        // 1. 写 PersistentData
        var data = maid.getPersistentData();
        String oldTask = data.getString("lma_flow_task");
        if (!oldTask.isEmpty() && !oldTask.equals(taskType)) {
            LmaFlowTask.restorePreviousTask(maid);
        }
        String sharedTaskId = String.valueOf(System.currentTimeMillis() % 100000);
        data.putString("lma_flow_task", taskType);
        data.putString("lma_flow_task_id", sharedTaskId);
        data.putString("lma_flow_state", "in_progress");
        data.putInt("lma_flow_step", 0);
        data.putInt("lma_flow_max_count", targetCount > 0 ? targetCount : 0);
        data.putInt("lma_flow_counter", 0);
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        data.remove("lma_flow_cached");
        LmaFlowTask.savePreviousTask(maid);

        // 2. 验证材料 + 切换 Brain
        PipelineResult result = TaskOrchestrator.validate(maid, taskType, sharedTaskId, target, targetCount);
        if (!result.completed()) {
            data.remove("lma_flow_task"); data.remove("lma_flow_task_id");
            data.remove("lma_flow_state"); data.remove("lma_flow_step");
            LmaFlowTask.restorePreviousTask(maid);
            LittleMaidMoreAction.LOGGER.warn("[V18] [StartTaskAction] validation failed: {}", result.feedback());
            return;
        }

        data.remove("lma_flow_cached");
        var newTask = LmaTaskTypeRegistry.findByTaskType(taskType);
        maid.setTask(newTask);
        LittleMaidMoreAction.LOGGER.info("[V18] [StartTaskAction] task '{}' started via rule, target={}", taskType, target);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}

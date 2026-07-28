package littlemaidmoreaction.littlemaidmoreaction.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys;

/**
 * v64: 查询任务状态 — AI可检查当前任务是否运行/完成/失败.
 *
 * <p>AI调用 lma_query_task 后获得完整任务状态, 可据此向玩家反馈进度。
 */
public final class QueryTaskTool implements ITool<QueryTaskTool.Params> {

    public record Params(boolean detailed) {
        @SuppressWarnings("unused")
        static Codec<Params> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("detailed", false).forGetter(Params::detailed)
        ).apply(i, Params::new));
    }

    @Override public String id() { return "lma_query_task"; }

    @Override
    public String summary(EntityMaid maid) {
        return "Query the current LMA task status. Call this BEFORE starting a new task " +
               "to check if a task is already running, and AFTER starting a task to verify it began. " +
               "Returns: task type, state, progress, target item, elapsed time.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties("detailed", com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.BoolParameter.create()
                .setDescription("If true, returns full step/phase details"), false);
        return root;
    }

    @Override
    public Codec<Params> codec() { return Params.CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Params p, LLMCallback cb) {
        EntityMaid maid = cb.getMaid();
        var data = maid.getPersistentData();
        String task = data.getString(TaskKeys.FLOW_TASK);

        if (task.isEmpty()) {
            return cb.addToolResult("idle — no LMA task is currently running", toolCallId);
        }

        String state = FlowTaskData.getState(maid);
        int counter = data.getInt(TaskKeys.FLOW_COUNTER);
        int max = data.getInt(TaskKeys.FLOW_MAX_COUNT);
        String target = data.getString(TaskKeys.TASK_TARGET);
        long tick = FlowTaskData.getTick(maid);
        long now = maid.level().getGameTime();

        StringBuilder sb = new StringBuilder();
        sb.append("task=").append(task);
        sb.append(" state=").append(state.isEmpty() ? "unknown" : state);
        if (!target.isEmpty()) sb.append(" target=").append(target);
        sb.append(" progress=").append(counter);
        if (max > 0) sb.append("/").append(max);
        sb.append(" elapsed=").append(now - tick).append("t (").append((now - tick) / 20).append("s)");

        if (p.detailed()) {
            var handler = TaskRegistry.get(task);
            if (handler != null) {
                sb.append(" steps=[");
                var steps = handler.pipeline().steps();
                for (int i = 0; i < steps.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(steps.get(i).label());
                }
                sb.append("]");
                sb.append(" longRunning=").append(handler.pipeline().isLongRunning());
            }
        }

        // 状态解读
        if (TaskKeys.STATE_CANCELLED.equals(state)) {
            sb.append(" WARNING: task was cancelled!");
        } else if (TaskKeys.STATE_FAILED.equals(state)) {
            String reason = data.getString(TaskKeys.FAIL_REASON);
            sb.append(" FAILED");
            if (!reason.isEmpty()) sb.append(" reason=").append(reason);
            sb.append(" — tell the owner the task failed and suggest retrying or checking materials");
        } else if (TaskKeys.STATE_COMPLETED.equals(state)) {
            sb.append(" COMPLETED — tell the owner the task finished successfully!");
        } else if (TaskKeys.STATE_IN_PROGRESS.equals(state) && now - tick > 200) {
            sb.append(" NOTE: task is in_progress but no heartbeat for ").append((now - tick) / 20)
              .append("s — the maid may not be near the workstation, or the workstation is missing");
        }

        LittleMaidMoreAction.LOGGER.info("[QueryTaskTool] status: {}", sb);
        return cb.addToolResult(sb.toString(), toolCallId);
    }

    @Override
    public String invocationSummary(Params p) {
        return "lma_query_task" + (p.detailed() ? " detailed" : "");
    }
}

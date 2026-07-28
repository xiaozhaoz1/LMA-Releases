package littlemaidmoreaction.littlemaidmoreaction.ai.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys;

/**
 * 任务状态上下文 — 按需查询 (v64).
 *
 * <p>promptContext=false, LLM 通过 {@code query_game_context("lma_task")} 查询。
 * 返回当前任务类型、状态、计数器、步骤、环境感知等详细信息。
 */
public final class MaidTaskContext {

    public static final String CATEGORY = "lma_task";
    private static final String SUMMARY =
        "LMA task system status: current task type, state, progress, step details.";

    private MaidTaskContext() {}

    public static void registerAll(GameContextRegister register) {
        register.registerCategory(CATEGORY, SUMMARY, false);
        register.registerContext(CATEGORY, new CurrentTaskContext());
        register.registerContext(CATEGORY, new TaskProgressContext());
    }

    /** 当前任务摘要 */
    private static final class CurrentTaskContext extends AbstractMaidContext {
        private CurrentTaskContext() { super("lma_current_task", "Current LMA task"); }

        @Override
        public String getValue(EntityMaid maid) {
            var data = maid.getPersistentData();
            String task = data.getString(TaskKeys.FLOW_TASK);
            if (task.isEmpty()) return "idle (no LMA task active)";

            String state = data.getString(TaskKeys.FLOW_STATE);
            int counter = data.getInt(TaskKeys.FLOW_COUNTER);
            int max = data.getInt(TaskKeys.FLOW_MAX_COUNT);
            String target = data.getString(TaskKeys.TASK_TARGET);

            StringBuilder sb = new StringBuilder();
            sb.append("task=").append(task);
            sb.append(" state=").append(state.isEmpty() ? "unknown" : state);
            if (!target.isEmpty()) sb.append(" target=").append(target);
            sb.append(" progress=").append(counter);
            if (max > 0) sb.append("/").append(max);
            return sb.toString();
        }
    }

    /** 任务步骤详情 */
    private static final class TaskProgressContext extends AbstractMaidContext {
        private TaskProgressContext() { super("lma_task_progress", "LMA task step progress"); }

        @Override
        public String getValue(EntityMaid maid) {
            var data = maid.getPersistentData();
            String task = data.getString(TaskKeys.FLOW_TASK);
            if (task.isEmpty()) return "no active task";

            var handler = TaskRegistry.get(task);
            if (handler == null) return "task=" + task + " (unknown type)";

            String state = FlowTaskData.getState(maid);
            int counter = data.getInt(TaskKeys.FLOW_COUNTER);
            int max = data.getInt(TaskKeys.FLOW_MAX_COUNT);
            long tick = FlowTaskData.getTick(maid);
            long now = maid.level().getGameTime();

            StringBuilder sb = new StringBuilder();
            sb.append("task=").append(handler.taskType());
            sb.append(" state=").append(state.isEmpty() ? "unknown" : state);
            sb.append(" count=").append(counter);
            if (max > 0) sb.append("/").append(max);
            sb.append(" elapsed=").append(now - tick).append("t");
            sb.append(" steps=[");
            var steps = handler.pipeline().steps();
            for (int i = 0; i < steps.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(steps.get(i).label());
            }
            sb.append("]");
            return sb.toString();
        }
    }
}

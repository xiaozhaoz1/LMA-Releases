package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 任务管道抽象 — 每个任务类型一个独立实现 (v52: 移除废弃的 execute()) */
public interface TaskPipeline {
    String taskType();
    /**
     * v43.1: 纯验证 — 必须无副作用。检查材料/目标/前置条件，不修改任何状态。
     * 子类必须覆写。默认返回 ok (允许执行)。
     */
    default PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");
    }

    /** v35.2: 任务子步骤声明 (供任务树 GUI 展示) */
    default List<TaskStep> steps() { return List.of(); }

    /**
     * v43.2: 中断回调 — 持续执行器必须覆写。
     * TaskDispatcher.cancel() 调用此方法通知管线停止。
     */
    default void interrupt(EntityMaid maid) {}

    /**
     * v44: 清理钩子 — 恢复该任务修改过的女仆状态。
     * 各Pipeline实现自己知道改了哪些状态(homeMode/pickupType/导航等)。
     * TaskDispatcher在终结任务时调用。
     */
    default void onCleanup(EntityMaid maid) {}

    /**
     * v44: 是否为长运行任务 — 需要调度器自动心跳。
     * true → 调度器每tick自动调 TaskStateManager.heartbeat()。
     */
    default boolean isLongRunning() { return false; }

    /** v45: 失败/超时后重试策略。默认不重试。 */
    default RetryPolicy retryPolicy() { return RetryPolicy.NEVER; }

    /**
     * v52: 目标方块判断 — 供 Brain shouldMoveTo 使用。
     * 返回 false 表示无目标方块（原地执行，如 altar_craft/env_sense）。
     * 各 Pipeline 自己决定找什么方块，注册层不再指定。
     */
    default boolean isTargetBlock(ServerLevel world, BlockPos pos, BlockState state) { return false; }

    /** 任务子步骤 */
    record TaskStep(String id, String label, StepType type, List<String> dependsOn) {}

    enum StepType { COLLECT, CRAFT, INTERACT, DELIVER, WAIT }
}

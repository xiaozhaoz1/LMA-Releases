package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.ProgressNotifier;
import net.minecraft.server.level.ServerLevel;

/**
 * 敲钟管道 — 处理女仆自动寻找并敲响钟的工作流。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>写入规则文件（触发后续行为）</li>
 *   <li>TODO: Phase 2 — 寻找钟 → 导航 → 敲钟</li>
 * </ol>
 *
 * <p>Phase 2 将实现完整的导航和敲钟循环。当前为骨架实现。</p>
 */
public final class BellRingPipeline implements TaskPipeline {

    @Override
    public String taskType() {
        return "bell_ring";
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return execute(level, maid, ctx);
    }

    @Override
    public PipelineResult execute(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        LittleMaidMoreAction.LOGGER.info("[V16] [BellRing] ====== START: target={}", ctx.target());

        // TODO Phase 2: find bell → navigate → ring

        // ★ v18: Brain Behavior 直接执行，不写规则文件
        String taskId = ctx.taskId();
        ProgressNotifier.notify(maid, ProgressNotifier.BELL_DONE);
        LittleMaidMoreAction.LOGGER.info("[V16] [BellRing] ====== END: {}", ProgressNotifier.BELL_DONE);
        return PipelineResult.ok(ProgressNotifier.BELL_DONE);
    }
}

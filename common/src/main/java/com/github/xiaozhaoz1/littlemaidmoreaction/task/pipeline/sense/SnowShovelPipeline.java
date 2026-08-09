package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvScanner;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSenseBroadcaster;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Set;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;

/**
 * v63: 雪天铲雪被动任务。
 *
 * <p>信号: SNOWING → 扫描雪层 → 逐个清除。
 * 雪清完或天气转晴自动停止。
 */
public final class SnowShovelPipeline implements TaskPipeline {

    @Override public String taskType() { return "snow_shovel"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean needsGameTick() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("", Set.of(Signals.ENV_SNOWING));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, String signal) {
        if (!Signals.ENV_SNOWING.equals(signal)) return;
        if (TaskKeys.STATE_IN_PROGRESS.equals(
                maid.getPersistentData().getString(TaskKeys.passiveKey(taskType())))) {
            return; // 已运行
        }
        // 轻量预检：附近真的有雪？
        List<BlockPos> snow = EnvScanner.scanSnowBlocks(
                (ServerLevel) maid.level(), maid.blockPosition(),
                PassiveTaskConfig.ENV_DEFAULT_RADIUS.get());
        if (snow.isEmpty()) return;
        TaskDispatcher.submitPassive(maid, taskType());
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        var pd = pipelineData(maid);
        int cd = pd.getInt("Cd") - 1;
        if (cd > 0) { pd.putInt("Cd", cd); return; }

        int radius = PassiveTaskConfig.ENV_DEFAULT_RADIUS.get();
        List<BlockPos> snow = EnvScanner.scanSnowBlocks(world, maid.blockPosition(), radius);

        if (snow.isEmpty()) {
            TaskDispatcher.cancelPassive(maid, taskType());
            return;
        }

        // 清除最近的一块雪
        world.destroyBlock(snow.get(0), true, maid);
        pd.putInt("Cd", 40); // 2秒一块
    }
    // onCleanup 用接口默认 (clearPipelineData) — v67.3 删除冗余覆写
}

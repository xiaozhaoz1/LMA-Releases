package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.PassiveSignalSkeleton;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;

/**
 * v63: 温度自适应被动任务。
 *
 * <p>COLD → 寻找篝火/岩浆, 导航 → 目标。
 * HOT  → 寻找水源, 导航 → 目标。
 * NORMAL → 停止。
 *
 * <p>所有状态存 pipelineData (lma_pl_temp_adapt) — clearPipelineData 自动清理。
 */
public final class TempAdaptPipeline implements PassiveSignalSkeleton, TaskConfigurable {

    @Override public String taskType() { return "temp_adapt"; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return okSignals(Set.of(Signals.ENV_TEMP_COLD, Signals.ENV_TEMP_HOT, Signals.ENV_TEMP_NORMAL));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, String signal) {
        var pd = pipelineData(maid);
        switch (signal) {
            case Signals.ENV_TEMP_COLD -> {
                pd.putString("goal", "warm");
                TaskDispatcher.submitPassive(maid, taskType());
            }
            case Signals.ENV_TEMP_HOT -> {
                pd.putString("goal", "cool");
                TaskDispatcher.submitPassive(maid, taskType());
            }
            case Signals.ENV_TEMP_NORMAL -> TaskDispatcher.cancelPassive(maid, taskType());
        }
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        var pd = pipelineData(maid);
        String goal = pd.getString("goal");
        if (goal.isEmpty()) { TaskDispatcher.cancelPassive(maid, taskType()); return; }

        // v79.61x S3: 扫描节奏改 ThrottleUtil 时戳节流 (原 "Cd" 递减自管); 间隔存 pipelineData
        // (默认 100, 无目标时 600 背压) — 行为与原先 CD=100/600 语义一致
        int interval = pd.getInt("interval");
        if (interval <= 0) interval = 100;
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                .shouldFire(maid, "temp_adapt", interval)) {
            return;
        }

        int radius = PassiveTaskConfig.ENV_DEFAULT_RADIUS.get();
        BlockPos center = maid.blockPosition();
        BlockPos target = switch (goal) {
            case "warm" -> findHeatSource(world, center, radius);
            case "cool" -> findWaterSource(world, center, radius);
            default -> null;
        };

        if (target != null) {
            pd.putInt("interval", 100);
            maid.getNavigation().moveTo(target.getX() + 0.5, target.getY(),
                    target.getZ() + 0.5, 0.8);
        } else {
            pd.putInt("interval", 600);
        }
    }
    // onCleanup 用接口默认 (clearPipelineData) — 删除冗余覆写

    private static BlockPos findHeatSource(ServerLevel world, BlockPos center, int radius) {
        int vert = 4;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        List<BlockPos> candidates = new java.util.ArrayList<>();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int y = -vert; y <= vert; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    mp.set(cx + x, cy + y, cz + z);
                    var s = world.getBlockState(mp);
                    // 2026-08-11c 冗余清理 (全景 #13): 原裸 CAMPFIRE 匹配覆盖 LIT 分支 —
                    // 修 LIT-only (灭的篝火不热, 行为修正)
                    if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)
                            || (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT))
                            || (s.is(Blocks.SOUL_CAMPFIRE) && s.getValue(CampfireBlock.LIT))
                            || s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK)) {
                        candidates.add(mp.immutable());
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static BlockPos findWaterSource(ServerLevel world, BlockPos center, int radius) {
        int vert = 4;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        List<BlockPos> candidates = new java.util.ArrayList<>();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int y = -vert; y <= vert; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    mp.set(cx + x, cy + y, cz + z);
                    var s = world.getBlockState(mp);
                    if (s.is(Blocks.WATER) && s.getFluidState().isSource()) {
                        candidates.add(mp.immutable());
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }
}

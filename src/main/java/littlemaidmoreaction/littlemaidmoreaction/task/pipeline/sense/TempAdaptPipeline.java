package littlemaidmoreaction.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.runtime.TaskDispatcher;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSignal;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * v63: 温度自适应被动任务。
 *
 * <p>COLD → 寻找篝火/岩浆, 导航 → 目标。
 * HOT  → 寻找水源, 导航 → 目标。
 * NORMAL → 停止。
 *
 * <p>所有状态存 pipelineData (lma_pl_temp_adapt) — clearPipelineData 自动清理。
 */
public final class TempAdaptPipeline implements TaskPipeline {

    @Override public String taskType() { return "temp_adapt"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean needsGameTick() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("", Set.of(EnvSignal.TEMP_COLD, EnvSignal.TEMP_HOT, EnvSignal.TEMP_NORMAL));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, EnvSignal signal) {
        var pd = pipelineData(maid);
        switch (signal) {
            case TEMP_COLD -> {
                pd.putString("goal", "warm");
                TaskDispatcher.submitPassive(maid, taskType());
            }
            case TEMP_HOT -> {
                pd.putString("goal", "cool");
                TaskDispatcher.submitPassive(maid, taskType());
            }
            case TEMP_NORMAL -> TaskDispatcher.cancelPassive(maid, taskType());
        }
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        var pd = pipelineData(maid);
        String goal = pd.getString("goal");
        if (goal.isEmpty()) { TaskDispatcher.cancelPassive(maid, taskType()); return; }

        int cd = pd.getInt("Cd") - 1;
        if (cd > 0) { pd.putInt("Cd", cd); return; }
        pd.putInt("Cd", 100);

        int radius = MoreActionConfig.ENV_DEFAULT_RADIUS.get();
        BlockPos center = maid.blockPosition();
        BlockPos target = switch (goal) {
            case "warm" -> findHeatSource(world, center, radius);
            case "cool" -> findWaterSource(world, center, radius);
            default -> null;
        };

        if (target != null) {
            maid.getNavigation().moveTo(target.getX() + 0.5, target.getY(),
                    target.getZ() + 0.5, 0.8);
        } else {
            pd.putInt("Cd", 600);
        }
    }

    @Override
    public void onCleanup(EntityMaid maid) {
        clearPipelineData(maid);
    }

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
                    if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)
                            || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)
                            || (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT))
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

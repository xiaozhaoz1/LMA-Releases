package littlemaidmoreaction.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.task.runtime.TaskDispatcher;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvScanner;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSignal;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;

import java.util.List;
import java.util.Set;

/**
 * v63: 日出日落自动开关灯被动任务。
 *
 * <p>信号: DAY_NIGHT_CHANGE → 扫描红石灯 → 切换 lit 状态。
 * 天亮关灯，天黑开灯。一次信号执行一次全扫描后自动停止。
 */
public final class LightControlPipeline implements TaskPipeline {

    @Override public String taskType() { return "light_control"; }
    @Override public boolean isLongRunning() { return false; }
    @Override public boolean needsGameTick() { return false; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("", Set.of(EnvSignal.DAY_NIGHT_CHANGE));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, EnvSignal signal) {
        if (signal != EnvSignal.DAY_NIGHT_CHANGE) return;
        if (!(maid.level() instanceof ServerLevel world)) return;

        boolean isDay = snap.world() != null && snap.world().day();
        int radius = MoreActionConfig.ENV_DEFAULT_RADIUS.get();
        List<BlockPos> lamps = EnvScanner.scanRedstoneLamps(world, maid.blockPosition(), radius);

        for (BlockPos pos : lamps) {
            var state = world.getBlockState(pos);
            if (!state.is(Blocks.REDSTONE_LAMP)) continue;
            boolean lit = state.getValue(RedstoneLampBlock.LIT);
            if (isDay && lit) {
                world.setBlock(pos, state.setValue(RedstoneLampBlock.LIT, false), 3);
            } else if (!isDay && !lit && canPower(world, pos)) {
                world.setBlock(pos, state.setValue(RedstoneLampBlock.LIT, true), 3);
            }
        }
    }

    /** 简单检查红石灯是否可通电（无红石线直连=不可控，跳过） */
    private static boolean canPower(ServerLevel world, BlockPos pos) {
        return world.hasNeighborSignal(pos);
    }
}

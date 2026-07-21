package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * 女仆搅拌 — Basin MIXING 配方 (v42: extends TimerBasedCreatePipeline)。
 */
public final class MixPipeline extends TimerBasedCreatePipeline {

    public MixPipeline() { super("lma_mix_timer", "lma_mix_target"); }

    @Override public String taskType() { return "mix"; }
    @Override public List<TaskPipeline.TaskStep> steps() { return List.of(new TaskStep("mix", "搅拌混合", StepType.INTERACT, List.of())); }

    @Override
    protected BlockPos findTarget(ServerLevel world, BlockPos maidPos) {
        return MixService.findBasin(world, maidPos);
    }

    @Override
    protected boolean hasRecipe(ServerLevel world, BlockPos target) {
        return MixService.hasRecipe(world, target);
    }

    @Override
    protected void tickWorking(ServerLevel world, EntityMaid maid, CompoundTag d, int timer) {
        if (timer % 20 == 0) {
            maid.swing(InteractionHand.MAIN_HAND);
            BlockPos target = readPos(d, keyTarget);
            if (target != null) MixService.playMixSound(world, target);
        }
        timer--;
        if (timer <= 0) {
            BlockPos target = readPos(d, keyTarget);
            if (target != null) MixService.executeMix(world, target);
            d.remove(keyTarget);
        }
        d.putInt(keyTimer, timer);
    }
}

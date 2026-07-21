package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * 女仆冲压 — Depot/Basin 双路 (v42: extends TimerBasedCreatePipeline)。
 */
public final class PressPipeline extends TimerBasedCreatePipeline {

    public PressPipeline() { super("lma_press_timer", "lma_press_target"); }

    @Override public String taskType() { return "press"; }
    @Override public List<TaskPipeline.TaskStep> steps() { return List.of(new TaskStep("press", "冲压塑形", StepType.INTERACT, List.of())); }

    @Override
    protected BlockPos findTarget(ServerLevel world, BlockPos maidPos) {
        return PressService.findTarget(world, maidPos);
    }

    @Override
    protected boolean hasRecipe(ServerLevel world, BlockPos target) {
        boolean depot = world.getBlockEntity(target) instanceof DepotBlockEntity;
        return depot ? PressService.hasDepotRecipe(world, target)
                     : PressService.hasBasinRecipe(world, target);
    }

    @Override
    protected void tickWorking(ServerLevel world, EntityMaid maid, CompoundTag d, int timer) {
        if (timer % 20 == 0) maid.swing(InteractionHand.MAIN_HAND);
        timer--;
        if (timer <= 0) {
            BlockPos target = readPos(d, keyTarget);
            if (target != null) {
                boolean depot = world.getBlockEntity(target) instanceof DepotBlockEntity;
                if (depot) {
                    var h = PressService.readHeldItem(world, target);
                    PressService.findPressingRecipe(world, h)
                        .ifPresent(r -> PressService.executeDepotPress(world, target, r));
                } else {
                    PressService.executeBasinPress(world, target);
                }
                PressService.playPressSound(world, target);
            }
            d.remove(keyTarget);
        }
        d.putInt(keyTimer, timer);
    }
}

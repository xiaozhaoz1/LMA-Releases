package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import littlemaidmoreaction.littlemaidmoreaction.api.navigation.NavigationMemory;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlock;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlockEntity;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 女仆跑步发电管线 v62 — pipelineData 管理私有状态.
 */
public final class RunningBeltPipeline implements TaskPipeline {
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean needsGameTick() { return true; }
    @Override public void onCleanup(EntityMaid maid) { cleanup(maid); TaskPipeline.super.onCleanup(maid); }
    @Override public void interrupt(EntityMaid maid) {
        maid.setSprinting(false);
        onCleanup(maid);
    }

    private static final int IDLE_TIMEOUT = 100;
    private static final int FOOD_INTERVAL = 100;
    private static final int COOLDOWN_TICKS = 60;
    private static final float SPRINT_SPEED = 0.2f;

    @Override public String taskType() { return "running_belt"; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("run", "跑步发电", StepType.INTERACT, List.of())); }
    @Override public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) { return PipelineResult.ok(""); }

    public IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) { tick(w, m); return TaskResult.CONTINUE; }
        };
    }

    // ── Tick ──

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        if (TaskKeys.STATE_CANCELLED.equals(FlowTaskData.getState(maid))) { cleanup(maid); return; }
        CompoundTag pd = pipelineData(maid);

        if ("true".equals(pd.getString("converted"))) {
            tickRunning(world, maid, pd);
        } else {
            tickSearching(world, maid, pd);
        }
    }

    private static void tickSearching(ServerLevel world, EntityMaid maid, CompoundTag pd) {
        int cd = pd.getInt("cooldown");
        if (cd > 0) { pd.putInt("cooldown", cd - 1); return; }

        BlockPos beltPos = null;
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos p = maid.blockPosition().offset(0, dy, 0);
            if (isHorizontalBelt(world.getBlockState(p))) { beltPos = p.immutable(); break; }
        }
        if (beltPos == null) return;
        if (RunningBeltService.findFoodItem(maid) == null) return;

        if (RunningBeltService.convertToMaidPowerBelt(world, beltPos)) {
            pd.putString("target", beltPos.toShortString());
            pd.putString("converted", "true");
            pd.putInt("idle", 0);
            pd.putInt("foodTimer", 0);
            pd.putInt("cooldown", 0);
        }
    }

    private static void tickRunning(ServerLevel world, EntityMaid maid, CompoundTag pd) {
        BlockPos target = readPos(pd.getString("target"));
        if (target == null) { revertAndClear(world, maid, pd); return; }

        if (!RunningBeltService.isMaidOnBelt(maid, target)) {
            int idle = pd.getInt("idle") + 1;
            pd.putInt("idle", idle);
            if (idle >= IDLE_TIMEOUT) { revertAndClear(world, maid, pd); }
            return;
        }

        int foodTimer = pd.getInt("foodTimer") + 1;
        if (foodTimer >= FOOD_INTERVAL) {
            var food = RunningBeltService.findFoodItem(maid);
            if (food == null) { revertAndClear(world, maid, pd); return; }
            RunningBeltService.consumeFood(maid, food.slotIndex());
            foodTimer = 0;
        }
        pd.putInt("foodTimer", foodTimer);

        pd.putInt("idle", 0);
        maid.setSprinting(true);
        MaidPowerBeltBlockEntity be = MaidPowerBeltBlock.getControllerBE(world, target);
        if (be != null) be.addSurfaceMovement(SPRINT_SPEED);
    }

    // ── 清理 ──

    public void cleanup(EntityMaid maid) {
        maid.setSprinting(false);
        if (!(maid.level() instanceof ServerLevel world)) return;
        revertAndClear(world, maid, pipelineData(maid));
        NavigationMemory.clearAllNav(maid);
    }

    private static void revertAndClear(ServerLevel world, EntityMaid maid, CompoundTag pd) {
        maid.setSprinting(false);
        BlockPos target = readPos(pd.getString("target"));
        if (target != null) RunningBeltService.revertToRegularBelt(world, target);
        pd.putString("converted", "false");
        pd.remove("target"); pd.remove("idle"); pd.remove("foodTimer");
        pd.putInt("cooldown", COOLDOWN_TICKS);
    }

    private static BlockPos readPos(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) { return null; }
    }

    private static boolean isHorizontalBelt(BlockState state) {
        return state.getBlock() instanceof BeltBlock
                && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
    }
}

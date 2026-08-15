package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BlockTargetNavigation;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlock;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlockEntity;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 女仆跑步发电管线 v62 — pipelineData 管理私有状态.
 */
public final class RunningBeltPipeline implements TaskPipeline, TaskConfigurable {
    @Override public boolean isLongRunning() { return true; }
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

    // executor/execute 删除 (v79.45) — 执行全归 GMPM tick 驱动

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
        BlockPos target = BlockTargetNavigation.parseTarget(pd.getString("target"));
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

        // 原地锚定 — 防随机走动 (WALK_TARGET 原地) + home 模式 (防跟玩家; 用户裁定不做 restrictTo 范围)
        // 强锚定 — 停导航 + 杀遗留 LMA 路径 (PathExecutor.sweep 无任务门控, 旧路径会驱动女仆走出)
        NavigationUtil.keepAlive(world, maid);
        maid.getNavigation().stop();
        com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi.clearNav(maid);
        maid.setHomeModeEnable(true);

        // 顺带摇周围 2 格内曲柄 (最多 2 个 — 跑步不移动, 就近摇; 发电上报式不中断)
        var cranks = CrankService.findCranks(world, maid.blockPosition(), 2, 2);
        if (!cranks.isEmpty() && world.getGameTime() % 20 == 0) {
            maid.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        for (BlockPos c : cranks) {
            CrankService.crank(world, c);
        }
    }

    // ── 清理 ──

    public void cleanup(EntityMaid maid) {
        maid.setSprinting(false);
        maid.setHomeModeEnable(false);   // 恢复 home 模式 (跑步时强制开启)
        if (!(maid.level() instanceof ServerLevel world)) return;
        revertAndClear(world, maid, pipelineData(maid));
        NavigationMemory.clearAllNav(maid);
    }

    private static void revertAndClear(ServerLevel world, EntityMaid maid, CompoundTag pd) {
        maid.setSprinting(false);
        BlockPos target = BlockTargetNavigation.parseTarget(pd.getString("target"));
        if (target != null) RunningBeltService.revertToRegularBelt(world, target);
        pd.putString("converted", "false");
        pd.remove("target"); pd.remove("idle"); pd.remove("foodTimer");
        pd.putInt("cooldown", COOLDOWN_TICKS);
    }

    private static boolean isHorizontalBelt(BlockState state) {
        return state.getBlock() instanceof BeltBlock
                && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
    }
}

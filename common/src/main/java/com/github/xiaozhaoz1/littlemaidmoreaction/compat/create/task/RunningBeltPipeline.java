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

    /** v79.62.1 卸载兜底注册 — 女仆死亡/移除/离开世界时还原残留发电皮带 (静态块自登记, 防 core 依赖) */
    static {
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.MaidUnloadRegistry.register(RunningBeltPipeline::onMaidUnload);
    }

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
        // 取消检查已删 (2026-08-16 实证: cancel 同帧 clearAll — 不可达; cleanup 归终结路径 onCleanup)
        CompoundTag pd = pipelineData(maid);

        // v79.62.1 残留皮带还原 (用户方案 C+A): PD target 只记脚下皮带, 进游戏后每 tick 检查 —
        // 若 target 处是发电皮带但女仆不在跑步 (converted!=true, 如世界重启后任务未启动/中途打断)
        // → revertToRegularBelt(anchor) 从 anchor 反走找 controller 还原整条链 (旁边格扩散).
        // 正在跑步 (converted=true) 不检查 — 女仆在用.
        if (!"true".equals(pd.getString("converted"))) {
            BlockPos anchor = BlockTargetNavigation.parseTarget(pd.getString("target"));
            if (anchor != null && MaidPowerBeltBlock.isMaidPowerBelt(world.getBlockState(anchor))) {
                RunningBeltService.revertToRegularBelt(world, anchor);
                pd.remove("target");
            }
        }

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
        // v79.61x: 缺皮带/缺食物静默 → 600t 节流气泡
        if (beltPos == null) {
            if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                    .shouldFire(maid, "running_belt_no_belt", 600)) {
                com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi
                        .showFail(maid, "脚下没有水平皮带");
            }
            return;
        }
        if (RunningBeltService.findFoodItem(maid) == null) {
            if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                    .shouldFire(maid, "running_belt_no_food", 600)) {
                com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi
                        .showFail(maid, "背包里没有食物");
            }
            return;
        }

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
        if (be != null) {
            // v79.62.1 女仆身体朝向皮带移动方向 (模拟在跑步机上跑步, 不随玩家转身)
            // 注意: getMovementFacing() = 物品运输方向 (皮带表面向后滚 → 人面朝其反方向,
            // 同现实跑步机). 女仆面朝 getMovementFacing().getOpposite().
            net.minecraft.core.Direction moveDir = be.getMovementFacing().getOpposite();
            float yaw = moveDir.toYRot();
            maid.setYRot(yaw);
            maid.setYHeadRot(yaw);
            maid.yBodyRot = yaw;
            // v79.62.1 蛋糕加成 (用户裁定 0→1024 / 1→2048 / 2→4096): 直接设最终输出
            // 蛋糕检测: 女仆前方 1~3 格 × 横向 ±1 (蛋糕放女仆面前地上)
            int cakes = Math.min(MaidPowerBeltBlock.countCakesAround(world, maid.blockPosition(), moveDir), 2);
            float rpm = cakes == 0 ? 96f : (cakes == 1 ? 192f : 256f);
            float stress = cakes == 0 ? 1024f : (cakes == 1 ? 2048f : 4096f);
            be.setDirectOutput(rpm, stress);
        }

        // 原地锚定 — 防随机走动 (WALK_TARGET 原地) + home 模式 (防跟玩家; 用户裁定不做 restrictTo 范围)
        // 强锚定 — 停导航 + 杀遗留 LMA 路径 (PathExecutor.sweep 无任务门控, 旧路径会驱动女仆走出)
        NavigationUtil.keepAlive(world, maid);
        maid.getNavigation().stop();
        com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi.clearNav(maid);
        maid.setHomeModeEnable(true);

        // 顺带摇周围 2 格内曲柄 (最多 2 个 — 跑步不移动, 就近摇; 发电上报式不中断)
        var cranks = CrankService.findCranks(world, maid.blockPosition(), 2, 2);
        if (!cranks.isEmpty()) {
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.MaidSwing.onInterval(maid, 20);
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

    /** 女仆卸载兜底 (v79.62.1, MaidUnloadRegistry 登记) — 女仆死亡/被移除/离开世界时
     *  还原残留的发电皮带. 读 PD target (脚下皮带锚点), 若仍是发电皮带则还原整条链
     *  (revertToRegularBelt 从 anchor 反走找 controller). 幂等 — 非发电皮带静默. */
    public static void onMaidUnload(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel world)) return;
        CompoundTag pd = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, "running_belt");
        BlockPos anchor = BlockTargetNavigation.parseTarget(pd.getString("target"));
        if (anchor == null) return;
        if (MaidPowerBeltBlock.isMaidPowerBelt(world.getBlockState(anchor))) {
            RunningBeltService.revertToRegularBelt(world, anchor);
            pd.remove("target");
        }
    }

    private static void revertAndClear(ServerLevel world, EntityMaid maid, CompoundTag pd) {
        maid.setSprinting(false);
        BlockPos target = BlockTargetNavigation.parseTarget(pd.getString("target"));
        // v79.62.1: target 作为残留锚点保留 — revertToRegularBelt 成功还原后由残留检查删
        // (若还原失败 (chunk 未加载等), 下次 tick 残留检查还会用 target 重试)
        if (target != null && RunningBeltService.revertToRegularBelt(world, target)) {
            pd.remove("target");
        }
        pd.putString("converted", "false");
        pd.remove("idle"); pd.remove("foodTimer");
        pd.putInt("cooldown", COOLDOWN_TICKS);
    }

    private static boolean isHorizontalBelt(BlockState state) {
        return state.getBlock() instanceof BeltBlock
                && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
    }
}

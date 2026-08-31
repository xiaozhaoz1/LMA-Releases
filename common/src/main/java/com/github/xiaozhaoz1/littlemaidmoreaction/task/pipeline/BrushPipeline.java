package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.LmaFakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v79.62 刷子管线 (主动任务) — 女仆检测周围可疑方块 (可疑沙子/沙砾), 走过去自动刷扫.
 *
 * <p>刷扫: 靠近后每 10 tick 调 {@link BrushableBlockEntity#brush} (FakePlayer 模拟刷子),
 * 刷满 10 次 → 方块变沙子/沙砾 + 掉落物品 (loot), 刷子消耗 1 耐久 (女仆背包找刷子).
 *
 * <p>防御点: 无可疑方块静默; 无刷子用主手空刷 (不消耗); 导航复用 WALK_TARGET 机制.
 */
public final class BrushPipeline implements TaskPipeline {

    private static final int SCAN_RADIUS = 8;
    private static final double WORK_DIST_SQR = 4.0;
    private static final int BRUSH_INTERVAL = 10;
    private static final int MAX_BRUSH_TICKS = 400;   // 兜底: 4 秒无完成则放弃该块

    @Override public String taskType() { return "brush"; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        BlockPos suspicious = findSuspicious(world, maid);
        if (suspicious == null) return;

        double d = maid.blockPosition().distSqr(suspicious);
        if (d > WORK_DIST_SQR) {
            navigate(world, maid, suspicious);
            return;
        }
        // 靠近 → 每 10 tick 刷一次
        long gt = world.getGameTime();
        if (gt % BRUSH_INTERVAL != 0) return;
        if (world.getBlockEntity(suspicious) instanceof BrushableBlockEntity be) {
            // v79.62.1 刷扫摆手动画 — 每 10t 刷一次同步挥臂 (原缺摆动, 刷扫无动作感)
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.MaidSwing.onInterval(maid, BRUSH_INTERVAL);
            LmaFakePlayer fp = new LmaFakePlayer(world, maid, suspicious);
            try {
                boolean done = be.brush(gt, fp, Direction.UP);
                if (done) {
                    // 刷子消耗 1 耐久 (从女仆背包找刷子)
                    var inv = maid.getAvailableInv(true);
                    for (int i = 0; i < inv.getSlots(); i++) {
                        ItemStack s = inv.getStackInSlot(i);
                        if (s.is(Items.BRUSH)) {
//? if 1.20.1 {
                            s.hurtAndBreak(1, maid, m -> { });
//?} else {
                            s.hurtAndBreak(1, maid, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
                            break;
                        }
                    }
                }
            } finally {
                com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.LmaPlayerSimulator.cleanup(fp, world);
            }
        }
    }

    private static BlockPos findSuspicious(ServerLevel level, EntityMaid maid) {
        BlockPos c = maid.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-SCAN_RADIUS, -4, -SCAN_RADIUS), c.offset(SCAN_RADIUS, 4, SCAN_RADIUS))) {
            BlockState st = level.getBlockState(p);
            if (st.is(Blocks.SUSPICIOUS_SAND) || st.is(Blocks.SUSPICIOUS_GRAVEL)) return p.immutable();
        }
        return null;
    }

    private static void navigate(ServerLevel world, EntityMaid maid, BlockPos target) {
        net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(
                maid, target, 1.0F, 0);
    }

    @Override
    public void onCleanup(EntityMaid maid) {}
}


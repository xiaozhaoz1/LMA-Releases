package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v79.26.8d: 卡方块自救协调器 (maid_useful_task {@code MaidSelfRescueBehavior} 移植)。
 *
 * <p>女仆被埋 (沙/砂砾塌落、挖穿脚下时上方方块塌下) → AABB 与窒息实心块相交 →
 * 瞬破清除 (TLM 导航对卡方块无能为力, 不挖会一直卡着 + 窒息掉血)。
 * 判定对齐 maid_useful_task {@code isNotSafeAndCanTryToDestroy}: 非空气 + 碰撞体非空 +
 * AABB 相交 + isSuffocating (窒息块) + canDestroyBlock (TLM 内建破坏门, 基岩/保护不可挖)。
 * 检查脚格 + 脚上一格 (女仆高 1.7, 头可能卡进上方格)。
 *
 * <p>LMA 管线适配: 无 TLM Brain 行为注入 — 由 ChainHarvestExecute 每 tick 入口调用,
 * 自救优先于一切动作 (导航/垫柱/开脉); 挖掉后 AABB 不再相交, 自然收敛无需节流。
 * 挖掘链复用 ChainHarvestExecute 工具链 (ensureToolFor 按方块类型换合适工具 —
 * 被埋常见石头/泥土/沙 → 镐/铲; destroyBlock 瞬破 + hurtAndBreak 消耗, charge 同款)。
 */
public final class SelfRescueCoordinator {

    private SelfRescueCoordinator() {}

    /** 每 tick 自救检查 — 卡住 → 挖掉窒息块 → true (调用方跳过本 tick 其余逻辑) */
    public static boolean tick(ServerLevel world, EntityMaid maid) {
        if (!ActiveTaskConfig.CHAIN_SELF_RESCUE.get()) return false;
        BlockPos pos = maid.blockPosition();
        if (tryDig(world, maid, pos)) return true;
        return tryDig(world, maid, pos.above());
    }

    /** 单格: 判定 (对齐 maid_useful_task isNotSafeAndCanTryToDestroy) + 瞬破 */
    private static boolean tryDig(ServerLevel world, EntityMaid maid, BlockPos pos) {
        BlockState bs = world.getBlockState(pos);
        if (bs.isAir()) return false;
        VoxelShape collision = bs.getCollisionShape(world, pos);
        if (collision.isEmpty()) return false;
        if (!maid.getBoundingBox().intersects(collision.bounds().move(pos))) return false;
        if (!bs.isSuffocating(world, pos)) return false;
        if (!maid.canDestroyBlock(pos)) return false;
        // 按方块类型换合适工具 (手工具已合适不动); 挖掉后 AABB 不再相交, 下轮自然收敛
        ChainHarvestExecute.ensureToolFor(maid, bs);
        ItemStack tool = maid.getMainHandItem();
        if (!maid.destroyBlock(pos)) return false;
        if (!tool.isEmpty() && tool.isDamageableItem()) {
//? if 1.20.1 {
            tool.hurtAndBreak(1, maid,
                    e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
//?} else {
            tool.hurtAndBreak(1, maid, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
        }
        maid.swing(InteractionHand.MAIN_HAND);
        return true;
    }
}

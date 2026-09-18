package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v79.26.7: 垂直挖穿协调器 — TLM 导航不破坏方块, 地下目标需挖脚下开路。
 * 从 ChainHarvestExecute 内联逻辑提取 (v79.19o 垂直)。
 *
 * <p>v79.26.8e: 用户裁定简化 ("只要挖上下能挖到的就行了") — 面前挖穿 (digFront)
 * 删除, 只保留垂直挖穿; 向下深度默认 3→6 (用户 "向下的可以多挖几格")。
 *
 * <p>v79.57: 用户裁定退役脚下挖穿 (digVertical) — 下挖挖泥土换主手铲 → 主手非镐 →
 * ORE 管线工具判断卡住 (用户实测); 女仆只挖裸露表面矿, 头顶 ≤6 格裸露矿保留
 * digUp 挖穿 (用户裁定), 连锁采集不受影响。
 *
 * <p>返回 boolean = 本 tick 是否挖了方块 (调用方 CONTINUE); false = 条件不适用
 * 或挖失败 (液体/基岩/事件取消) — 调用方走正常流程 (导航/跳过), 不死循环。
 *
 * <p>v79.61x 定级 (两轴表): 无类内跨 tick 状态 (每 tick 挖一格, 状态在调用方) +
 * 导航域业务语义 → 单拍执行器; 留 execute (api/pathing 经本类挖穿 — 搬 task/service
 * 会 api 层→task 层依赖倒挂)。
 */
public final class DigThroughCoordinator {

    private DigThroughCoordinator() {}

    /**
     * 向上挖穿 — 目标在头顶 (深度 ≤ CHAIN_DIG_DOWN_DEPTH 且 水平 ≤ ARRIVE_DIST_SQR
     * 且 TLM 不可达) → 自下而上挖矿正下方整列 (含矿本体): 先挖路径石头, 最后挖到矿,
     * 掉落物沿挖穿通道落到女仆脚边 — 背包满时掉落物也躺脚边, 不卡在矿下方石头上。
     * 破坏方块无 reach 限制 (服务端 destroyBlock), 头顶最高 6 格可直挖。
     * <p>v79.58: 门控改 {@link PathingApi#canReachAround} (不含目标正下方列) —
     * 头顶正上矿的女仆脚下恒可达, canReachNear (含正下方) 会误判"走正常路" → 门控
     * 恒通过 → digUp 恒拒绝 → 死循环。
     */
    public static boolean digUp(ServerLevel world, EntityMaid maid, BlockPos target) {
        BlockPos foot = maid.blockPosition();
        int hx = target.getX() - foot.getX();
        int hz = target.getZ() - foot.getZ();
        if (target.getY() <= foot.getY()
                || target.getY() - foot.getY() > ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get()
                || hx * hx + hz * hz > VanillaConstants.ARRIVE_DIST_SQR
                || PathingApi.canReachAround(maid, target)) {
            return false;
        }
        // 自下而上挖目标列最低实心格 (每 tick 一格) — 路径石头先挖通, 最后挖到矿本体
        for (int y = foot.getY() + 1; y <= target.getY(); y++) {
            BlockPos p = new BlockPos(target.getX(), y, target.getZ());
            BlockState bs = world.getBlockState(p);
            if (!bs.isAir()) {
                return digOne(world, maid, p, bs);
            }
        }
        return false;
    }

    /** 挖一格: 非空气/非基岩/非流体源 + 可破坏 → 目标驱动换工具 → destroyBlock (+耐久 +挥臂) */
    static boolean digOne(ServerLevel world, EntityMaid maid, BlockPos pos, BlockState bs) {
        if (bs.isAir() || bs.is(net.minecraft.world.level.block.Blocks.BEDROCK)
                || bs.getFluidState().isSource() || !maid.canDestroyBlock(pos)) {
            return false;
        }
        ChainHarvestExecute.ensureToolFor(maid, bs);
        ItemStack tool = maid.getMainHandItem();
        // destroyBlock 失败 (事件取消等) → false → 调用方回退 (不死循环)
        if (!maid.destroyBlock(pos)) {
            return false;
        }
        // ★ v79.63.3 ④: 女仆自己挖穿也清附近跳过集 (地形变了 — 用户裁定)
        ChainHarvestExecute.invalidateSkipsNear(pos, 8);
//? if 1.20.1 {
        tool.hurtAndBreak(1, maid, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
//?} else {
        tool.hurtAndBreak(1, maid, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
        maid.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /**
     * **强制向上挖穿** (v79.63.3, 用户实测「墙上的矿」): 与 {@link #digUp} 同逻辑,
     * 但**不检查** {@code canReachAround} 门 — 供"走路已失败一次"的兜底调用 (那时已证明走不过去)。
     * 只保留"目标在上方 + 深度内 + 水平相邻"三个几何前提。
     */
    public static boolean digUpForce(ServerLevel world, EntityMaid maid, BlockPos target) {
        BlockPos foot = maid.blockPosition();
        int hx = target.getX() - foot.getX();
        int hz = target.getZ() - foot.getZ();
        if (target.getY() <= foot.getY()
                || target.getY() - foot.getY() > ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get()
                || hx * hx + hz * hz > VanillaConstants.ARRIVE_DIST_SQR) {
            return false;
        }
        for (int y = foot.getY() + 1; y <= target.getY(); y++) {
            BlockPos p = new BlockPos(target.getX(), y, target.getZ());
            BlockState bs = world.getBlockState(p);
            if (!bs.isAir()) {
                return digOne(world, maid, p, bs);
            }
        }
        return false;
    }

    /**
     * **清"矿正下方那一列"到女仆脚高度** (v79.63.4 用户裁定) — 修「墙上的矿挖掉了但掉落物停在墙沿, 收不到」。
     *
     * <p>为什么需要: 矿石悬在她上方 (如嵌在墙面/半空) 且**下方是实心**时, 掉落物会停在下面那块方块上
     * (离她 >2 格 ⇒ 拾取范围外 ⇒ **挖了等于白挖**)。清掉矿到脚之间的那一列 ⇒ 掉落物落到她脚边 ✓。
     *
     * <p>只在"矿石高于她脚 且 高差 ≤ CHAIN_DIG_DOWN_DEPTH"时动手; 从矿下一格往下逐格清到脚上方;
     * 遇到空气 (已有通道 ⇒ 掉落能落下来) 或挖不动的方块 (基岩/液体源/canDestroyBlock 拒绝) 立即停止 ✓ 不死循环。
     */
    public static void clearFallColumn(ServerLevel world, EntityMaid maid, BlockPos orePos) {
        BlockPos foot = maid.blockPosition();
        int dy = orePos.getY() - foot.getY();
        if (dy <= 0 || dy > ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get()) return;
        for (int y = orePos.getY() - 1; y > foot.getY(); y--) {
            BlockPos p = new BlockPos(orePos.getX(), y, orePos.getZ());
            BlockState bs = world.getBlockState(p);
            if (bs.isAir()) break;                 // 已有通道 ⇒ 掉落自然能落下来
            if (!digOne(world, maid, p, bs)) break; // 挖不动 ⇒ 放弃 (不死循环)
        }
    }
}

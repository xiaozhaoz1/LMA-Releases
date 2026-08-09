package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

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
 * <p>返回 boolean = 本 tick 是否挖了方块 (调用方 CONTINUE); false = 条件不适用
 * 或挖失败 (液体/基岩/事件取消) — 调用方走正常流程 (导航/跳过), 不死循环。
 */
public final class DigThroughCoordinator {

    private DigThroughCoordinator() {}

    /**
     * 垂直挖穿 — 目标在脚下 (深度 ≤ CHAIN_DIG_DOWN_DEPTH 且 水平 ≤ ARRIVE_DIST_SQR 且
     * TLM 不可达) → 挖女仆脚下列逐层下到矿层: 女仆每 tick 挖脚下格掉落, 落至矿层
     * 站在洞里, 矿同层水平偏移 ≤2.83 由 idleScan 直接开脉 (掉落物同层可拾取)。
     * 错题 #138 (v79.26.8g 自检): 挖矿 XZ 列 (含矿本体) 水平偏移时女仆不掉洞, 掉落物
     * 在洞底拾取半径外滞留 — 挖穿列必须与女仆同列 (挖脚下格才掉洞)。
     * 双门控 (v79.26.7): 水平门对齐 v79.19o 原语义 "已到目标水平邻域才挖穿"
     * (远处走 TLM 导航); canReach 预检 — TLM 可达 → 走正常路, 不可达 (地下被盖) → 挖穿。
     */
    public static boolean digVertical(ServerLevel world, EntityMaid maid, BlockPos target) {
        BlockPos foot = maid.blockPosition();
        int hx = target.getX() - foot.getX();
        int hz = target.getZ() - foot.getZ();
        if (target.getY() >= foot.getY()
                || foot.getY() - target.getY() > ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get()
                || hx * hx + hz * hz > VanillaConstants.ARRIVE_DIST_SQR
                || PathingApi.canReach(maid, target)) {
            return false;
        }
        // 挖女仆脚下列 (每 tick 一格, 挖到矿层): 女仆每 tick 挖脚下格掉落,
        // 一路落至矿层站在洞里 → 矿同层水平偏移 ≤2.83 由 idleScan 直接开脉
        // (掉落物同层可拾取); 偏移 3 边界 → TLM 1 格邻域 or FAILED 跳过集 (矿保留)。
        // 错题 #138: 挖矿 XZ 列 (含矿本体) 水平偏移时女仆不掉洞, 掉落物在洞底
        // 拾取半径外滞留 — 改回挖女仆脚下列 (XZ=foot)。
        for (int y = foot.getY() - 1; y >= target.getY(); y--) {
            BlockPos p = new BlockPos(foot.getX(), y, foot.getZ());
            BlockState bs = world.getBlockState(p);
            if (!bs.isAir()) {
                return digOne(world, maid, p, bs);
            }
        }
        return false;
    }

    /**
     * 向上挖穿 (v79.26.8g) — 目标在头顶 (深度 ≤ CHAIN_DIG_DOWN_DEPTH 且 水平 ≤ ARRIVE_DIST_SQR
     * 且 TLM 不可达) → 自下而上挖矿正下方整列 (含矿本体): 先挖路径石头, 最后挖到矿,
     * 掉落物沿挖穿通道落到女仆脚边 — 背包满时掉落物也躺脚边, 不卡在矿下方石头上。
     * 破坏方块无 reach 限制 (服务端 destroyBlock), 头顶最高 6 格可直挖。
     */
    public static boolean digUp(ServerLevel world, EntityMaid maid, BlockPos target) {
        BlockPos foot = maid.blockPosition();
        int hx = target.getX() - foot.getX();
        int hz = target.getZ() - foot.getZ();
        if (target.getY() <= foot.getY()
                || target.getY() - foot.getY() > ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get()
                || hx * hx + hz * hz > VanillaConstants.ARRIVE_DIST_SQR
                || PathingApi.canReach(maid, target)) {
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
    private static boolean digOne(ServerLevel world, EntityMaid maid, BlockPos pos, BlockState bs) {
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
//? if 1.20.1 {
        tool.hurtAndBreak(1, maid, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
//?} else {
        tool.hurtAndBreak(1, maid, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
        maid.swing(InteractionHand.MAIN_HAND);
        return true;
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * FakePlayer 右键交互样板 — 3 处重复 (v38 InteractBlockAction / v67 BlockInteractService) 的公共骨架。
 *
 * <p>new LmaFakePlayer → simulate(RIGHT_CLICK_ONCE) → syncHandToMaid → cleanup。
 * 距离/方块存在等业务检查由调用方负责。
 */
public final class FakePlayerInteract {

    private FakePlayerInteract() {}

    /**
     * 模拟女仆右键方块一次。
     *
     * @return true=交互成功, false=模拟失败
     */
    public static boolean rightClick(ServerLevel world, EntityMaid maid, BlockPos pos, Direction face) {
        return rightClick(world, maid, pos, face, Double.MAX_VALUE);
    }

    /**
     * 带距离判定的右键交互 (UseBlockAction 组合吸收) — distSqr > range² → no-op。
     */
    public static boolean rightClick(ServerLevel world, EntityMaid maid, BlockPos pos, Direction face,
                                     double range) {
        if (range >= 0 && maid.blockPosition().distSqr(pos) > range * range) return false;
        return rightClickInner(world, maid, pos, face);
    }

    /**
     * 纯块放置 (仿 maid_useful_task placeBlock 链) — 无实体交互扫描, 无事件。
     * 放置路径专用 (PathExecutor PLACE): 点击 pos 的 face 面, 新块落 pos.relative(face)。
     * 距离/方块存在等业务检查由调用方负责。
     *
     * @return true=useOn 消费 (放置被接受; 复查 placed = 目标格实心由调用方做)
     */
    public static boolean placeBlock(ServerLevel world, EntityMaid maid, BlockPos pos, Direction face) {
        LmaFakePlayer fp = new LmaFakePlayer(world, maid, pos);
        try {
            boolean ok = LmaPlayerSimulator.simulatePlaceBlock(fp, world, pos, face);
            LmaPlayerSimulator.syncHandToMaid(fp);
            return ok;
        } finally {
            LmaPlayerSimulator.cleanup(fp, world);
        }
    }

    private static boolean rightClickInner(ServerLevel world, EntityMaid maid, BlockPos pos, Direction face) {
        LmaFakePlayer fp = new LmaFakePlayer(world, maid, pos);
        try {
            boolean ok = LmaPlayerSimulator.simulate(fp, world, pos, face,
                    LmaPlayerSimulator.Mode.RIGHT_CLICK_ONCE);
            LmaPlayerSimulator.syncHandToMaid(fp);
            return ok;
        } finally {
            LmaPlayerSimulator.cleanup(fp, world);
        }
    }
}

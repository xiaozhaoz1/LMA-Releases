package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemSelect;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * 工具选择执行 (v77.5 移植自 Numen ToolSelect) — 全背包选最优挖掘工具 + 换主手。
 *
 * <p>评分语义 (Numen 同款): 能正确收获的 (tier-gated 方块 requiresCorrectToolForDrops →
 * isCorrectToolForDrops) 优先于只更快的 (getDestroySpeed); 无正确 tier 工具时回退最快任意工具。
 * 换手: maid.setItemInHand (LmaPlayerSimulator.syncHandToMaid 模式)。
 * 评分器独立暴露 (纯逻辑, 可单测)。
 */
public final class ToolSelectExecute {

    private ToolSelectExecute() {}

    /** 挖掘评分器 — 速度 > 1.0 (胜过空手) 且 tier 优先 (Numen holdBestTool 语义) */
    public static double miningScore(ItemStack stack, BlockState state) {
        float spd = stack.getDestroySpeed(state);
        if (spd <= 1.0f) return -Double.MAX_VALUE;   // 不优于空手 → 排除
        if (state.requiresCorrectToolForDrops()) {
            return stack.isCorrectToolForDrops(state) ? 1000.0 + spd : spd;   // 正确 tier 压倒性优先
        }
        return spd;
    }

    /** 全背包选最优工具 (未换手 — 只查询) */
    public static Optional<ItemSelect.SlotPick<ItemStack>> bestToolFor(EntityMaid maid, BlockState state) {
        return ItemSelect.selectBest(maid, s -> true, s -> miningScore(s, state));
    }

    /** 选最优工具并换主手 — 返回是否换手成功 */
    public static boolean holdBestTool(EntityMaid maid, BlockState state) {
        Optional<ItemSelect.SlotPick<ItemStack>> pick = bestToolFor(maid, state);
        if (pick.isEmpty()) return false;
        maid.setItemInHand(InteractionHand.MAIN_HAND, pick.get().value());
        return true;
    }
}

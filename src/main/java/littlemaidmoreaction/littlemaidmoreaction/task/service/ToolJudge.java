package littlemaidmoreaction.littlemaidmoreaction.task.service;

import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 工具判断计算层 (v36) — 组合 {@link ToolStateReader} 原子 IO 产出高层判断。
 *
 * <p>执行层（ChainHarvestExecute 等）只调用本类，不得内联任何
 * instanceof / MC 工具判断逻辑。仿 {@link RecipeResolver} 的 service 模式。
 */
public final class ToolJudge {

    private ToolJudge() {}

    /**
     * 判断镐挖掘等级：是镐 且 挖掘等级足够让此方块掉落。
     * （木镐挖钻石矿 → false）
     */
    public static boolean canPickaxeMine(ItemStack tool, BlockState state) {
        return ToolStateReader.isPickaxe(tool)
                && ToolStateReader.isCorrectToolForDrops(tool, state);
    }

    /**
     * 判断斧砍伐：是斧即可。
     * 原木无挖掘等级门槛（任何斧都能掉落原木），不走 isCorrectToolForDrops —
     * 该判定对无 needs_*_tool 标签的方块可能因材质表缺失而误判。
     */
    public static boolean canAxeChop(ItemStack tool, BlockState state) {
        return ToolStateReader.isAxe(tool);
    }

    /**
     * 工具可用判定：剩余耐久大于保留值（防止把工具用坏）。
     *
     * @param reserveDurability 保留耐久（如 1 = 剩最后 1 点时停手）
     */
    public static boolean isToolUsable(ItemStack tool, int reserveDurability) {
        return !tool.isEmpty()
                && ToolStateReader.getRemainingDurability(tool) > reserveDurability;
    }

    // ── v36.2: 挖掘等级速度表（用户 2026-07-17 定义） ──

    /** 按 tier 的破坏间隔 (tick/块): 木20 / 石15 / 铁10 / 钻5 / 下界合金5 */
    private static final int[] TIER_INTERVAL_TICKS = {20, 15, 10, 5, 5};
    /** 空手/非对应工具/将坏工具的破坏间隔 */
    private static final int BARE_HAND_INTERVAL_TICKS = 40;

    /**
     * v36.2 挖掘间隔查表：等级越高破坏越快。
     * 非对应类型工具/空手/将坏 → 40 tick（慢速兜底，砍树不拦截语义）。
     *
     * @param requireAxe true=砍树(需斧提速) / false=挖矿(需镐提速)
     */
    public static int harvestIntervalTicks(ItemStack tool, boolean requireAxe) {
        boolean properTool = requireAxe
                ? ToolStateReader.isAxe(tool)
                : ToolStateReader.isPickaxe(tool);
        if (!properTool || !isToolUsable(tool, 1)) {
            return BARE_HAND_INTERVAL_TICKS;
        }
        int tier = ToolStateReader.getTierLevel(tool);
        if (tier < 0) {
            return BARE_HAND_INTERVAL_TICKS;
        }
        return TIER_INTERVAL_TICKS[Math.min(tier, TIER_INTERVAL_TICKS.length - 1)];
    }
}

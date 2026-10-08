package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * 泛化背包物品选择器 (v77.5) — 评分函数驱动的全背包最优选择 (Numen ToolSelect 泛化)。
 *
 * <p>核心 = 泛型纯函数 {@link #selectBestFrom} (零 MC 依赖, 纯 JVM 可测);
 * ItemStack 版为薄封装。应用场景: 工具选择 (评分器 = getDestroySpeed + tier 优先)、
 * 放置方块、食物、弹药 — 谓词过滤候选 + 评分器取最高分。
 */
public final class ItemSelect {

    /** 选中的槽位与值 */
    public record SlotPick<T>(int slot, T value) {}

    private ItemSelect() {}

    /** 泛型纯核心 — 评分最高候选 (谓词过滤; 空列表/无候选 → empty) */
    public static <T> Optional<SlotPick<T>> selectBestFrom(List<T> items,
                                                           Predicate<T> candidate,
                                                           ToDoubleFunction<T> scorer) {
        SlotPick<T> best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            T v = items.get(i);
            if (!candidate.test(v)) continue;
            double score = scorer.applyAsDouble(v);
            if (score > bestScore) {
                bestScore = score;
                best = new SlotPick<>(i, v);
            }
        }
        return Optional.ofNullable(best);
    }

    /** 女仆全背包 (handsFirst) — 评分最高候选 */
    public static Optional<SlotPick<ItemStack>> selectBest(EntityMaid maid,
                                                           Predicate<ItemStack> candidate,
                                                           ToDoubleFunction<ItemStack> scorer) {
        return selectBest(maid.getAvailableInv(true), candidate, scorer);
    }

    /** 任意 IItemHandler 容器 */
    public static Optional<SlotPick<ItemStack>> selectBest(IItemHandler inv,
                                                           Predicate<ItemStack> candidate,
                                                           ToDoubleFunction<ItemStack> scorer) {
        List<ItemStack> stacks = new ArrayList<>(inv.getSlots());
        for (int i = 0; i < inv.getSlots(); i++) {
            stacks.add(inv.getStackInSlot(i));
        }
        return selectBestFrom(stacks, candidate, scorer);
    }
}

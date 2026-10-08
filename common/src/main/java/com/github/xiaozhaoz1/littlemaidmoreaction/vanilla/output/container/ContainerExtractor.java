package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.container.NearbyContainerScanner;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.container.WirelessChestSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if 1.20.1 {
import net.minecraftforge.common.capabilities.ForgeCapabilities;
//?} else {
import net.neoforged.neoforge.capabilities.Capabilities;
//?}

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 附近容器提取器 (v79.63 从 {@code input/container/NearbyContainerScanner} 拆出 — 评审 A-2「边错」修正)
 * — <b>写侧: 真提取</b> (io 原语, 单拍写)。
 *
 * <p><b>为什么拆</b>: 原类混居读写 — 提取会 `container.removeItem(...)` / `handler.extractItem(...)`
 * 并 `be.setChanged()`, 属**写操作**, 按两轴表应归 `output/`; 而扫描 (副本列表) 是纯读留 `input/`。
 * 拆分后: 读 {@link NearbyContainerScanner} · 写 本类。
 *
 * <p>语义 (自原实现逐字保留, 零行为变化):
 * <ol>
 *   <li>附近容器: 三重循环, **首中即返回** (优先 vanilla {@code Container}, 回退 {@code IItemHandler})</li>
 *   <li>隙间 (可选): 绑定箱子可能不在搜索半径内</li>
 * </ol>
 * 防御点: `isEmpty` 守卫 / 未命中返回 {@link ItemStack#EMPTY} / `be == null` 跳过 /
 * 容器方块通配符过滤 (复用 {@link NearbyContainerScanner#matchWildcard})。
 */
public final class ContainerExtractor {

    private ContainerExtractor() {}

    // ── Extract (真提取 — vanilla Container 优先, IItemHandler 回退) ──

    public static ItemStack extractItem(Level level, BlockPos center, int radius,
                                         Predicate<ItemStack> filter, Set<String> containerBlocks) {
        return extractItem(level, center, radius, filter, containerBlocks, false, null);
    }

    /** 提取物品 (含隙间可选). */
    public static ItemStack extractItem(Level level, BlockPos center, int radius,
                                         Predicate<ItemStack> filter, Set<String> containerBlocks,
                                         boolean includeWireless,
                                         @Nullable com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        // 1. 附近容器
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockEntity be = level.getBlockEntity(center.offset(dx, dy, dz));
                    if (be == null) continue;
                    if (!containerBlocks.isEmpty()) {
                        String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                        if (containerBlocks.stream().noneMatch(p -> NearbyContainerScanner.matchWildcard(id, p))) {
                            continue;
                        }
                    }
                    ItemStack extracted = tryExtract(be, filter);
                    if (!extracted.isEmpty()) return extracted;
                }
        // 2. 隙间 (绑定箱子, 可能不在搜索半径内)
        if (includeWireless && maid != null) {
            var w = WirelessChestSpace.getWirelessHandler(maid);
            if (w != null) {
                for (int s = 0; s < w.getSlots(); s++) {
                    ItemStack st = w.getStackInSlot(s);
                    if (!st.isEmpty() && filter.test(st)) {
                        return w.extractItem(s, st.getMaxStackSize(), false);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 从方块实体提取物品: vanilla Container → IItemHandler 回退 */
    private static ItemStack tryExtract(BlockEntity be, Predicate<ItemStack> filter) {
        // 1. 优先 vanilla Container (箱子/漏斗/熔炉等) — 直接操作真实库存
        if (be instanceof Container container) {
            for (int s = 0; s < container.getContainerSize(); s++) {
                ItemStack st = container.getItem(s);
                if (!st.isEmpty() && filter.test(st)) {
                    ItemStack taken = container.removeItem(s, st.getMaxStackSize());
                    be.setChanged();
                    return taken;
                }
            }
            return ItemStack.EMPTY;
        }
        // 2. 回退 IItemHandler (mod 容器)
//? if 1.20.1 {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> {
//?} else {
        var handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
        if (handler == null) return ItemStack.EMPTY;
//?}
            for (int s = 0; s < handler.getSlots(); s++) {
                ItemStack st = handler.getStackInSlot(s);
                if (!st.isEmpty() && filter.test(st)) {
                    ItemStack taken = handler.extractItem(s, st.getMaxStackSize(), false);
                    be.setChanged();
                    return taken;
                }
            }
            return ItemStack.EMPTY;
//? if 1.20.1 {
        }).orElse(ItemStack.EMPTY);
//?}
    }

    /** 按物品 ID 通配符提取 (复用读侧匹配语义) */
    public static ItemStack extractById(Level level, BlockPos center, int radius,
                                         String itemPattern, Set<String> containerBlocks) {
        return extractItem(level, center, radius,
                st -> NearbyContainerScanner.matchesItemId(st, itemPattern), containerBlocks);
    }
}

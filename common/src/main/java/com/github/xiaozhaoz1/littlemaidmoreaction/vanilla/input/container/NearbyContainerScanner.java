package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.container;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if 1.20.1 {
import net.minecraftforge.common.capabilities.ForgeCapabilities;
//?} else {
import net.neoforged.neoforge.capabilities.Capabilities;
//?}
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 附近容器扫描器 (v79.6x 自 task/service/NearbyContainerService 迁入 — B8 原语抽离)
 * — <b>读侧: 通用扫描 / 通配符匹配</b> (io 原语, 只读无副作用)。
 *
 * <p><b>v79.63 拆分 (评审 A-2「边错」修正)</b>: 原类混合「扫描 (读) + 提取 (写)」两种语义 —
 * **提取半边已迁 {@code vanilla/output/container/ContainerExtractor}** (提取会 `removeItem` +
 * `be.setChanged()`, 属写操作); 本类保持**纯读** (扫描副本 / 匹配 / 容器过滤)。
 * 调用方迁移: `MaidAssemblyPipeline` / `MaidAssemblyService` / `NearbyCollectBehavior` 的
 * `extractItem` 调用 → `ContainerExtractor`。
 *
 * <p>防御点: isEmpty 守卫 / `st.copy()` 副本 / `be == null` 跳过 / 通配符 * 前缀·后缀·中缀语义。
 *
 * <p>物品ID通配符:
 * <ul>
 *   <li>{@code "minecraft:iron_ingot"} — 精确</li>
 *   <li>{@code "minecraft:iron_*"} — 前缀</li>
 *   <li>{@code "*_ingot"} — 后缀</li>
 *   <li>{@code "*"} — 全部</li>
 * </ul>
 *
 * <p>容器方块过滤: {@code containerBlocks} 通配符匹配, 空=所有容器.
 */
public final class NearbyContainerScanner {

    /** 默认搜索半径 (格) — 提取侧 {@code ContainerExtractor} 共用同值 */
    public static final int DEFAULT_RADIUS = 3;

    private NearbyContainerScanner() {}

    // ── Wildcard ──

    public static boolean matchesItemId(ItemStack stack, String pattern) {
        if (stack.isEmpty()) return false;
        return matchWildcard(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), pattern);
    }

    public static boolean matchWildcard(String text, String pattern) {
        if ("*".equals(pattern)) return true;
        if (pattern.startsWith("*") && pattern.endsWith("*"))
            return text.contains(pattern.substring(1, pattern.length() - 1));
        if (pattern.startsWith("*")) return text.endsWith(pattern.substring(1));
        if (pattern.endsWith("*")) return text.startsWith(pattern.substring(0, pattern.length() - 1));
        return text.equals(pattern);
    }

    // ── Scan (副本列表, 不提取 — 纯读) ──

    public static List<ItemStack> scanItems(Level level, BlockPos center, int radius) {
        return scanItems(level, center, radius, s -> true, Set.of());
    }

    public static List<ItemStack> scanItems(Level level, BlockPos center, int radius,
                                             Predicate<ItemStack> filter, Set<String> containerBlocks) {
        List<ItemStack> result = new ArrayList<>();
        forEachHandler(level, center, radius, containerBlocks, handler -> {
            for (int s = 0; s < handler.getSlots(); s++) {
                ItemStack st = handler.getStackInSlot(s);
                if (!st.isEmpty() && filter.test(st)) result.add(st.copy());
            }
        });
        return result;
    }

    // ── Internal ──

    @FunctionalInterface
    private interface HandlerVisitor { void visit(IItemHandler handler); }

    /**
     * 遍历附近容器的 IItemHandler — **仅读侧 scan 使用** (不真提取)。
     * 写侧提取有自己的三重循环 (需首中即返回语义), 不复用本方法 —
     * 它需要 `be instanceof Container` 优先 + `setChanged()`, 属 ContainerExtractor。
     */
    private static void forEachHandler(Level level, BlockPos center, int radius,
                                        Set<String> containerBlocks, HandlerVisitor v) {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockEntity be = level.getBlockEntity(center.offset(dx, dy, dz));
                    if (be == null) continue;
                    if (!containerBlocks.isEmpty()) {
                        String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                        if (containerBlocks.stream().noneMatch(p -> matchWildcard(id, p))) continue;
                    }
//? if 1.20.1 {
                    be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(v::visit);
//?} else {
                    var cap = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
                    if (cap != null) v.visit(cap);
//?}
                }
    }
}

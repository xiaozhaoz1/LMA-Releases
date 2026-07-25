package littlemaidmoreaction.littlemaidmoreaction.task.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 附近容器服务 — 通用扫描/提取.
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
public final class NearbyContainerService {

    public static final int DEFAULT_RADIUS = 3;

    private NearbyContainerService() {}

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

    // ── Scan (副本列表, 不需要真提取) ──

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

    // ── Extract (真提取 — vanilla Container优先, IItemHandler回退) ──

    public static ItemStack extractItem(Level level, BlockPos center, int radius,
                                         Predicate<ItemStack> filter, Set<String> containerBlocks) {
        return extractItem(level, center, radius, filter, containerBlocks, false, null);
    }

    /** 提取物品 (含隙间可选). */
    public static ItemStack extractItem(Level level, BlockPos center, int radius,
                                         Predicate<ItemStack> filter, Set<String> containerBlocks,
                                         boolean includeWireless,
                                         @javax.annotation.Nullable com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        // 1. 附近容器
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockEntity be = level.getBlockEntity(center.offset(dx, dy, dz));
                    if (be == null) continue;
                    if (!containerBlocks.isEmpty()) {
                        String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                        if (containerBlocks.stream().noneMatch(p -> matchWildcard(id, p))) continue;
                    }
                    ItemStack extracted = tryExtract(be, filter);
                    if (!extracted.isEmpty()) return extracted;
                }
        // 2. 隙间 (绑定箱子, 可能不在搜索半径内)
        if (includeWireless && maid != null) {
            var w = littlemaidmoreaction.littlemaidmoreaction.vanilla.input.container.WirelessChestSpace.getWirelessHandler(maid);
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
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> {
            for (int s = 0; s < handler.getSlots(); s++) {
                ItemStack st = handler.getStackInSlot(s);
                if (!st.isEmpty() && filter.test(st)) {
                    ItemStack taken = handler.extractItem(s, st.getMaxStackSize(), false);
                    be.setChanged();
                    return taken;
                }
            }
            return ItemStack.EMPTY;
        }).orElse(ItemStack.EMPTY);
    }

    public static ItemStack extractById(Level level, BlockPos center, int radius,
                                         String itemPattern, Set<String> containerBlocks) {
        return extractItem(level, center, radius, st -> matchesItemId(st, itemPattern), containerBlocks);
    }

    // ── Internal ──

    @FunctionalInterface
    private interface HandlerVisitor { void visit(IItemHandler handler); }

    /** 遍历附近容器 — 仅用于 scan (副本读取), 不用于 extract (需真操作). */
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
                    be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(v::visit);
                }
    }
}

package littlemaidmoreaction.littlemaidmoreaction.task.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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

    // ── Extract (真提取, 直接操作 IItemHandler) ──

    public static ItemStack extractItem(Level level, BlockPos center, int radius,
                                         Predicate<ItemStack> filter, Set<String> containerBlocks) {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockEntity be = level.getBlockEntity(center.offset(dx, dy, dz));
                    if (be == null) continue;
                    if (!containerBlocks.isEmpty()) {
                        String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                        if (containerBlocks.stream().noneMatch(p -> matchWildcard(id, p))) continue;
                    }
                    var result = be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> {
                        for (int s = 0; s < handler.getSlots(); s++) {
                            ItemStack st = handler.getStackInSlot(s);
                            if (!st.isEmpty() && filter.test(st))
                                return handler.extractItem(s, st.getMaxStackSize(), false);
                        }
                        return ItemStack.EMPTY;
                    }).orElse(ItemStack.EMPTY);
                    if (!result.isEmpty()) return result;
                }
        return ItemStack.EMPTY;
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

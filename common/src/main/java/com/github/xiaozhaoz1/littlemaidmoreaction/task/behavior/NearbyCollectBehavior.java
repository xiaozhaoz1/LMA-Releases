package com.github.xiaozhaoz1.littlemaidmoreaction.task.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.NearbyContainerService;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.container.WirelessChestSpace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.items.ItemHandlerHelper;
//?} else {
import net.neoforged.neoforge.items.ItemHandlerHelper;
//?}

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 附近容器拿物品Behavior. Pipeline 提供 filter, 其余用默认值.
 *
 * <p>产物分发: 指定位置 → 背包 → 隙间 → 扔地上
 */
public class NearbyCollectBehavior extends MaidCheckRateTask {

    private final Function<EntityMaid, Predicate<ItemStack>> filterProvider;
    private final Set<String> containerBlocks;
    private final String dest;
    private final int radius;
    private final boolean includeWireless;

    /** 最简构造: 默认放背包/半径3/全部容器/含隙间 */
    public NearbyCollectBehavior(Function<EntityMaid, Predicate<ItemStack>> filterProvider) {
        this(filterProvider, Set.of(), "backpack", 3, true);
    }

    public NearbyCollectBehavior(Function<EntityMaid, Predicate<ItemStack>> filterProvider,
                                  Set<String> containerBlocks, String dest, int radius) {
        this(filterProvider, containerBlocks, dest, radius, true);
    }

    public NearbyCollectBehavior(Function<EntityMaid, Predicate<ItemStack>> filterProvider,
                                  Set<String> containerBlocks, String dest, int radius,
                                  boolean includeWireless) {
        super(ImmutableMap.of());
        setMaxCheckRate(100);
        this.filterProvider = filterProvider;
        this.containerBlocks = containerBlocks;
        this.dest = dest != null && !dest.isEmpty() ? dest : "backpack";
        this.radius = radius > 0 ? radius : 3;
        this.includeWireless = includeWireless;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid)) return false;
        Predicate<ItemStack> filter = filterProvider.apply(maid);
        if (filter == null) return false;
        return hasDestSpace(maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        Predicate<ItemStack> filter = filterProvider.apply(maid);
        if (filter == null) return;
        ItemStack taken = NearbyContainerService.extractItem(
            level, maid.blockPosition(), radius, filter, containerBlocks, includeWireless, maid);
        if (!taken.isEmpty()) putItem(maid, taken, level);
    }

    /** 检查目标位置是否可容纳物品 */
    private boolean hasDestSpace(EntityMaid maid) {
        return switch (dest) {
            case "mainhand" -> true; // 主手总是能放
            case "offhand" -> true;  // 副手总是能放
            default -> {
                var bp = maid.getAvailableBackpackInv();
                for (int s = 0; s < bp.getSlots(); s++)
                    if (bp.getStackInSlot(s).isEmpty()) yield true;
                yield false;
            }
        };
    }

    /** 放入物品, 溢出 → 隙间 → 扔地上 */
    private void putItem(EntityMaid maid, ItemStack stack, ServerLevel level) {
        ItemStack remaining;
        switch (dest) {
            case "mainhand" -> {
                ItemStack old = maid.getMainHandItem();
                if (!old.isEmpty()) {
                    old = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), old, false);
                    if (!old.isEmpty()) return; // 旧物品塞不回去, 放弃
                }
                maid.setItemInHand(InteractionHand.MAIN_HAND, stack);
                remaining = ItemStack.EMPTY;
            }
            case "offhand" -> {
                ItemStack old = maid.getOffhandItem();
                if (!old.isEmpty()) {
                    old = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), old, false);
                    if (!old.isEmpty()) return;
                }
                maid.setItemInHand(InteractionHand.OFF_HAND, stack);
                remaining = ItemStack.EMPTY;
            }
            default -> remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), stack, false);
        }
        // 溢出 → 隙间 → 扔地上
        if (!remaining.isEmpty()) {
            var wireless = WirelessChestSpace.getWirelessHandler(maid);
            if (wireless != null) remaining = ItemHandlerHelper.insertItemStacked(wireless, remaining, false);
        }
        if (!remaining.isEmpty()) {
            ItemEntity ie = new ItemEntity(level, maid.getX(), maid.getY() + 1, maid.getZ(), remaining);
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }
    }
}

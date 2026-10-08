package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
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
//? if 1.20.1 {
import net.minecraftforge.items.ItemHandlerHelper;
//?} else {
import net.neoforged.neoforge.items.ItemHandlerHelper;
//?}

/**
 * 物品搬运服务 (v53: 移出 compat/create) — 原子化 input→compute→output 三步模式。
 */
public final class ArmTransferService {
    private ArmTransferService() {}

    // ── Input ──

    public static ItemStack readSourceItem(EntityMaid maid, BlockPos sourcePos) {
        return readSourceItem(maid, sourcePos, s -> true);
    }

    /** 读取第一个非空且通过过滤的源物品 (搬运黑白名单用) */
    public static ItemStack readSourceItem(EntityMaid maid, BlockPos sourcePos,
                                           java.util.function.Predicate<ItemStack> filter) {
        IItemHandler handler = getHandler(maid, sourcePos);
        if (handler == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && filter.test(stack)) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack readMaidItem(EntityMaid maid) {
        var inv = maid.getAvailableInv(false);
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty()) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    // ── Compute ──

    public static int computeExtractCount(EntityMaid maid, ItemStack sourceItem) {
        if (sourceItem.isEmpty()) return 0;
        int maxTake = Math.min(sourceItem.getCount(), sourceItem.getMaxStackSize());
        var inv = maid.getAvailableInv(false);
        int totalSpace = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty()) { totalSpace += sourceItem.getMaxStackSize(); }
            else if (ItemStackHelper.isSameItem(slot, sourceItem)) {
                totalSpace += slot.getMaxStackSize() - slot.getCount();
            }
        }
        return Math.min(maxTake, Math.max(totalSpace, 0));
    }

    public static int computeDepositCount(EntityMaid maid, BlockPos targetPos, ItemStack maidItem) {
        if (maidItem.isEmpty()) return 0;
        IItemHandler handler = getHandler(maid, targetPos);
        if (handler == null) return 0;
        ItemStack remainder = ItemHandlerHelper.insertItem(handler, maidItem.copy(), true);
        return maidItem.getCount() - remainder.getCount();
    }

    // ── Output ──

    /** 从源容器提取物品到女仆背包, 返回实际提取数 — 委托 ContainerOutput (溢出退还统一) */
    public static int executeExtract(EntityMaid maid, BlockPos sourcePos, ItemStack item, int count) {
        IItemHandler handler = getHandler(maid, sourcePos);
        if (handler == null) return 0;
        return com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                .withdrawItemStack(maid, handler, item, count);
    }

    /** 从女仆背包存入目标容器, 返回实际存入数 — 委托 ContainerOutput (溢出退还统一) */
    public static int executeDeposit(EntityMaid maid, BlockPos targetPos, ItemStack item, int count) {
        IItemHandler handler = getHandler(maid, targetPos);
        if (handler == null) return 0;
        return com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                .depositItemStack(maid, handler, item, count);
    }

    // ── Query ──

    public static boolean isSourceEmpty(EntityMaid maid, BlockPos pos) {
        return readSourceItem(maid, pos).isEmpty();
    }

    public static boolean hasInventorySpace(EntityMaid maid) {
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    public static boolean isValidContainer(EntityMaid maid, BlockPos pos) {
        if (pos == null || !maid.isWithinRestriction(pos)) return false;
        return getHandler(maid, pos) != null;
    }

    private static IItemHandler getHandler(EntityMaid maid, BlockPos pos) {
        // capability 六方向遍历统一到 ContainerOutput.getHandler
        if (!(maid.level() instanceof net.minecraft.server.level.ServerLevel sl)) return null;
        return com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput.getHandler(sl, pos);
    }

    // ── 物品标识 (v79.61x 架构审计 C 从 ArmTransferPipeline 归位) ──

    /** 物品 registry id (双平台) — 记录搬运物品供放货匹配 */
    public static String itemId(EntityMaid maid, ItemStack item) {
        net.minecraft.resources.ResourceLocation id;
 //? if 1.20.1 {
        id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
 //?} else {
        id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem());
 //?}
        return id != null ? id.toString() : item.getDescriptionId();
    }

    /** 按 registry id 在女仆背包找物品 (放货匹配) */
    public static ItemStack findMaidItem(EntityMaid maid, String itemId) {
        var rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (rl == null) return ItemStack.EMPTY;
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            net.minecraft.resources.ResourceLocation id;
 //? if 1.20.1 {
            id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
 //?} else {
            id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
 //?}
            if (rl.equals(id)) return s.copy();
        }
        return ItemStack.EMPTY;
    }
}

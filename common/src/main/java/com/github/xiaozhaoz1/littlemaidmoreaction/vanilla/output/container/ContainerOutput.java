package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
//?}

/** 容器交互输出 — 物品存取原语 */
public final class ContainerOutput {
    private ContainerOutput() {}

    /**
     * v79.5: 容器 handler 获取 — capability 六方向遍历 (ArmTransferService.getHandler 提升)。
     * 1.20.1 = ForgeCapabilities.ITEM_HANDLER + resolve; 1.21.1 = Capabilities.ItemHandler.BLOCK。
     */
    @javax.annotation.Nullable
    public static IItemHandler getHandler(net.minecraft.server.level.ServerLevel level,
                                          net.minecraft.core.BlockPos pos) {
        if (pos == null) return null;
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        for (var dir : net.minecraft.core.Direction.values()) {
//? if 1.20.1 {
            var handler = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, dir)
                    .resolve().orElse(null);
//?} else {
            var handler = be.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    be.getBlockPos(), dir);
//?}
            if (handler != null) return handler;
        }
        return null;
    }

    /**
     * v79.5: 按 ItemStack 匹配存取 (isSameItem — NBT 级) — ArmTransferService.execute*
     * 溢出退还算法统一于此。返回实际存取数。
     */
    public static int depositItemStack(EntityMaid maid, IItemHandler container,
                                       ItemStack item, int count) {
        var inv = maid.getAvailableInv(false);
        int actual = 0;
        for (int i = 0; i < inv.getSlots() && actual < count; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper.isSameItem(stack, item)) continue;
            int take = Math.min(count - actual, stack.getCount());
            ItemStack taken = inv.extractItem(i, take, false);
            if (taken.isEmpty()) continue;
            int before = taken.getCount();
            // 逐槽插入 + 溢出退还 (与 depositItem 同款循环 — 零 ItemHandlerHelper 依赖, 双平台安全)
            for (int j = 0; j < container.getSlots() && !taken.isEmpty(); j++) {
                taken = container.insertItem(j, taken, false);
            }
            int after = taken.getCount();
            actual += (before - after);
            if (after > 0) inv.insertItem(i, taken, false);
        }
        return actual;
    }

    /** v79.5: 按 ItemStack 匹配提取 (isSameItem) — 溢出退还容器。返回实际提取数。 */
    public static int withdrawItemStack(EntityMaid maid, IItemHandler container,
                                        ItemStack item, int count) {
        var inv = maid.getAvailableInv(false);
        int actual = 0;
        for (int i = 0; i < container.getSlots() && actual < count; i++) {
            ItemStack stack = container.getStackInSlot(i);
            if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper.isSameItem(stack, item)) continue;
            int take = Math.min(count - actual, stack.getCount());
            ItemStack extracted = container.extractItem(i, take, false);
            if (extracted.isEmpty()) continue;
            int before = extracted.getCount();
            // 逐槽插入 + 溢出退还 (零 ItemHandlerHelper 依赖, 双平台安全)
            for (int j = 0; j < inv.getSlots() && !extracted.isEmpty(); j++) {
                extracted = inv.insertItem(j, extracted, false);
            }
            int after = extracted.getCount();
            actual += (before - after);
            if (after > 0) container.insertItem(i, extracted, false);
        }
        return actual;
    }

    /** 从女仆背包提取物品并存入容器。溢出自动退还女仆。 */
    public static boolean depositItem(EntityMaid maid, IItemHandler container, Item item, int count) {
        var inv = maid.getAvailableInv(false);
        int remaining = count;
        for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
            var stack = inv.getStackInSlot(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                var toDeposit = inv.extractItem(i, take, false);
                if (!toDeposit.isEmpty()) {
                    int before = toDeposit.getCount();
                    for (int j = 0; j < container.getSlots() && !toDeposit.isEmpty(); j++)
                        toDeposit = container.insertItem(j, toDeposit, false);
                    if (!toDeposit.isEmpty()) // 容器满了，退还女仆
                        inv.insertItem(i, toDeposit, false);
                    remaining -= (before - toDeposit.getCount());
                }
            }
        }
        return remaining < count;
    }

    /** 从容器提取物品并存入女仆背包。溢出自动退还容器。 */
    public static boolean withdrawItem(EntityMaid maid, IItemHandler container, Item item, int count) {
        var inv = maid.getAvailableInv(false);
        int remaining = count;
        for (int i = 0; i < container.getSlots() && remaining > 0; i++) {
            var stack = container.getStackInSlot(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                var extracted = container.extractItem(i, take, false);
                if (!extracted.isEmpty()) {
                    int before = extracted.getCount();
                    for (int j = 0; j < inv.getSlots() && !extracted.isEmpty(); j++)
                        extracted = inv.insertItem(j, extracted, false);
                    if (!extracted.isEmpty()) // 女仆背包满了，退还容器
                        container.insertItem(i, extracted, false);
                    remaining -= (before - extracted.getCount());
                }
            }
        }
        return remaining < count;
    }

    /** 从女仆背包提取任意物品 (首个非空栈起) 存入容器。溢出自动退还女仆。 (v76 Phase 4) */
    public static boolean depositAny(EntityMaid maid, IItemHandler container, int count) {
        var inv = maid.getAvailableInv(false);
        int remaining = count;
        for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
            var stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int take = Math.min(remaining, stack.getCount());
                var toDeposit = inv.extractItem(i, take, false);
                if (!toDeposit.isEmpty()) {
                    int before = toDeposit.getCount();
                    for (int j = 0; j < container.getSlots() && !toDeposit.isEmpty(); j++)
                        toDeposit = container.insertItem(j, toDeposit, false);
                    if (!toDeposit.isEmpty()) // 容器满了，退还女仆
                        inv.insertItem(i, toDeposit, false);
                    remaining -= (before - toDeposit.getCount());
                }
            }
        }
        return remaining < count;
    }

    /** 从容器提取任意物品存入女仆背包。溢出自动退还容器。 (v76 Phase 4) */
    public static boolean withdrawAny(EntityMaid maid, IItemHandler container, int count) {
        var inv = maid.getAvailableInv(false);
        int remaining = count;
        for (int i = 0; i < container.getSlots() && remaining > 0; i++) {
            var stack = container.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int take = Math.min(remaining, stack.getCount());
                var extracted = container.extractItem(i, take, false);
                if (!extracted.isEmpty()) {
                    int before = extracted.getCount();
                    for (int j = 0; j < inv.getSlots() && !extracted.isEmpty(); j++)
                        extracted = inv.insertItem(j, extracted, false);
                    if (!extracted.isEmpty()) // 女仆背包满了，退还容器
                        container.insertItem(i, extracted, false);
                    remaining -= (before - extracted.getCount());
                }
            }
        }
        return remaining < count;
    }
}

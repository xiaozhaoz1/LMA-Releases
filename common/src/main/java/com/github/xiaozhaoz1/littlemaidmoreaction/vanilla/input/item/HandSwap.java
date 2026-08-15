package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.items.ItemHandlerHelper;
//?} else {
import net.neoforged.neoforge.items.ItemHandlerHelper;
//?}

/**
 * 主手物品切换原语 (v79.61x 抽取) — 整槽提取 + 旧物保全三链 (槽位 → 背包 → 落地)。
 *
 * <p>来源收敛: ChainHarvestExecute.swapTool 与 BlockUpCoordinator.placeMaterial/dropToBackpack
 * 两处复制的「换手不丢」链 (错题 #162 丢物品族: 原 extractItem(1) 只取 1 个 → 剩余堆叠占槽 →
 * 旧物塞不进静默丢失)。对照 TLM TaskEquipUtil: putMainHandBack 只塞空槽、背包满即失败
 * (return false, 旧物留主手) — LMA 版加落地兜底 (旧物不丢)。
 *
 * <p>v79.61x 定级 (两轴表): io 层「通用复合原语」— 判据是通用性 (无业务语义, 多域复用:
 * 挖矿换工具 + 垫柱放置), 不是粒度 (复合 ≠ 业务编排); service 单拍的判据是领域语义。
 * slot = {@code maid.getAvailableInv(true)} 槽位索引 (含背包, 与 ItemSelect 返回槽位对应)。
 */
public final class HandSwap {

    private HandSwap() {}

    /** 整槽提取 + 旧主手物品保全 (不动主手) — 槽无效/空 → EMPTY */
    public static ItemStack extractSlotStashOld(EntityMaid maid, int slot) {
        var inv = maid.getAvailableInv(true);
        if (slot < 0 || slot >= inv.getSlots()) return ItemStack.EMPTY;
        // 整槽提取腾空槽位 — 原 extractItem(1) 只取 1 个, 剩余堆叠占槽 → 旧物塞不进
        ItemStack picked = inv.extractItem(slot, inv.getStackInSlot(slot).getCount(), false);
        if (picked.isEmpty()) return ItemStack.EMPTY;
        // 提取后确认槽位腾空 — 非空 (异常 handler 未取净) → 旧物不塞此槽, 直接走全背包兜底
        boolean slotEmpty = inv.getStackInSlot(slot).isEmpty();
        ItemStack old = maid.getMainHandItem();
        if (!old.isEmpty()) {
            ItemStack remainder = slotEmpty ? inv.insertItem(slot, old, false) : old;
            if (!remainder.isEmpty()) {
                stashOrDrop(maid, remainder);
            }
        }
        return picked;
    }

    /** 提取并换到主手 (旧物保全) — 成功 → true */
    public static boolean swapTo(EntityMaid maid, int slot) {
        ItemStack picked = extractSlotStashOld(maid, slot);
        if (picked.isEmpty()) return false;
        maid.setItemInHand(InteractionHand.MAIN_HAND, picked);
        return true;
    }

    /** 余量兜底 — 背包 (不含手槽) 插不进 → 落地 (旧物/柱材不丢) */
    public static void stashOrDrop(EntityMaid maid, ItemStack stack) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), stack, false);
        if (!remainder.isEmpty()) {
            maid.spawnAtLocation(remainder);
        }
    }
}

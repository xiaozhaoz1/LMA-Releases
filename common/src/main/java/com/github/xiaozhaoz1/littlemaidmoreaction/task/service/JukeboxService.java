package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.ItemSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
//? if 1.20.1 {
import net.minecraftforge.items.ItemHandlerHelper;
//?} else {
import net.neoforged.neoforge.items.ItemHandlerHelper;
//?}

/**
 * 唱片机业务服务 (v79.61x 定级修正) — 原 JukeboxOutput 多步业务动作 (插碟/弹碟),
 * 非 io 原语 (有领域语义: 弹碟回背包满落地); 无跨 tick 状态 → service 单拍。行为零变化。
 */
public final class JukeboxService {

    private JukeboxService() {}

    /** 向唱片机插入唱片 */
    public static boolean insertDisc(JukeboxBlockEntity jukebox, ItemStack disc, Level level, BlockPos pos) {
        if (disc.isEmpty()) return false;
//? if 1.20.1 {
        jukebox.setFirstItem(disc.copy());
//?} else {
        jukebox.setItem(0, disc.copy());
//?}
        jukebox.setChanged();
        level.levelEvent(null, 1010, pos, Item.getId(disc.getItem()));
        return true;
    }

    /** 从唱片机弹出唱片回女仆背包。背包满时溢出掉地上。 (原文逐行搬移 — insertItem 部分插入判定) */
    public static boolean ejectDisc(JukeboxBlockEntity jukebox, EntityMaid maid) {
//? if 1.20.1 {
        ItemStack record = jukebox.getFirstItem();
//?} else {
        ItemStack record = jukebox.getItem(0);
//?}
        if (record.isEmpty()) return false;
        ItemStack remainder = ItemHandlerHelper.insertItem(maid.getAvailableInv(true), record.copy(), false);
        if (remainder.getCount() < record.getCount()) {
            jukebox.removeItem(0, 1);
            jukebox.setChanged();
            if (!remainder.isEmpty()) ItemSpawner.spawnForPickup(maid, remainder);
            return true;
        }
        return false;
    }
}

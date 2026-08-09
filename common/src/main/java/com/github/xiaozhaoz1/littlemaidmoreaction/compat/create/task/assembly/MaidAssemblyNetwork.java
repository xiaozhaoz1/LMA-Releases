package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
//? if 1.20.1 {
import net.minecraftforge.network.NetworkHooks;
//?}

import javax.annotation.Nullable;

/**
 * 便携装配网络层 — MenuProvider + 数据包。
 * 木棍右键女仆时由 MaidAssemblyEventHandler 调用 openGui()。
 */
public final class MaidAssemblyNetwork {

    private MaidAssemblyNetwork() {}

    /** 服务端打开装配GUI (v75.1: 1.21 用 vanilla openMenu — neoforge 无 NetworkHooks) */
    public static void openGui(ServerPlayer player, EntityMaid maid) {
//? if 1.20.1 {
        NetworkHooks.openScreen(player, new MaidAssemblyMenuProvider(maid),
            buf -> buf.writeInt(maid.getId()));
//?} else {
        player.openMenu(new MaidAssemblyMenuProvider(maid),
            buf -> buf.writeInt(maid.getId()));
//?}
    }

    /** MenuProvider — 传递女仆实体ID */
    public static class MaidAssemblyMenuProvider implements MenuProvider {
        private final EntityMaid maid;

        public MaidAssemblyMenuProvider(EntityMaid maid) { this.maid = maid; }

        @Override
        public Component getDisplayName() {
            return Component.translatable("gui." + LittleMaidMoreAction.MOD_ID + ".maid_assembly");
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
            return new MaidAssemblyMenu(id, playerInv, maid);
        }
    }

    /** 客户端从buffer读取maid ID */
    public static EntityMaid getMaidFromMenu(Inventory playerInv, FriendlyByteBuf data) {
        int maidId = data.readInt();
        if (playerInv.player.level().getEntity(maidId) instanceof EntityMaid maid)
            return maid;
        throw new IllegalStateException("MaidAssemblyGUI: 女仆实体不存在 id=" + maidId);
    }
}

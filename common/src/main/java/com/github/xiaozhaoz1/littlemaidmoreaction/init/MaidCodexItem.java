package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidCodexScreenPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * v79.47: 图鉴书物品 — 右键打开击杀图鉴界面。
 *
 * <p>服务端右键 → 合并玩家全部女仆的图鉴计数 (PD lma_codex) →
 * {@link MaidCodexScreenPacket} (S2C) → 客户端打开 {@code MaidCodexScreen}。
 * 零 C2S (数据随 S2C 包直发)。
 */
public class MaidCodexItem extends Item {

    public MaidCodexItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /** use 签名双平台同为 InteractionResultHolder (编译实证 — 1.21.1 未改签名) */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            sendCodex(sp, player);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    /** 服务端: 合并玩家全部女仆图鉴计数 (全维度扫描, MaidList 模式) → S2C 开屏 */
    private static void sendCodex(ServerPlayer sp, Player player) {
        Map<String, Integer> merged = new HashMap<>();
        for (var serverLevel : sp.server.getAllLevels()) {
            for (var e : serverLevel.getAllEntities()) {
                if (!(e instanceof EntityMaid maid)) continue;
                if (!maid.isAlive()) continue;
                if (!player.getUUID().equals(maid.getOwnerUUID())) continue;
                CompoundTag codex = maid.getPersistentData().getCompound(
                        com.github.xiaozhaoz1.littlemaidmoreaction.event.MaidCodexKillListener.CODEX_KEY);
                for (String id : codex.getAllKeys()) {
                    merged.merge(id, codex.getInt(id), Integer::sum);
                }
            }
        }
        MaidCodexScreenPacket.sendTo(sp, merged);
    }
}

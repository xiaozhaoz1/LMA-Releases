package com.github.xiaozhaoz1.littlemaidmoreaction.network.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

/**
 * v7 动画数据同步 — 客户端落地处理 (2026-09-21 由 {@code LmaAnimSyncMessage} 外移)。
 *
 * <p>在单机/专用服务器中, 服务端 EntityMaid 和客户端 EntityMaid 是不同对象,
 * PersistentData 不会自动同步 ⇒ 服务端写完后发包, 本类把 key 合并进客户端女仆的
 * PersistentData, 使 {@code LmaMagicCastingProvider} 能读到动画请求。
 *
 * <p>边界: 空 NBT = 恶意/损坏包 ⇒ **忽略** (客户端信任边界, 与迁移前同);
 * 无世界 (主菜单) 或 maidId 找不到实体 ⇒ 静默跳过。
 */
@OnlyIn(Dist.CLIENT)
public final class AnimSyncClientHandler {

    private AnimSyncClientHandler() {}

    /** 客户端: 把服务端动画 NBT 合并进目标女仆 PersistentData */
    public static void apply(int maidId, CompoundTag animData) {
        if (animData == null) return;   // 恶意/损坏包: 空 NBT 忽略 (客户端信任边界)
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(maidId) instanceof EntityMaid maid) {
            CompoundTag clientData = maid.getPersistentData();
            for (String key : animData.getAllKeys()) {
                clientData.put(key, animData.get(key).copy());
            }
        }
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge 1.21.1 网络发送实现 — LmaNetwork.ISender 注入。
 * <p>forge 侧对应 SimpleChannelSender (SimpleChannel + PacketDistributor)。</p>
 */
public final class NeoNetworkSender implements LmaNetwork.ISender {

    @Override
    public void sendToServer(Object msg) {
        // v75.2: 主菜单 (未进世界) 无连接 — PacketDistributor.sendToServer requireNonNull 崩 (用户实证)
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer((CustomPacketPayload) msg);
    }

    @Override
    public void sendToAll(Object msg) {
        PacketDistributor.sendToAllPlayers((CustomPacketPayload) msg);
    }

    @Override
    public void sendToTrackingEntity(Entity entity, Object msg) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, (CustomPacketPayload) msg);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object msg) {
        PacketDistributor.sendToPlayer(player, (CustomPacketPayload) msg);
    }
}

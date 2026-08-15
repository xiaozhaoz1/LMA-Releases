package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if 1.20.1 {
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
//?}

/**
 * Forge 1.20.1 网络发送实现 — SimpleChannel 封装 (LmaNetwork.ISender 注入)。
 * <p>neoforge 侧对应 {@code NeoNetworkSender} (payload)。</p>
 */
//? if 1.20.1 {
public record SimpleChannelSender(SimpleChannel channel) implements LmaNetwork.ISender {
    @Override
    public void sendToServer(Object msg) {
        // M-1: 对称 neoforge v75.2 修复 — 主菜单 (未进世界) 无连接: SimpleChannel.sendToServer
        // 内部 Minecraft.getInstance().getConnection() 为 null → manager.send NPE (forge 47.2.0 源码实证)
        if (Minecraft.getInstance().getConnection() == null) return;
        channel.sendToServer(msg);
    }

    @Override
    public void sendToAll(Object msg) {
        channel.send(PacketDistributor.ALL.noArg(), msg);
    }

    @Override
    public void sendToTrackingEntity(Entity entity, Object msg) {
        channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), msg);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object msg) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
//?} else {
//?}

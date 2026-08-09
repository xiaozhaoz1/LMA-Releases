package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if 1.20.1 {
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

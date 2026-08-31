package com.github.xiaozhaoz1.littlemaidmoreaction.network;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.patpat.PatPatCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
//? if 1.20.1 {
import net.minecraftforge.network.NetworkEvent;
//?}
//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

import java.util.function.Supplier;

/**
 * PatPat 抚摸反应包 (C→S, v79.61x) — 客户端感知女仆被 PatPat 抚摸 (状态表轮询) →
 * 发本包 → 服务端校验 (owner/距离/600t 节流) → 好感+1 + 爱心粒子 + 音效 + 气泡。
 *
 * <p>信任客户端模型 (与 PatPat 服务端一致 — PatPat 自己也是 C2S 零校验信任客户端);
 * LMA 侧补轻校验: owner + 3 格 + per-maid 600t 节流 (服务端权威节流, 客户端节流为降载)。
 */
//? if 1.20.1 {
public record PatPatReactionPacket(int maidId) {
//?} else {
public record PatPatReactionPacket(int maidId) implements CustomPacketPayload {
//?}

    public static void encode(PatPatReactionPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId());
    }

    public static PatPatReactionPacket decode(FriendlyByteBuf buf) {
        return new PatPatReactionPacket(buf.readInt());
    }

//? if 1.20.1 {
    public static void handle(PatPatReactionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PatPatCompat.onReaction(player, msg.maidId());
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<PatPatReactionPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "patpat_reaction"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, PatPatReactionPacket> STREAM_CODEC =
        PacketCodecs.wrap(PatPatReactionPacket::encode, PatPatReactionPacket::decode);

    public static void handlePayload(PatPatReactionPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PatPatCompat.onReaction(player, msg.maidId());
        });
    }
//?}

    /** 客户端发送反应请求 (被摸女仆 entityId) */
    public static void sendToServer(int maidId) {
        LmaNetwork.sender.sendToServer(new PatPatReactionPacket(maidId));
    }
}

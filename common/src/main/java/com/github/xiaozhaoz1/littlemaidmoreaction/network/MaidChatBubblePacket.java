package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.client.MaidBubbleClientHandler;
import net.minecraft.network.FriendlyByteBuf;
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
 * 女仆表情气泡通用网络包 (v79.20) — 服务端给任意女仆实体头上加表情气泡。
 *
 * <p>通用 API 形态 (用户裁定): 管道写参数 (maidId + 表情类型) 后直接发包,
 * 客户端在目标 maid 实体上 {@code addChatBubble} → TLM ChatBubbleRenderer 渲染。
 * 不绑 haqi — 任何管道/逻辑都可复用。
 *
 * <p>注: TLM 自身服务端 addChatBubble 已走 SynchedEntityData force 同步
 * (ChatBubbleManager.forceUpdateChatBubble, set(..., true) 实证);
 * 本包提供显式通用通道 + 客户端本地构造 (自定义 data 免跨端序列化依赖)。
 */
//? if 1.20.1 {
public record MaidChatBubblePacket(int maidId, byte emojiType) {
//?} else {
public record MaidChatBubblePacket(int maidId, byte emojiType) implements CustomPacketPayload {
//?}

    public static void encode(MaidChatBubblePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId());
        buf.writeByte(msg.emojiType());
    }

    public static MaidChatBubblePacket decode(FriendlyByteBuf buf) {
        return new MaidChatBubblePacket(buf.readInt(), buf.readByte());
    }

//? if 1.20.1 {
    public static void handle(MaidChatBubblePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> MaidBubbleClientHandler.apply(msg.maidId(), msg.emojiType()));
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<MaidChatBubblePacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_chat_bubble"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, MaidChatBubblePacket> STREAM_CODEC =
        PacketCodecs.wrap(MaidChatBubblePacket::encode, MaidChatBubblePacket::decode);

    public static void handlePayload(MaidChatBubblePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> MaidBubbleClientHandler.apply(msg.maidId(), msg.emojiType()));
    }
//?}

    // 2026-09-21 架构修环 (铁律 §1): 本包**不再引用 chatbubble 层** ——
    //   原 sendToTracking(EntityMaid, MaidEmojiType) 已删 (全仓 0 调用方 = 死方法; 活入口是
    //   chatbubble/MaidEmojiApi.send(...), 方向 chatbubble→network 才是 §1 允许的 ✓)。
    //   客户端落地改由 chatbubble 侧注册 sink (MaidEmojiClientSink) ⇒ 本层不 import 表现层 ✓
}

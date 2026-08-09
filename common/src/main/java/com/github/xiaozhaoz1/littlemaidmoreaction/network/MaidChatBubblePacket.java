package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidEmojiBubbleData;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidEmojiType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}
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
public final class MaidChatBubblePacket {
//?} else {
public final class MaidChatBubblePacket implements CustomPacketPayload {
//?}
    private final int maidId;
    private final byte emojiType;

    public MaidChatBubblePacket(int maidId, byte emojiType) {
        this.maidId = maidId;
        this.emojiType = emojiType;
    }

    public static void encode(MaidChatBubblePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId);
        buf.writeByte(msg.emojiType);
    }

    public static MaidChatBubblePacket decode(FriendlyByteBuf buf) {
        return new MaidChatBubblePacket(buf.readInt(), buf.readByte());
    }

//? if 1.20.1 {
    public static void handle(MaidChatBubblePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<MaidChatBubblePacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_chat_bubble"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, MaidChatBubblePacket> STREAM_CODEC = StreamCodec.of(
        (ByteBuf buf, MaidChatBubblePacket msg) -> encode(msg, (FriendlyByteBuf) buf),
        (ByteBuf buf) -> decode((FriendlyByteBuf) buf));

    public static void handlePayload(MaidChatBubblePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> handleClient(msg));
    }
//?}

    /** 客户端: 在目标女仆实体上直接加表情气泡 (渲染走 TLM ChatBubbleRenderer) */
    @OnlyIn(Dist.CLIENT)
    private static void handleClient(MaidChatBubblePacket msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(msg.maidId) instanceof EntityMaid maid) {
            maid.getChatBubbleManager().addChatBubble(MaidEmojiBubbleData.create(MaidEmojiType.byId(msg.emojiType)));
        }
    }

    /** 向追踪指定女仆的所有客户端发送表情气泡请求 (通用入口, 任意管道可调) */
    public static void sendToTracking(EntityMaid maid, MaidEmojiType type) {
        if (maid.level().isClientSide()) return;
        LmaNetwork.sender.sendToTrackingEntity(maid, new MaidChatBubblePacket(maid.getId(), type.id()));
    }
}

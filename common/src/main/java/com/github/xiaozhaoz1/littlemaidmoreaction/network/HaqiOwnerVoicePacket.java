package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.client.PecoHaqiSoundPlayer;
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
 * 对主人哈气语音触发包 (v79.20) — 服务端通知客户端播放 littlemaid_peco 声音包的 idle 子集。
 *
 * <p>服务端不能直接播 peco 包声音 (ogg 只在客户端文件系统, TLM 自定义声音包机制),
 * 也无法指定文件 (TLM {@code SoundCache.getBuffer} 全随机)。因此只传 maidId + volume,
 * 客户端 {@link PecoHaqiSoundPlayer} 按 11 文件子集随机读取并播放。
 */
//? if 1.20.1 {
public record HaqiOwnerVoicePacket(int maidId, float volume) {
//?} else {
public record HaqiOwnerVoicePacket(int maidId, float volume) implements CustomPacketPayload {
//?}

    public static void encode(HaqiOwnerVoicePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId());
        buf.writeFloat(msg.volume());
    }

    public static HaqiOwnerVoicePacket decode(FriendlyByteBuf buf) {
        return new HaqiOwnerVoicePacket(buf.readInt(), buf.readFloat());
    }

//? if 1.20.1 {
    public static void handle(HaqiOwnerVoicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<HaqiOwnerVoicePacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "haqi_owner_voice"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, HaqiOwnerVoicePacket> STREAM_CODEC =
        PacketCodecs.wrap(HaqiOwnerVoicePacket::encode, HaqiOwnerVoicePacket::decode);

    public static void handlePayload(HaqiOwnerVoicePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> handleClient(msg));
    }
//?}

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(HaqiOwnerVoicePacket msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(msg.maidId()) instanceof EntityMaid maid) {
            PecoHaqiSoundPlayer.play(maid, msg.volume());
        }
    }

    /** 向追踪指定女仆的所有客户端发送对主人哈气语音请求 */
    public static void sendToTracking(EntityMaid maid, float volume) {
        if (maid.level().isClientSide()) return;
        LmaNetwork.sender.sendToTrackingEntity(maid, new HaqiOwnerVoicePacket(maid.getId(), volume));
    }
}

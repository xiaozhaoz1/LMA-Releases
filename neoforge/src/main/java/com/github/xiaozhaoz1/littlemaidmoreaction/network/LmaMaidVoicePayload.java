package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 假人随机语音 (v75.3, S→C) — 桥定时触发: 客户端从女仆语音包 (soundPackId) 取音频,
 * 在假人位置播放 (仿 TLM MaidSoundInstance 但绑定假人 — PlayMaidSoundPackage 客户端只认 EntityMaid)。
 * soundEvent 用 TLM 通用 maid 语音键 (maid.ai.hurt_player), 客户端 SoundCache.getBuffer 随机取音频。
 */
public record LmaMaidVoicePayload(UUID companionUuid, String soundPackId, ResourceLocation soundEvent)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LmaMaidVoicePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    LittleMaidMoreAction.MOD_ID, "maid_voice"));

    public static final StreamCodec<ByteBuf, LmaMaidVoicePayload> STREAM_CODEC = StreamCodec.of(
            (ByteBuf buf, LmaMaidVoicePayload msg) -> {
                FriendlyByteBuf fb = (FriendlyByteBuf) buf;
                fb.writeUUID(msg.companionUuid());
                fb.writeUtf(msg.soundPackId());
                fb.writeResourceLocation(msg.soundEvent());
            },
            (ByteBuf buf) -> {
                FriendlyByteBuf fb = (FriendlyByteBuf) buf;
                return new LmaMaidVoicePayload(fb.readUUID(), fb.readUtf(), fb.readResourceLocation());
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handlePayload(LmaMaidVoicePayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.github.xiaozhaoz1.littlemaidmoreaction.compat.numen.NumenVoicePlayback.play(msg));
    }
}

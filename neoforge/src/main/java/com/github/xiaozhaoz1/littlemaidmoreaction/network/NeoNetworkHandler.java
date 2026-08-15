package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * NeoForge 1.21.1 payload 网络注册 — 对应 forge 侧 SimpleChannel 14 包 (ID 0,2,3,5,6,7,8,9-15),
 * 共 16 条注册 (ConfigSync 双向各占 TYPE)。
 *
 * <p>批次 A: 循环消费 {@link PacketRegistry#DEFS} (单一事实源) — v79.51 前 16 条 playTo 手写,
 * 仅注释对应 forge ID (R-03/R-05)。注册 map 键 = 清单 name; 值为类型化 lambda (TYPE/STREAM_CODEC/
 * handlePayload 引用), 方向由清单 {@link PacketDef#direction()} 驱动选 playToClient/playToServer。</p>
 *
 * <p>协议字节不变: TYPE 字符串原样保留; 各包 STREAM_CODEC/handlePayload 本体零改动。</p>
 */
public final class NeoNetworkHandler {

    private static final String VERSION = "1.0";

    /** name → playToClient/playToServer 调用 (类型化引用 — 错配在编译期暴露) */
    private static final Map<String, BiConsumer<PayloadRegistrar, PacketDef>> REGISTRATIONS = Map.ofEntries(
            Map.entry("anim_sync", (reg, def) -> play(reg, def, LmaAnimSyncMessage.TYPE, LmaAnimSyncMessage.STREAM_CODEC, LmaAnimSyncMessage::handlePayload)),
            Map.entry("interact_trigger", (reg, def) -> play(reg, def, InteractTriggerPacket.TYPE, InteractTriggerPacket.STREAM_CODEC, InteractTriggerPacket::handlePayload)),
            Map.entry("task_config_action", (reg, def) -> play(reg, def, TaskConfigActionPacket.TYPE, TaskConfigActionPacket.STREAM_CODEC, TaskConfigActionPacket::handlePayload)),
            Map.entry("request_task_config", (reg, def) -> play(reg, def, RequestTaskConfigPacket.TYPE, RequestTaskConfigPacket.STREAM_CODEC, RequestTaskConfigPacket::handlePayload)),
            Map.entry("reply_task_config", (reg, def) -> play(reg, def, ReplyTaskConfigPacket.TYPE, ReplyTaskConfigPacket.STREAM_CODEC, ReplyTaskConfigPacket::handlePayload)),
            // ConfigSync 同 class 双 TYPE (清单 R-02/R-05): config_sync (C→S) / config_sync_s2c (S→C) — outboundType 字段不动
            Map.entry("config_sync", (reg, def) -> play(reg, def, ConfigSyncPacket.TYPE, ConfigSyncPacket.STREAM_CODEC, ConfigSyncPacket::handlePayload)),
            Map.entry("config_sync_s2c", (reg, def) -> play(reg, def, ConfigSyncPacket.TYPE_S2C, ConfigSyncPacket.STREAM_CODEC, ConfigSyncPacket::handlePayload)),
            // neoforge 独有 (清单 neoOnly, Numen 兼容模块仅 1.21.1 — COMMON.md §10 隔离对)
            Map.entry("numen_companions", (reg, def) -> play(reg, def, NumenCompanionSyncPayload.TYPE, NumenCompanionSyncPayload.STREAM_CODEC, NumenCompanionSyncPayload::handlePayload)),
            Map.entry("maid_voice", (reg, def) -> play(reg, def, LmaMaidVoicePayload.TYPE, LmaMaidVoicePayload.STREAM_CODEC, LmaMaidVoicePayload::handlePayload)),
            Map.entry("anim_file_sync", (reg, def) -> play(reg, def, AnimFileSyncPacket.TYPE, AnimFileSyncPacket.STREAM_CODEC, AnimFileSyncPacket::handlePayload)),
            Map.entry("haqi_owner_voice", (reg, def) -> play(reg, def, HaqiOwnerVoicePacket.TYPE, HaqiOwnerVoicePacket.STREAM_CODEC, HaqiOwnerVoicePacket::handlePayload)),
            Map.entry("maid_chat_bubble", (reg, def) -> play(reg, def, MaidChatBubblePacket.TYPE, MaidChatBubblePacket.STREAM_CODEC, MaidChatBubblePacket::handlePayload)),
            Map.entry("maid_codex_screen", (reg, def) -> play(reg, def, MaidCodexScreenPacket.TYPE, MaidCodexScreenPacket.STREAM_CODEC, MaidCodexScreenPacket::handlePayload)),
            Map.entry("maid_env_sense_toggle", (reg, def) -> play(reg, def, MaidEnvSenseTogglePacket.TYPE, MaidEnvSenseTogglePacket.STREAM_CODEC, MaidEnvSenseTogglePacket::handlePayload)),
            Map.entry("maid_list_query", (reg, def) -> play(reg, def, MaidListQueryPacket.TYPE, MaidListQueryPacket.STREAM_CODEC, MaidListQueryPacket::handlePayload)),
            Map.entry("maid_list_response", (reg, def) -> play(reg, def, MaidListResponsePacket.TYPE, MaidListResponsePacket.STREAM_CODEC, MaidListResponsePacket::handlePayload))
    );

    /** 清单驱动注册 (RegisterPayloadHandlersEvent); 漂移 (缺/幽灵条目) fail-fast */
    public static void registerPacket(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(VERSION);
        PacketRegistry.validatePlatformNames(REGISTRATIONS.keySet(), "neoforge");
        for (PacketDef def : PacketRegistry.DEFS) {
            BiConsumer<PayloadRegistrar, PacketDef> registration = REGISTRATIONS.get(def.name());
            if (registration == null) {
                throw new IllegalStateException("[LMA] neoforge 网络注册表缺条目: " + def.name());
            }
            registration.accept(registrar, def);
        }
    }

    /** 方向由清单驱动: C2S → playToServer / S2C → playToClient (同 TYPE 只能注册一次 — 双向靠双 TYPE) */
    private static <T extends CustomPacketPayload> void play(PayloadRegistrar registrar, PacketDef def,
                                                             CustomPacketPayload.Type<T> type, StreamCodec<ByteBuf, T> codec,
                                                             IPayloadHandler<T> handler) {
        if (def.direction() == PacketDef.Direction.C2S) {
            registrar.playToServer(type, codec, handler);
        } else {
            registrar.playToClient(type, codec, handler);
        }
    }

    private NeoNetworkHandler() {
    }
}

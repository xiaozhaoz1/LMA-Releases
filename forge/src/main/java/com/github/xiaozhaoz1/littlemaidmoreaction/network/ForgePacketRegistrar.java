package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Forge 1.20.1 网络包注册驱动 (批次 A) — 循环消费 {@link PacketRegistry#DEFS} 单点收口
 * (v79.51 前: commonSetup 14 条 registerMessage 手写, ID 硬编码 — R-02/R-04)。
 *
 * <p>注册 map 键 = 清单 name; 值为类型化 lambda (codec/handler 引用, 方向由清单
 * {@link PacketDef#direction()} 驱动) — forge/neoforge handler 签名不同, common 不可引平台
 * 类型, 故 handler 分派留在平台驱动侧。加新包 = 清单登记 + 本 map 一行 + neoforge map 一行。</p>
 *
 * <p>协议字节不变: 各包 encode/decode/handle 本体零改动; ID 值原样保留 (0,2,3,5-15)。</p>
 */
public final class ForgePacketRegistrar {

    /** name → registerMessage 调用 (类型化引用 — 错配在编译期暴露) */
    private static final Map<String, BiConsumer<SimpleChannel, PacketDef>> REGISTRATIONS = Map.ofEntries(
            Map.entry("anim_sync", (ch, def) -> ch.registerMessage(def.id(), LmaAnimSyncMessage.class,
                    LmaAnimSyncMessage::encode, LmaAnimSyncMessage::decode, LmaAnimSyncMessage::handle, dir(def.direction()))),
            Map.entry("interact_trigger", (ch, def) -> ch.registerMessage(def.id(), InteractTriggerPacket.class,
                    InteractTriggerPacket::encode, InteractTriggerPacket::decode, InteractTriggerPacket::handle, dir(def.direction()))),
            Map.entry("task_config_action", (ch, def) -> ch.registerMessage(def.id(), TaskConfigActionPacket.class,
                    TaskConfigActionPacket::encode, TaskConfigActionPacket::decode, TaskConfigActionPacket::handle, dir(def.direction()))),
            Map.entry("request_task_config", (ch, def) -> ch.registerMessage(def.id(), RequestTaskConfigPacket.class,
                    RequestTaskConfigPacket::encode, RequestTaskConfigPacket::decode, RequestTaskConfigPacket::handle, dir(def.direction()))),
            Map.entry("reply_task_config", (ch, def) -> ch.registerMessage(def.id(), ReplyTaskConfigPacket.class,
                    ReplyTaskConfigPacket::encode, ReplyTaskConfigPacket::decode, ReplyTaskConfigPacket::handle, dir(def.direction()))),
            // ConfigSync 同 class 双 ID (清单 R-02): 7=C2S / 8=S2C — 方向由清单驱动, 双向自然成立
            Map.entry("config_sync", (ch, def) -> ch.registerMessage(def.id(), ConfigSyncPacket.class,
                    ConfigSyncPacket::encode, ConfigSyncPacket::decode, ConfigSyncPacket::handle, dir(def.direction()))),
            Map.entry("config_sync_s2c", (ch, def) -> ch.registerMessage(def.id(), ConfigSyncPacket.class,
                    ConfigSyncPacket::encode, ConfigSyncPacket::decode, ConfigSyncPacket::handle, dir(def.direction()))),
            Map.entry("anim_file_sync", (ch, def) -> ch.registerMessage(def.id(), AnimFileSyncPacket.class,
                    AnimFileSyncPacket::encode, AnimFileSyncPacket::decode, AnimFileSyncPacket::handle, dir(def.direction()))),
            Map.entry("haqi_owner_voice", (ch, def) -> ch.registerMessage(def.id(), HaqiOwnerVoicePacket.class,
                    HaqiOwnerVoicePacket::encode, HaqiOwnerVoicePacket::decode, HaqiOwnerVoicePacket::handle, dir(def.direction()))),
            Map.entry("maid_chat_bubble", (ch, def) -> ch.registerMessage(def.id(), MaidChatBubblePacket.class,
                    MaidChatBubblePacket::encode, MaidChatBubblePacket::decode, MaidChatBubblePacket::handle, dir(def.direction()))),
            Map.entry("maid_list_query", (ch, def) -> ch.registerMessage(def.id(), MaidListQueryPacket.class,
                    MaidListQueryPacket::encode, MaidListQueryPacket::decode, MaidListQueryPacket::handle, dir(def.direction()))),
            Map.entry("maid_list_response", (ch, def) -> ch.registerMessage(def.id(), MaidListResponsePacket.class,
                    MaidListResponsePacket::encode, MaidListResponsePacket::decode, MaidListResponsePacket::handle, dir(def.direction()))),
            Map.entry("maid_codex_screen", (ch, def) -> ch.registerMessage(def.id(), MaidCodexScreenPacket.class,
                    MaidCodexScreenPacket::encode, MaidCodexScreenPacket::decode, MaidCodexScreenPacket::handle, dir(def.direction()))),
            Map.entry("maid_env_sense_toggle", (ch, def) -> ch.registerMessage(def.id(), MaidEnvSenseTogglePacket.class,
                    MaidEnvSenseTogglePacket::encode, MaidEnvSenseTogglePacket::decode, MaidEnvSenseTogglePacket::handle, dir(def.direction())))
    );

    /** 清单驱动注册 — commonSetup enqueueWork 内调用; 漂移 (缺/幽灵条目) fail-fast */
    public static void registerAll(SimpleChannel channel) {
        PacketRegistry.validatePlatformNames(REGISTRATIONS.keySet(), "forge");
        for (PacketDef def : PacketRegistry.DEFS) {
            if (!def.forgeVisible()) {
                continue;
            }
            BiConsumer<SimpleChannel, PacketDef> registration = REGISTRATIONS.get(def.name());
            if (registration == null) {
                throw new IllegalStateException("[LMA] forge 网络注册表缺条目: " + def.name());
            }
            registration.accept(channel, def);
        }
        LittleMaidMoreAction.LOGGER.info("[LMA] 网络通道初始化完成 ({} packets)", REGISTRATIONS.size());
    }

    /** 方向映射: C2S → PLAY_TO_SERVER / S2C → PLAY_TO_CLIENT */
    private static Optional<NetworkDirection> dir(PacketDef.Direction direction) {
        return Optional.of(direction == PacketDef.Direction.C2S
                ? NetworkDirection.PLAY_TO_SERVER : NetworkDirection.PLAY_TO_CLIENT);
    }

    private ForgePacketRegistrar() {
    }
}

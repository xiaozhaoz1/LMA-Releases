package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.ConfigSyncPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.HaqiOwnerVoicePacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidChatBubblePacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.InteractTriggerPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.LmaAnimSyncMessage;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.ReplyTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.TaskConfigActionPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge 1.21.1 payload 网络注册 — 对应 forge 侧 SimpleChannel 7 包 (ID 0,2,3,5,6,7,8)。
 */
public final class NeoNetworkHandler {

    private static final String VERSION = "1.0";

    public static void registerPacket(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(VERSION);
        // S→C: 动画数据同步 (forge ID 0)
        registrar.playToClient(LmaAnimSyncMessage.TYPE, LmaAnimSyncMessage.STREAM_CODEC, LmaAnimSyncMessage::handlePayload);
        // C→S: 任务手动触发 (forge ID 2)
        registrar.playToServer(InteractTriggerPacket.TYPE, InteractTriggerPacket.STREAM_CODEC, InteractTriggerPacket::handlePayload);
        // C→S: 任务配置动作 (forge ID 3)
        registrar.playToServer(TaskConfigActionPacket.TYPE, TaskConfigActionPacket.STREAM_CODEC, TaskConfigActionPacket::handlePayload);
        // C→S: 任务配置请求 (forge ID 5)
        registrar.playToServer(RequestTaskConfigPacket.TYPE, RequestTaskConfigPacket.STREAM_CODEC, RequestTaskConfigPacket::handlePayload);
        // S→C: 任务配置响应 (forge ID 6)
        registrar.playToClient(ReplyTaskConfigPacket.TYPE, ReplyTaskConfigPacket.STREAM_CODEC, ReplyTaskConfigPacket::handlePayload);
        // 配置同步 (forge ID 7 C→S + ID 8 S→C, 同包双向) — NeoForge 同 TYPE 只能注册一次, 双向需双 TYPE
        registrar.playToServer(ConfigSyncPacket.TYPE, ConfigSyncPacket.STREAM_CODEC, ConfigSyncPacket::handlePayload);
        registrar.playToClient(ConfigSyncPacket.TYPE_S2C, ConfigSyncPacket.STREAM_CODEC, ConfigSyncPacket::handlePayload);
        // S→C: Numen 假人绑定集 (v74/v75: provider/voice 绑定 + 孤儿环清理)
        registrar.playToClient(NumenCompanionSyncPayload.TYPE, NumenCompanionSyncPayload.STREAM_CODEC, NumenCompanionSyncPayload::handlePayload);
        // S→C: 假人随机语音 (v75.3)
        registrar.playToClient(LmaMaidVoicePayload.TYPE, LmaMaidVoicePayload.STREAM_CODEC, LmaMaidVoicePayload::handlePayload);
        // S→C: 动画文件同步 (v79.18, 专用服务器自定义动画文件推送 — 纯 S2C 单 TYPE)
        registrar.playToClient(AnimFileSyncPacket.TYPE, AnimFileSyncPacket.STREAM_CODEC, AnimFileSyncPacket::handlePayload);
        // S→C: 对主人哈气语音 (v79.20, maidId+volume → 客户端 peco 包 idle 子集随机播放)
        registrar.playToClient(HaqiOwnerVoicePacket.TYPE, HaqiOwnerVoicePacket.STREAM_CODEC, HaqiOwnerVoicePacket::handlePayload);
        // S→C: 女仆表情气泡通用包 (v79.20, maidId+表情类型 → 客户端 maid 实体上加气泡)
        registrar.playToClient(MaidChatBubblePacket.TYPE, MaidChatBubblePacket.STREAM_CODEC, MaidChatBubblePacket::handlePayload);
        // v79.25.2: 女仆列表查询 (C→S, 服务端全维度扫描) + 响应 (S→C)
        registrar.playToServer(MaidListQueryPacket.TYPE, MaidListQueryPacket.STREAM_CODEC, MaidListQueryPacket::handlePayload);
        registrar.playToClient(MaidListResponsePacket.TYPE, MaidListResponsePacket.STREAM_CODEC, MaidListResponsePacket::handlePayload);
    }

    private NeoNetworkHandler() {}
}

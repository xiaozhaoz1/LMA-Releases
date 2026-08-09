package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.dwinovo.numen.client.agent.AgentLoopRegistry;
import com.dwinovo.numen.client.agent.NumenRoster;
import com.dwinovo.numen.client.voice.VoiceLibrary;
import com.dwinovo.numen.agent.llm.ProviderLibrary;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Numen 假人绑定集广播 (S→C, v74/v75) — provider/voice 绑定 + 孤儿环清理。
 *
 * <p>桥每 20 tick 广播绑定集 (uuid + provider/voice), 客户端:
 * ① 仅 owner 客户端 (uuid ∈ NumenRoster) → 按名称 resolve ProviderLibrary/VoiceLibrary
 * 条目 → assign (JSON 持久, 下回合生效);
 * ② v74.1: 消失的绑定 (假人销毁/面板删除) → dispose 孤儿 LLM 环
 * (prev 集合跟踪, 仅 owner 名下 — 防幽灵聊天 + 烧 token)。
 *
 * <p>v75: 删渲染隐藏用途 (假人可见, YSM 模型); 假人 SHELVED (女仆在石板) 时不广播 —
 * LLM 环靠 summon 时绑定 + Numen 全局默认 provider。
 * 纯 JDK + neoforge API, 客户端类引用仅在 handlePayload 方法体 (懒解析, 服务端不执行)。</p>
 */
public final class NumenCompanionSyncPayload implements CustomPacketPayload {

    /** 一个假人绑定: uuid + 配置的 provider/voice 名称 (空串 = 不绑定) */
    public record Binding(UUID uuid, String provider, String voice) {}

    public static final CustomPacketPayload.Type<NumenCompanionSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    LittleMaidMoreAction.MOD_ID, "numen_companions"));

    /** 上次广播的绑定集 (客户端, dispose 对比基准) */
    private static final Set<UUID> PREV_BOUND = new HashSet<>();

    private final List<Binding> bindings;

    public NumenCompanionSyncPayload(List<Binding> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    private static void encode(FriendlyByteBuf buf, NumenCompanionSyncPayload msg) {
        buf.writeInt(msg.bindings.size());
        for (Binding b : msg.bindings) {
            buf.writeUUID(b.uuid());
            buf.writeUtf(b.provider());
            buf.writeUtf(b.voice());
        }
    }

    private static NumenCompanionSyncPayload decode(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<Binding> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Binding(buf.readUUID(), buf.readUtf(), buf.readUtf()));
        }
        return new NumenCompanionSyncPayload(list);
    }

    public static final StreamCodec<ByteBuf, NumenCompanionSyncPayload> STREAM_CODEC = StreamCodec.of(
            (ByteBuf buf, NumenCompanionSyncPayload msg) -> encode((FriendlyByteBuf) buf, msg),
            (ByteBuf buf) -> decode((FriendlyByteBuf) buf));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handlePayload(NumenCompanionSyncPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            NumenRoster roster = NumenRoster.instance();
            Set<UUID> current = new HashSet<>();
            for (Binding b : msg.bindings) {
                current.add(b.uuid());
                if (roster.name(b.uuid()) == null) continue;   // 非本玩家 companion
                if (!b.provider().isEmpty()) {
                    String id = findProviderIdByName(b.provider());
                    if (id != null) {
                        ProviderLibrary.instance().assign(b.uuid(), id);
                        AgentLoopRegistry.getOrCreate(b.uuid()).setProviderEntry(id);
                    }
                }
                if (!b.voice().isEmpty()) {
                    String id = findVoiceIdByName(b.voice());
                    if (id != null) VoiceLibrary.instance().assign(b.uuid(), id);
                }
            }
            // 消失的绑定 → dispose 孤儿 LLM 环 (仅 owner 名下)
            for (UUID gone : PREV_BOUND) {
                if (!current.contains(gone) && roster.name(gone) != null) {
                    AgentLoopRegistry.dispose(gone);
                }
            }
            PREV_BOUND.clear();
            PREV_BOUND.addAll(current);
        });
    }

    /** ProviderLibrary 按名称匹配条目 id (名称 = 玩家在 Numen G 面板创建) */
    private static String findProviderIdByName(String name) {
        for (ProviderLibrary.Entry e : ProviderLibrary.instance().list()) {
            if (name.equals(e.name())) return e.id();
        }
        return null;
    }

    /** VoiceLibrary 按名称匹配条目 id */
    private static String findVoiceIdByName(String name) {
        for (VoiceLibrary.Entry e : VoiceLibrary.instance().list()) {
            if (name.equals(e.name())) return e.id();
        }
        return null;
    }
}

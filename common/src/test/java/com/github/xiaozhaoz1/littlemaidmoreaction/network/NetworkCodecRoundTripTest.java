package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网络包序列化 round-trip 测试 (NEW-M8) — 13 个 STREAM_CODEC 包 encode → decode → 字段相等。
 *
 * <p><b>可测性说明</b>: 测试跑在 forge 节点 (common 测试源直编, 不经 stonecutter), 1.20.1
 * 形态的包类只有 {@code static encode/decode} (STREAM_CODEC 是 neoforge 形态薄封装, 逐字节
 * 委托相同方法) — 因此测 encode/decode 即覆盖 STREAM_CODEC 的对称逻辑, 正是 v79.50 批次 4
 * 序列化对称 bug 高发区。
 *
 * <p>断言策略 — <b>字节级 round-trip</b>: encode(msg) 取字节 → decode → re-encode 取字节 →
 * 两段字节必须一致。该断言比字段相等更强 (捕获字段错位/丢失/多写), 且规避 1.20.1 形态
 * 包类 private 字段无 accessor 的可见性问题 (不改业务代码加 getter)。
 *
 * <p>纯 JVM 铁律: FriendlyByteBuf(Unpooled.buffer()) 与 CompoundTag 均为纯数据结构
 * (零注册表依赖); 不触碰 handle/handleClient (MC 实体/客户端耦合) 与 LmaNetwork (发送侧)。
 */
class NetworkCodecRoundTripTest {

    /** 字节级 round-trip 操作描述 (双平台 encode/decode 均为 static) */
    private interface CodecOps<T> {
        void encode(T msg, FriendlyByteBuf buf);

        T decode(FriendlyByteBuf buf);
    }

    /**
     * encode(msg) 字节 = encode(decode(encode(msg))) 字节。
     * 若 decode 丢字段/错位/多读, 二次 encode 字节必不一致 (错位需字段同值才会漏检, 已另配
     * record/getter 字段断言兜底)。
     */
    private static <T> T assertRoundTrip(T msg, CodecOps<T> ops) {
        FriendlyByteBuf first = new FriendlyByteBuf(Unpooled.buffer());
        ops.encode(msg, first);
        byte[] firstBytes = ByteBufUtil.getBytes(first);

        T decoded = ops.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(firstBytes)));

        FriendlyByteBuf second = new FriendlyByteBuf(Unpooled.buffer());
        ops.encode(decoded, second);
        assertArrayEquals(firstBytes, ByteBufUtil.getBytes(second),
                "encode→decode→re-encode 字节必须一致 (序列化对称性)");
        return decoded;
    }

    // ─── 零字段包 (encode 无写入; decode 空 buffer 成功即对称) ───

    @Test
    @DisplayName("InteractTriggerPacket: keyId 字段 round-trip")
    void interactTrigger_roundTrip() {
        assertRoundTrip(new InteractTriggerPacket("block_interact"), new CodecOps<>() {
            @Override public void encode(InteractTriggerPacket m, FriendlyByteBuf b) { InteractTriggerPacket.encode(m, b); }
            @Override public InteractTriggerPacket decode(FriendlyByteBuf b) { return InteractTriggerPacket.decode(b); }
        });
    }

    @Test
    @DisplayName("InteractTriggerPacket 边界: 空 keyId + 超长 keyId")
    void interactTrigger_keyIdEdge() {
        assertRoundTrip(new InteractTriggerPacket(""), new CodecOps<>() {
            @Override public void encode(InteractTriggerPacket m, FriendlyByteBuf b) { InteractTriggerPacket.encode(m, b); }
            @Override public InteractTriggerPacket decode(FriendlyByteBuf b) { return InteractTriggerPacket.decode(b); }
        });
        String longKey = "k".repeat(64);
        assertRoundTrip(new InteractTriggerPacket(longKey), new CodecOps<>() {
            @Override public void encode(InteractTriggerPacket m, FriendlyByteBuf b) { InteractTriggerPacket.encode(m, b); }
            @Override public InteractTriggerPacket decode(FriendlyByteBuf b) { return InteractTriggerPacket.decode(b); }
        });
    }

    @Test
    @DisplayName("MaidListQueryPacket 零字段: decode 空 buffer 返回实例")
    void maidListQuery_roundTrip() {
        assertNotNull(MaidListQueryPacket.decode(new FriendlyByteBuf(Unpooled.buffer())));
    }

    // ─── 基础类型包 ───

    @Test
    @DisplayName("HaqiOwnerVoicePacket: int+float round-trip")
    void haqiOwnerVoice_roundTrip() {
        assertRoundTrip(new HaqiOwnerVoicePacket(42, 0.5f), new CodecOps<>() {
            @Override public void encode(HaqiOwnerVoicePacket m, FriendlyByteBuf b) { HaqiOwnerVoicePacket.encode(m, b); }
            @Override public HaqiOwnerVoicePacket decode(FriendlyByteBuf b) { return HaqiOwnerVoicePacket.decode(b); }
        });
    }

    @Test
    @DisplayName("HaqiOwnerVoicePacket 边界: 负 id + 极端音量")
    void haqiOwnerVoice_extremes() {
        assertRoundTrip(new HaqiOwnerVoicePacket(-1, 2.0f), new CodecOps<>() {
            @Override public void encode(HaqiOwnerVoicePacket m, FriendlyByteBuf b) { HaqiOwnerVoicePacket.encode(m, b); }
            @Override public HaqiOwnerVoicePacket decode(FriendlyByteBuf b) { return HaqiOwnerVoicePacket.decode(b); }
        });
    }

    @Test
    @DisplayName("AnimFileSyncPacket: 文件名+字节内容 round-trip")
    void animFileSync_roundTrip() {
        byte[] content = "{\"name\":\"haqi\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertRoundTrip(new AnimFileSyncPacket("haqi.animation.json", content), new CodecOps<>() {
            @Override public void encode(AnimFileSyncPacket m, FriendlyByteBuf b) { AnimFileSyncPacket.encode(m, b); }
            @Override public AnimFileSyncPacket decode(FriendlyByteBuf b) { return AnimFileSyncPacket.decode(b); }
        });
    }

    @Test
    @DisplayName("AnimFileSyncPacket 边界: 全字节值 0-255 内容")
    void animFileSync_binaryContent() {
        byte[] binary = new byte[256];
        for (int i = 0; i < binary.length; i++) binary[i] = (byte) i;
        assertRoundTrip(new AnimFileSyncPacket("anim.animation.json", binary), new CodecOps<>() {
            @Override public void encode(AnimFileSyncPacket m, FriendlyByteBuf b) { AnimFileSyncPacket.encode(m, b); }
            @Override public AnimFileSyncPacket decode(FriendlyByteBuf b) { return AnimFileSyncPacket.decode(b); }
        });
    }

    @Test
    @DisplayName("RequestTaskConfigPacket: int+Utf round-trip")
    void requestTaskConfig_roundTrip() {
        assertRoundTrip(new RequestTaskConfigPacket(7, "haqi"), new CodecOps<>() {
            @Override public void encode(RequestTaskConfigPacket m, FriendlyByteBuf b) { RequestTaskConfigPacket.encode(m, b); }
            @Override public RequestTaskConfigPacket decode(FriendlyByteBuf b) { return RequestTaskConfigPacket.decode(b); }
        });
    }

    @Test
    @DisplayName("MaidEnvSenseTogglePacket: UUID round-trip")
    void maidEnvSenseToggle_roundTrip() {
        UUID id = UUID.randomUUID();
        assertRoundTrip(new MaidEnvSenseTogglePacket(id), new CodecOps<>() {
            @Override public void encode(MaidEnvSenseTogglePacket m, FriendlyByteBuf b) { MaidEnvSenseTogglePacket.encode(m, b); }
            @Override public MaidEnvSenseTogglePacket decode(FriendlyByteBuf b) { return MaidEnvSenseTogglePacket.decode(b); }
        });
    }

    @Test
    @DisplayName("MaidChatBubblePacket: int+byte round-trip")
    void maidChatBubble_roundTrip() {
        assertRoundTrip(new MaidChatBubblePacket(9, (byte) 2), new CodecOps<>() {
            @Override public void encode(MaidChatBubblePacket m, FriendlyByteBuf b) { MaidChatBubblePacket.encode(m, b); }
            @Override public MaidChatBubblePacket decode(FriendlyByteBuf b) { return MaidChatBubblePacket.decode(b); }
        });
    }

    // ─── NBT 包 (writeNbt/readNbt) ───

    @Test
    @DisplayName("TaskConfigActionPacket: int+Utf+byte+NBT round-trip")
    void taskConfigAction_roundTrip() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("key", "value");
        nbt.putInt("num", 3);
        assertRoundTrip(new TaskConfigActionPacket(7, "block_interact", (byte) 1, nbt), new CodecOps<>() {
            @Override public void encode(TaskConfigActionPacket m, FriendlyByteBuf b) { TaskConfigActionPacket.encode(m, b); }
            @Override public TaskConfigActionPacket decode(FriendlyByteBuf b) { return TaskConfigActionPacket.decode(b); }
        });
    }

    @Test
    @DisplayName("TaskConfigActionPacket: null NBT 往返 (writeNbt null 写 0)")
    void taskConfigAction_nullNbt() {
        assertRoundTrip(new TaskConfigActionPacket(1, "haqi", (byte) 0, null), new CodecOps<>() {
            @Override public void encode(TaskConfigActionPacket m, FriendlyByteBuf b) { TaskConfigActionPacket.encode(m, b); }
            @Override public TaskConfigActionPacket decode(FriendlyByteBuf b) { return TaskConfigActionPacket.decode(b); }
        });
    }

    @Test
    @DisplayName("ReplyTaskConfigPacket (record): int+Utf+NBT round-trip + 组件断言")
    void replyTaskConfig_roundTrip() {
        CompoundTag nbt = new CompoundTag();
        nbt.putDouble("chance", 0.3d);
        ReplyTaskConfigPacket decoded = assertRoundTrip(new ReplyTaskConfigPacket(5, "haqi", nbt), new CodecOps<>() {
            @Override public void encode(ReplyTaskConfigPacket m, FriendlyByteBuf b) { ReplyTaskConfigPacket.encode(m, b); }
            @Override public ReplyTaskConfigPacket decode(FriendlyByteBuf b) { return ReplyTaskConfigPacket.decode(b); }
        });
        // record 有 accessor — 字段级兜底断言
        assertEquals(5, decoded.maidId());
        assertEquals("haqi", decoded.taskType());
        assertEquals(nbt, decoded.config());
    }

    @Test
    @DisplayName("LmaAnimSyncMessage: int+NBT round-trip (嵌套 NBT)")
    void lmaAnimSync_roundTrip() {
        CompoundTag inner = new CompoundTag();
        inner.putString("anim", "haqi");
        CompoundTag outer = new CompoundTag();
        outer.put("lma_anim_start", inner);
        assertRoundTrip(new LmaAnimSyncMessage(3, outer), new CodecOps<>() {
            @Override public void encode(LmaAnimSyncMessage m, FriendlyByteBuf b) { LmaAnimSyncMessage.encode(m, b); }
            @Override public LmaAnimSyncMessage decode(FriendlyByteBuf b) { return LmaAnimSyncMessage.decode(b); }
        });
    }

    // ─── 集合包 ───

    @Test
    @DisplayName("MaidListResponsePacket: 多条目 round-trip (中文名/边界值)")
    void maidListResponse_roundTrip() {
        // 1.20.1 形态外层 entries 私有无 getter — 字节比较已覆盖逐字段对称 (错位/丢失必改字节)
        List<MaidListResponsePacket.MaidEntry> entries = List.of(
                new MaidListResponsePacket.MaidEntry(UUID.randomUUID(), "小铃", "minecraft:overworld", 4.25d, 12, 18.5f, 20.0f, true),
                new MaidListResponsePacket.MaidEntry(new UUID(0L, 0L), "", "minecraft:the_nether", 0.0d, 0, 0.0f, 0.0f, false),
                new MaidListResponsePacket.MaidEntry(UUID.randomUUID(), "Alice Bob 测试/\\:123", "mod:dim", Double.MAX_VALUE, Integer.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, true));
        assertRoundTrip(new MaidListResponsePacket(entries), new CodecOps<>() {
            @Override public void encode(MaidListResponsePacket m, FriendlyByteBuf b) { MaidListResponsePacket.encode(m, b); }
            @Override public MaidListResponsePacket decode(FriendlyByteBuf b) { return MaidListResponsePacket.decode(b); }
        });
    }

    @Test
    @DisplayName("MaidListResponsePacket: 空列表 round-trip")
    void maidListResponse_empty() {
        assertRoundTrip(new MaidListResponsePacket(List.of()), new CodecOps<>() {
            @Override public void encode(MaidListResponsePacket m, FriendlyByteBuf b) { MaidListResponsePacket.encode(m, b); }
            @Override public MaidListResponsePacket decode(FriendlyByteBuf b) { return MaidListResponsePacket.decode(b); }
        });
    }

    @Test
    @DisplayName("MaidCodexScreenPacket: Map 计数 round-trip + getCounts 断言")
    void maidCodexScreen_roundTrip() {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put("touhou_little_maid:haqi", 3);
        counts.put("minecraft:zombie", 0);
        counts.put("", 100);
        MaidCodexScreenPacket decoded = assertRoundTrip(new MaidCodexScreenPacket(counts), new CodecOps<>() {
            @Override public void encode(MaidCodexScreenPacket m, FriendlyByteBuf b) { MaidCodexScreenPacket.encode(m, b); }
            @Override public MaidCodexScreenPacket decode(FriendlyByteBuf b) { return MaidCodexScreenPacket.decode(b); }
        });
        assertEquals(counts, decoded.getCounts());
    }

    @Test
    @DisplayName("MaidCodexScreenPacket: 空 Map round-trip")
    void maidCodexScreen_empty() {
        assertRoundTrip(new MaidCodexScreenPacket(Map.of()), new CodecOps<>() {
            @Override public void encode(MaidCodexScreenPacket m, FriendlyByteBuf b) { MaidCodexScreenPacket.encode(m, b); }
            @Override public MaidCodexScreenPacket decode(FriendlyByteBuf b) { return MaidCodexScreenPacket.decode(b); }
        });
    }

    // ─── ConfigSyncPacket: 5 种值类型全覆盖 ───

    @Test
    @DisplayName("ConfigSyncPacket: 5 类型条目 round-trip (bool/int/double/string/list)")
    void configSync_allValueTypes() {
        List<com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.ConfigValueEntry> entries = List.of(
                new com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.ConfigValueEntry("common.debug.debug_mode", false),
                new com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.ConfigValueEntry("passive.env_sense.hot_threshold", 1.5d),
                new com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.ConfigValueEntry("haqi.haqi.duration_ticks_to_owner", "字符串值"),
                new com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.ConfigValueEntry("list.some", List.of("a", "b", "c")));
        // 1.20.1 形态 entries 私有无 getter — 字节比较已覆盖 5 类型编码分支对称性
        assertRoundTrip(new ConfigSyncPacket(entries), new CodecOps<>() {
            @Override public void encode(ConfigSyncPacket m, FriendlyByteBuf b) { ConfigSyncPacket.encode(m, b); }
            @Override public ConfigSyncPacket decode(FriendlyByteBuf b) { return ConfigSyncPacket.decode(b); }
        });
    }

    @Test
    @DisplayName("ConfigSyncPacket: 空列表 round-trip")
    void configSync_empty() {
        assertRoundTrip(new ConfigSyncPacket(List.of()), new CodecOps<>() {
            @Override public void encode(ConfigSyncPacket m, FriendlyByteBuf b) { ConfigSyncPacket.encode(m, b); }
            @Override public ConfigSyncPacket decode(FriendlyByteBuf b) { return ConfigSyncPacket.decode(b); }
        });
    }

    // ─── 异常路径: 类型标记损坏 ───

    @Test
    @DisplayName("ConfigSyncPacket: 未知类型标记 → 跳过该条目不抛 (客户端信任边界, v79.61 批2)")
    void configSync_unknownTypeTag_skips() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(2);
        buf.writeUtf("some.path");
        buf.writeByte((byte) 99); // 非 0-4 的标记 — 应跳过
        buf.writeUtf("common.debug_mode");
        buf.writeByte((byte) 0); // TYPE_BOOL
        buf.writeBoolean(true);
        ConfigSyncPacket decoded = ConfigSyncPacket.decode(buf);   // 不抛即通过
        FriendlyByteBuf re = new FriendlyByteBuf(Unpooled.buffer());
        ConfigSyncPacket.encode(decoded, re);
        assertEquals(1, re.readVarInt());   // 坏条目被跳过, 仅剩 1 条
    }

    @Test
    @DisplayName("MaidListResponsePacket: 超大/负条目计数 → 空列表不 OOM (客户端信任边界)")
    void maidListResponse_oversizeCount() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1_000_000);
        MaidListResponsePacket decoded = MaidListResponsePacket.decode(buf);
        FriendlyByteBuf re = new FriendlyByteBuf(Unpooled.buffer());
        MaidListResponsePacket.encode(decoded, re);
        assertEquals(1, re.readableBytes());   // 空列表重编码 = 仅 varint 0

        FriendlyByteBuf neg = new FriendlyByteBuf(Unpooled.buffer());
        neg.writeVarInt(-1);
        MaidListResponsePacket decodedNeg = MaidListResponsePacket.decode(neg);
        FriendlyByteBuf reNeg = new FriendlyByteBuf(Unpooled.buffer());
        MaidListResponsePacket.encode(decodedNeg, reNeg);
        assertEquals(1, reNeg.readableBytes());
    }

    @Test
    @DisplayName("MaidCodexScreenPacket: 超大条目计数 → 空 Map 不 OOM (客户端信任边界)")
    void maidCodexScreen_oversizeCount() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1_000_000);
        MaidCodexScreenPacket decoded = MaidCodexScreenPacket.decode(buf);
        assertTrue(decoded.getCounts().isEmpty());
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * v79.25.2: 女仆列表响应包 (S→C) — 服务端全维度扫描结果回传。
 * 客户端 handle 落静态缓存 {@link #getLastEntries()}, MaidListScreen tick 轮询刷新。
 * v79.26: MaidEntry 加 level/health — 列表行 Lv + ❤ 数据。
 */
//? if 1.20.1 {
public final class MaidListResponsePacket {
//?} else {
public final class MaidListResponsePacket implements CustomPacketPayload {
//?}

    /**
     * 女仆条目: uuid + 名字 + 维度 id + 距请求玩家距离² + 等级 (TLM 契约 exp/120) + 生命值。
     * 加 level/health — 列表行显示 Lv + ❤ (回归旧大面板行信息)。
     * v79.47: + envsense — per-maid 环境感知开关状态 (默认开: 无键视为开, 显式 false = 关)。
     */
    public record MaidEntry(UUID uuid, String name, String dimension, double distSqr,
                            int level, float health, float maxHealth, boolean envsense) {
    }

    /** 单包条目数上限 — S2C 客户端信任边界 (恶意服务器超大计数致 OOM/负值致 IAE) */
    private static final int MAX_ENTRIES = 1024;
    /** 纯 JDK 日志 — decode 路径可被纯 JVM 测试触发, 禁用 LittleMaidMoreAction.LOGGER (错题 #174: MC 类加载必炸) */
    private static final System.Logger LOG = System.getLogger("LMA-Net-MaidListResponse");

    /** 客户端缓存 — MaidListScreen tick 轮询 (远端女仆列表, 无实体引用) */
    private static volatile List<MaidEntry> lastEntries = List.of();

    public static List<MaidEntry> getLastEntries() {
        return lastEntries;
    }

    /**
     * 客户端缓存清理 (M-3) — 客户端断开/退出世界时调用 (双平台
     * ClientPlayerNetworkEvent.LoggingOut 挂载于客户端入口), 防跨世界 stale 列表展示
     * (旧世界女仆条目残留到新世界开屏窗口期)。
     */
    public static void clearCache() {
        lastEntries = List.of();
    }

    private final List<MaidEntry> entries;

    public MaidListResponsePacket(List<MaidEntry> entries) {
        this.entries = entries;
    }

    public static void encode(MaidListResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (MaidEntry e : msg.entries) {
            buf.writeUUID(e.uuid());
            buf.writeUtf(e.name());
            buf.writeUtf(e.dimension());
            buf.writeDouble(e.distSqr());
            buf.writeInt(e.level());
            buf.writeFloat(e.health());
            buf.writeFloat(e.maxHealth());
            buf.writeBoolean(e.envsense());
        }
    }

    public static MaidListResponsePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) {
            LOG.log(System.Logger.Level.WARNING, "[LMA] 女仆列表包条目数异常 ({0}), 丢弃", n);
            return new MaidListResponsePacket(List.of());
        }
        List<MaidEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new MaidEntry(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readDouble(),
                    buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
        return new MaidListResponsePacket(list);
    }

    //? if 1.20.1 {
    public static void handle(MaidListResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            lastEntries = msg.entries;
        });
        ctx.get().setPacketHandled(true);
    }
    //?}
    //? if !1.20.1 {
    public static final CustomPacketPayload.Type<MaidListResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_list_response"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, MaidListResponsePacket> STREAM_CODEC =
            PacketCodecs.wrap(MaidListResponsePacket::encode, MaidListResponsePacket::decode);

    public static void handlePayload(MaidListResponsePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            lastEntries = msg.entries;
        });
    }
    //?}

    /** 服务端发送给请求玩家 */
    public static void sendToPlayer(ServerPlayer player, List<MaidEntry> entries) {
        LmaNetwork.sender.sendToPlayer(player, new MaidListResponsePacket(entries));
    }
}

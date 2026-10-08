package com.github.xiaozhaoz1.littlemaidmoreaction.network;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
//? if 1.20.1 {
import net.minecraftforge.network.NetworkEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.network.PacketDistributor;
//?} else {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 配置同步包 (v67.11, 双向) — 专用服务器上 GUI 改动生效通道。
 *
 * <p>C→S (ID 7): 客户端保存配置后推送全量快照 → 服务端校验 OP 权限 → 应用 + saveAll + 广播。
 * <p>S→C (ID 8): 服务端广播全量快照 → 客户端应用 (spec 内存值, 不写客户端 toml)。
 *
 * <p>单机/LAN host (hasSinglePlayerServer) 跳过发送 — 本地 saveAll 已生效, 零网络开销。
 */
//? if 1.20.1 {
public final class ConfigSyncPacket {
//?} else {
public final class ConfigSyncPacket implements CustomPacketPayload {
//?}

    private static final byte TYPE_BOOL = 0;
    private static final byte TYPE_INT = 1;
    private static final byte TYPE_DOUBLE = 2;
    private static final byte TYPE_STRING = 3;
    private static final byte TYPE_LIST = 4;

    /** 单包条目上限 — 双向包客户端信任边界 (恶意服务器超大计数致 OOM) */
    private static final int MAX_ENTRIES = 2048;
    /** 纯 JDK 日志 — decode 路径可被纯 JVM 测试触发 (错题 #174) */
    private static final System.Logger LOG = System.getLogger("LMA-Net-ConfigSync");

    private final List<MoreActionConfig.ConfigValueEntry> entries;

    public ConfigSyncPacket(List<MoreActionConfig.ConfigValueEntry> entries) {
        this.entries = entries;
//? if !1.20.1 {
        this.outboundType = TYPE;
//?}
    }

    public static void encode(ConfigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.entries, (b, e) -> {
            b.writeUtf(e.path());
            Object v = e.value();
            if (v instanceof Boolean bool) {
                b.writeByte(TYPE_BOOL);
                b.writeBoolean(bool);
            } else if (v instanceof Integer integer) {
                b.writeByte(TYPE_INT);
                b.writeInt(integer);
            } else if (v instanceof Double d) {
                b.writeByte(TYPE_DOUBLE);
                b.writeDouble(d);
            } else if (v instanceof String s) {
                b.writeByte(TYPE_STRING);
                b.writeUtf(s);
            } else if (v instanceof List<?> list) {
                b.writeByte(TYPE_LIST);
                b.writeCollection(list.stream().map(String::valueOf).toList(), FriendlyByteBuf::writeUtf);
            } else {
                throw new IllegalStateException("[LMA] 配置同步不支持的值类型: "
                        + (v == null ? "null" : v.getClass().getName()) + " @ " + e.path());
            }
        });
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) {
            LOG.log(System.Logger.Level.WARNING, "[LMA] 配置同步包条目数异常 ({0}), 丢弃", n);
            return new ConfigSyncPacket(List.of());
        }
        List<MoreActionConfig.ConfigValueEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String path = buf.readUtf();
            byte type = buf.readByte();
            switch (type) {
                case TYPE_BOOL -> list.add(new MoreActionConfig.ConfigValueEntry(path, buf.readBoolean()));
                case TYPE_INT -> list.add(new MoreActionConfig.ConfigValueEntry(path, buf.readInt()));
                case TYPE_DOUBLE -> list.add(new MoreActionConfig.ConfigValueEntry(path, buf.readDouble()));
                case TYPE_STRING -> list.add(new MoreActionConfig.ConfigValueEntry(path, buf.readUtf()));
                case TYPE_LIST -> list.add(new MoreActionConfig.ConfigValueEntry(path, buf.readList(FriendlyByteBuf::readUtf)));
                default -> LOG.log(System.Logger.Level.WARNING, "[LMA] 配置同步未知类型标记 {0} @ {1} — 跳过该条目", type, path);
            }
        }
        return new ConfigSyncPacket(list);
    }

//? if 1.20.1 {
    public static void handle(ConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer sender = context.getSender();
                // 仅 OP (权限 ≥2) 可改服务端配置 — 防任意客户端篡改
                if (sender == null || !sender.hasPermissions(2)) {
                    LittleMaidMoreAction.LOGGER.warn("[LMA] 配置同步被拒绝 (非 OP): {}",
                            sender == null ? "?" : sender.getGameProfile().getName());
                    return;
                }
                MoreActionConfig.applySnapshot(msg.entries);
                MoreActionConfig.saveAll();
                LittleMaidMoreAction.LOGGER.info("[LMA] {} 已应用配置同步 ({} 项)",
                        sender.getGameProfile().getName(), msg.entries.size());
                // 广播全量快照 — 所有客户端 (含发送者) spec 一致
                LmaNetwork.sender.sendToAll(
                        new ConfigSyncPacket(MoreActionConfig.snapshot()));
            });
        } else {
            context.enqueueWork(() -> MoreActionConfig.applySnapshot(msg.entries));
        }
        context.setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<ConfigSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "config_sync"));
    // NeoForge 21.1 payload 注册表: 同 TYPE 只能注册一次 — 双向需双 TYPE (C→S: config_sync, S→C: config_sync_s2c)
    public static final CustomPacketPayload.Type<ConfigSyncPacket> TYPE_S2C =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "config_sync_s2c"));

    private final CustomPacketPayload.Type<ConfigSyncPacket> outboundType;

    private ConfigSyncPacket(List<MoreActionConfig.ConfigValueEntry> entries, CustomPacketPayload.Type<ConfigSyncPacket> outboundType) {
        this.entries = entries;
        this.outboundType = outboundType;
    }

    /** 服务端广播专用 (S→C 方向, 使用独立 TYPE_S2C) */
    public static ConfigSyncPacket forBroadcast(List<MoreActionConfig.ConfigValueEntry> entries) {
        return new ConfigSyncPacket(entries, TYPE_S2C);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return outboundType; }

    public static final StreamCodec<ByteBuf, ConfigSyncPacket> STREAM_CODEC =
        PacketCodecs.wrap(ConfigSyncPacket::encode, ConfigSyncPacket::decode);

    public static void handlePayload(ConfigSyncPacket msg, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sender)) {
                    LittleMaidMoreAction.LOGGER.warn("[LMA] 配置同步被拒绝 (非玩家)");
                    return;
                }
                // 仅 OP (权限 ≥2) 可改服务端配置 — 防任意客户端篡改
                if (!sender.hasPermissions(2)) {
                    LittleMaidMoreAction.LOGGER.warn("[LMA] 配置同步被拒绝 (非 OP): {}",
                            sender.getGameProfile().getName());
                    return;
                }
                MoreActionConfig.applySnapshot(msg.entries);
                MoreActionConfig.saveAll();
                LittleMaidMoreAction.LOGGER.info("[LMA] {} 已应用配置同步 ({} 项)",
                        sender.getGameProfile().getName(), msg.entries.size());
                // 广播全量快照 — 所有客户端 (含发送者) spec 一致 (S→C 方向需 TYPE_S2C)
                LmaNetwork.sender.sendToAll(
                        ConfigSyncPacket.forBroadcast(MoreActionConfig.snapshot()));
            });
        } else {
            ctx.enqueueWork(() -> MoreActionConfig.applySnapshot(msg.entries));
        }
    }
//?}

    /** 客户端发送当前全量快照 (调用方需先 saveAll 本地落盘) */
    public static void send() {
        LmaNetwork.sender.sendToServer(new ConfigSyncPacket(MoreActionConfig.snapshot()));
    }
}

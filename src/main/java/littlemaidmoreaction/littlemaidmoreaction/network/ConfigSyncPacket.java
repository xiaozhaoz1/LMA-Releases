package littlemaidmoreaction.littlemaidmoreaction.network;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

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
public final class ConfigSyncPacket {

    private static final byte TYPE_BOOL = 0;
    private static final byte TYPE_INT = 1;
    private static final byte TYPE_DOUBLE = 2;
    private static final byte TYPE_STRING = 3;
    private static final byte TYPE_LIST = 4;

    private final List<MoreActionConfig.ConfigValueEntry> entries;

    public ConfigSyncPacket(List<MoreActionConfig.ConfigValueEntry> entries) {
        this.entries = entries;
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
        List<MoreActionConfig.ConfigValueEntry> list = buf.readList(b -> {
            String path = b.readUtf();
            Object v = switch (b.readByte()) {
                case TYPE_BOOL -> b.readBoolean();
                case TYPE_INT -> b.readInt();
                case TYPE_DOUBLE -> b.readDouble();
                case TYPE_STRING -> b.readUtf();
                case TYPE_LIST -> b.readList(FriendlyByteBuf::readUtf);
                default -> throw new IllegalStateException("[LMA] 配置同步未知类型标记 @ " + path);
            };
            return new MoreActionConfig.ConfigValueEntry(path, v);
        });
        return new ConfigSyncPacket(list);
    }

    public static void handle(ConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer sender = context.getSender();
                // v67.11: 仅 OP (权限 ≥2) 可改服务端配置 — 防任意客户端篡改
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
                LittleMaidMoreAction.NETWORK.send(PacketDistributor.ALL.noArg(),
                        new ConfigSyncPacket(MoreActionConfig.snapshot()));
            });
        } else {
            context.enqueueWork(() -> MoreActionConfig.applySnapshot(msg.entries));
        }
        context.setPacketHandled(true);
    }

    /** 客户端发送当前全量快照 (调用方需先 saveAll 本地落盘) */
    public static void send() {
        LittleMaidMoreAction.NETWORK.sendToServer(new ConfigSyncPacket(MoreActionConfig.snapshot()));
    }
}

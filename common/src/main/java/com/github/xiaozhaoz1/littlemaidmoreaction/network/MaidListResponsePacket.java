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
     * v79.26: 加 level/health — 列表行显示 Lv + ❤ (回归旧大面板行信息)。
     */
    public record MaidEntry(UUID uuid, String name, String dimension, double distSqr,
                            int level, float health, float maxHealth) {
    }

    /** 客户端缓存 — MaidListScreen tick 轮询 (远端女仆列表, 无实体引用) */
    private static volatile List<MaidEntry> lastEntries = List.of();

    public static List<MaidEntry> getLastEntries() {
        return lastEntries;
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
        }
    }

    public static MaidListResponsePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<MaidEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new MaidEntry(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readDouble(),
                    buf.readInt(), buf.readFloat(), buf.readFloat()));
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

    public static final StreamCodec<ByteBuf, MaidListResponsePacket> STREAM_CODEC = StreamCodec.of(
            (ByteBuf buf, MaidListResponsePacket msg) -> encode(msg, (FriendlyByteBuf) buf),
            (ByteBuf buf) -> decode((FriendlyByteBuf) buf));

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

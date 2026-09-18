package com.github.xiaozhaoz1.littlemaidmoreaction.network;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.FarmRegion;
import net.minecraft.network.FriendlyByteBuf;
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
import java.util.function.Supplier;

/**
 * 作物区域同步包 (S→C, v79.62) — 服务端回区域列表给区域管理 GUI.
 * 客户端落静态缓存 {@link #getLast(uuid)}, MaidFarmRegionScreen 读显示.
 *
 * <p>decode 守卫: 条目上限 (S2C 信任边界, 对齐三件套).
 */
//? if 1.20.1 {
public record FarmRegionSyncPacket(String maidUuid, List<FarmRegion> regions) {
//?} else {
public record FarmRegionSyncPacket(String maidUuid, List<FarmRegion> regions) implements CustomPacketPayload {
//?}

    /** 单包区域上限 — S2C 信任边界 */
    private static final int MAX_REGIONS = 64;

    /** 客户端缓存 (uuid → 区域列表) — MaidFarmRegionScreen 读 */
    private static volatile FarmRegionSyncPacket lastSync = null;

    public static FarmRegionSyncPacket getLastSync() {
        return lastSync;
    }

    public static void encode(FarmRegionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.maidUuid());
        buf.writeVarInt(msg.regions().size());
        for (FarmRegion r : msg.regions()) {
            buf.writeInt(r.minX());
            buf.writeInt(r.minY());
            buf.writeInt(r.minZ());
            buf.writeInt(r.maxX());
            buf.writeInt(r.maxY());
            buf.writeInt(r.maxZ());
            buf.writeUtf(r.cropId(), 64);
            buf.writeUtf(r.harvestMode(), 8);
            buf.writeUtf(r.name() != null ? r.name() : "", 64);
            // v79.62 per-region 箱 (null → NO_BOX; 不能用 -1 — 负坐标合法)
            buf.writeInt(r.seedX() != null ? r.seedX() : com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX);
            buf.writeInt(r.seedY() != null ? r.seedY() : com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX);
            buf.writeInt(r.seedZ() != null ? r.seedZ() : com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX);
            buf.writeInt(r.harvestX() != null ? r.harvestX() : com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX);
            buf.writeInt(r.harvestY() != null ? r.harvestY() : com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX);
            buf.writeInt(r.harvestZ() != null ? r.harvestZ() : com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX);
            // v79.64 维度 (空 = 旧数据/未指定)
            buf.writeUtf(r.dimension() != null ? r.dimension() : "", 128);
        }
    }

    public static FarmRegionSyncPacket decode(FriendlyByteBuf buf) {
        String uuid = buf.readUtf(64);
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_REGIONS) n = 0;
        List<FarmRegion> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int a = buf.readInt(), b = buf.readInt(), c = buf.readInt();
            int d = buf.readInt(), e = buf.readInt(), f = buf.readInt();
            String cropId = buf.readUtf(64);
            String mode = buf.readUtf(8);
            String name = buf.readUtf(64);
            int sx = buf.readInt(), sy = buf.readInt(), sz = buf.readInt();
            int hx = buf.readInt(), hy = buf.readInt(), hz = buf.readInt();
            String dimension = buf.readUtf(128);
            int nob = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.NO_BOX;
            list.add(new FarmRegion(a, b, c, d, e, f, cropId, mode,
                    sx == nob ? null : sx, sy == nob ? null : sy, sz == nob ? null : sz,
                    hx == nob ? null : hx, hy == nob ? null : hy, hz == nob ? null : hz,
                    name != null && !name.isBlank() ? name : "", dimension));
        }
        return new FarmRegionSyncPacket(uuid, list);
    }

//? if 1.20.1 {
    public static void handle(FarmRegionSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> lastSync = msg);
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<FarmRegionSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "farm_region_sync"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, FarmRegionSyncPacket> STREAM_CODEC =
        PacketCodecs.wrap(FarmRegionSyncPacket::encode, FarmRegionSyncPacket::decode);

    public static void handlePayload(FarmRegionSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> lastSync = msg);
    }
//?}

    /** 服务端发送给定玩家 (区域编辑处理回调用) */
    public static void sendToPlayer(ServerPlayer player, String maidUuid, List<FarmRegion> regions) {
        LmaNetwork.sender.sendToPlayer(player, new FarmRegionSyncPacket(maidUuid, regions));
    }
}

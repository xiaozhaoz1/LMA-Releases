package com.github.xiaozhaoz1.littlemaidmoreaction.network;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage;
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

import java.util.List;
import java.util.function.Supplier;

/**
 * 作物区域编辑包 (C→S, v79.62 区域管理 GUI) — MaidFarmRegionScreen 操作 →
 * 服务端改 {@link FarmRegionStorage} + save + 回 {@link FarmRegionSyncPacket} 刷新.
 *
 * <p>action: 0=QUERY (仅请求列表) / 1=ADD / 2=REMOVE(index) / 3=CLEAR.
 * region 帧 (QUERY/CLEAR 时空): 4 字节 mode 前缀区分.
 */
//? if 1.20.1 {
public record FarmRegionEditPacket(int action, String maidUuid, int index,
        int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String cropId, String harvestMode,
        String name, int seedX, int seedY, int seedZ, int harvestX, int harvestY, int harvestZ) {
//?} else {
public record FarmRegionEditPacket(int action, String maidUuid, int index,
        int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String cropId, String harvestMode,
        String name, int seedX, int seedY, int seedZ, int harvestX, int harvestY, int harvestZ)
        implements CustomPacketPayload {
//?}

    public static final int ACTION_QUERY = 0;
    public static final int ACTION_ADD = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_CLEAR = 3;
    /** v79.62: 逐行编辑 — 改第 index 个区域的 cropId/收获方式 (保留端点与箱) */
    public static final int ACTION_UPDATE = 4;

    public static void encode(FarmRegionEditPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action());
        buf.writeUtf(msg.maidUuid());
        buf.writeInt(msg.index());
        boolean hasRegion = msg.action() == ACTION_ADD || msg.action() == ACTION_UPDATE;
        buf.writeBoolean(hasRegion);
        if (hasRegion) {
            buf.writeUtf(msg.cropId(), 64);
            buf.writeUtf(msg.harvestMode(), 8);
            buf.writeUtf(msg.name(), 64);
            buf.writeInt(msg.minX());
            buf.writeInt(msg.minY());
            buf.writeInt(msg.minZ());
            buf.writeInt(msg.maxX());
            buf.writeInt(msg.maxY());
            buf.writeInt(msg.maxZ());
            buf.writeInt(msg.seedX());
            buf.writeInt(msg.seedY());
            buf.writeInt(msg.seedZ());
            buf.writeInt(msg.harvestX());
            buf.writeInt(msg.harvestY());
            buf.writeInt(msg.harvestZ());
        } else {
            // 占位 (无 region 帧) — decode 侧按 hasRegion 决定
            buf.writeUtf("");
            buf.writeUtf("");
            buf.writeUtf("");
            for (int i = 0; i < 12; i++) buf.writeInt(0);
        }
    }

    public static FarmRegionEditPacket decode(FriendlyByteBuf buf) {
        int action = buf.readByte();
        String maidUuid = buf.readUtf(64);
        int index = buf.readInt();
        boolean hasRegion = buf.readBoolean();
        String cropId = buf.readUtf(64);
        String harvestMode = buf.readUtf(8);
        String name = buf.readUtf(64);
        int x1 = buf.readInt(), y1 = buf.readInt(), z1 = buf.readInt();
        int x2 = buf.readInt(), y2 = buf.readInt(), z2 = buf.readInt();
        int sx = buf.readInt(), sy = buf.readInt(), sz = buf.readInt();
        int hx = buf.readInt(), hy = buf.readInt(), hz = buf.readInt();
        return new FarmRegionEditPacket(action, maidUuid, index, x1, y1, z1, x2, y2, z2,
                hasRegion ? cropId : "", hasRegion ? harvestMode : "left",
                hasRegion ? name : "", sx, sy, sz, hx, hy, hz);
    }

//? if 1.20.1 {
    public static void handle(FarmRegionEditPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) handleFarm(player, msg);
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<FarmRegionEditPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "farm_region_edit"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, FarmRegionEditPacket> STREAM_CODEC =
        PacketCodecs.wrap(FarmRegionEditPacket::encode, FarmRegionEditPacket::decode);

    public static void handlePayload(FarmRegionEditPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) handleFarm(player, msg);
        });
    }
//?}

    /** 服务端统一处理: 改该女仆 NBT 里的区域 + 回 sync 刷新 (v79.64: 按 uuid 解析女仆实体) */
    private static void handleFarm(ServerPlayer player, FarmRegionEditPacket msg) {
        String uuid = msg.maidUuid();
        if (uuid == null || uuid.isBlank() || uuid.length() > 64) return;
        // 按 uuid 找女仆 (包只带 uuid; 找不到 = 未加载/已收魂符)
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel ownerLevel)) return;
        var m = FarmRegionStorage.findMaid(ownerLevel, uuid);
        if (m == null) return;
        String dim = player.level().dimension().location().toString();
        switch (msg.action()) {
            case ACTION_ADD -> {
                FarmRegionStorage.FarmRegion r = FarmRegionStorage.of(msg.minX(), msg.minY(), msg.minZ(),
                        msg.maxX(), msg.maxY(), msg.maxZ(), msg.cropId(), msg.harvestMode());
                // 维度 = 玩家(操作者)当前世界 (区域是玩家在眼前框选的)
                r = new FarmRegionStorage.FarmRegion(r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(),
                        r.cropId(), r.harvestMode(), r.seedX(), r.seedY(), r.seedZ(),
                        r.harvestX(), r.harvestY(), r.harvestZ(), msg.name(), dim);
                FarmRegionStorage.addRegion(m, r);
            }
            case ACTION_REMOVE -> FarmRegionStorage.removeRegion(m, msg.index());
            case ACTION_CLEAR -> FarmRegionStorage.putRegions(m, List.of());
            // 边界不可变 (区域几何在绑定时确定; 改边界 = 重新框选绑定), 故不传 min/max
            case ACTION_UPDATE -> FarmRegionStorage.updateRegionFull(m, msg.index(),
                    msg.name(), msg.cropId(), msg.harvestMode(),
                    msg.seedX(), msg.seedY(), msg.seedZ(),
                    msg.harvestX(), msg.harvestY(), msg.harvestZ());
            case ACTION_QUERY -> { /* 仅查询 */ }
            default -> { return; }
        }
        // 数据已写进女仆 NBT (随实体保存) ⇒ 无文件落盘; 回客户端刷新
        FarmRegionSyncPacket.sendToPlayer(player, uuid, FarmRegionStorage.getFor(m));
    }

    /** 客户端发送区域编辑请求 (v79.62.1 全字段) */
    public static void sendToServer(int action, String maidUuid, int index,
            int x1, int y1, int z1, int x2, int y2, int z2, String cropId, String harvestMode,
            String name, int seedX, int seedY, int seedZ, int harvestX, int harvestY, int harvestZ) {
        LmaNetwork.sender.sendToServer(new FarmRegionEditPacket(action, maidUuid, index,
                x1, y1, z1, x2, y2, z2, cropId, harvestMode,
                name, seedX, seedY, seedZ, harvestX, harvestY, harvestZ));
    }
}

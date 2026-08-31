package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.CropRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.network.NetworkEvent;
//?}
//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

import java.util.function.Supplier;

/**
 * 作物区域绑定包 (C→S, v79.62) — 玩家在世界内: 木棒潜行框选区域(两点) + 木棒右键容器标记
 * 种子箱/目标箱 → 右键女仆 → 客户端把当前选区 AABB 发来, 服务端结合主手木棍 NBT 里的
 * farm_seed/farm_harvest 建一个区域 (cropId 留空=只收, GUI 里补), 加列表 + save + 回 sync.
 *
 * <p>区域与容器绑定分离: 容器绑定在游戏内用标记物品完成 (FarmContainerBindPacket 写木棍 NBT),
 * 本包只在「右键女仆交付」时携带选区坐标.
 */
//? if 1.20.1 {
public record FarmRegionBindPacket(String maidUuid, int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ) {
//?} else {
public record FarmRegionBindPacket(String maidUuid, int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ) implements CustomPacketPayload {
//?}

    public static void encode(FarmRegionBindPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.maidUuid(), 64);
        buf.writeInt(msg.minX());
        buf.writeInt(msg.minY());
        buf.writeInt(msg.minZ());
        buf.writeInt(msg.maxX());
        buf.writeInt(msg.maxY());
        buf.writeInt(msg.maxZ());
    }

    public static FarmRegionBindPacket decode(FriendlyByteBuf buf) {
        return new FarmRegionBindPacket(buf.readUtf(64),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt());
    }

//? if 1.20.1 {
    public static void handle(FarmRegionBindPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) handleBind(player, msg);
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<FarmRegionBindPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "farm_region_bind"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, FarmRegionBindPacket> STREAM_CODEC =
        PacketCodecs.wrap(FarmRegionBindPacket::encode, FarmRegionBindPacket::decode);

    public static void handlePayload(FarmRegionBindPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) handleBind(player, msg);
        });
    }
//?}

    /** 服务端: 木棍 farm_seed/farm_harvest + 选区 → 建区域 + save + 回 sync + 清木棍标记 */
    private static void handleBind(ServerPlayer player, FarmRegionBindPacket msg) {
        String uuid = msg.maidUuid();
        if (uuid == null || uuid.isBlank() || uuid.length() > 64) return;
        ItemStack held = player.getMainHandItem();
        CompoundTag tag;
//? if 1.20.1 {
        tag = held.getTag() != null ? held.getTag() : new CompoundTag();
//?} else {
        var cd = held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        tag = cd.copyTag();
//?}
        int nob = com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.NO_BOX;
        int sx = nob, sy = nob, sz = nob, hx = nob, hy = nob, hz = nob;
        if (tag.contains("farm_seed")) {
            BlockPos p = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(tag, "farm_seed");
            if (p != null) { sx = p.getX(); sy = p.getY(); sz = p.getZ(); }
        }
        if (tag.contains("farm_harvest")) {
            BlockPos p = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(tag, "farm_harvest");
            if (p != null) { hx = p.getX(); hy = p.getY(); hz = p.getZ(); }
        }
        // v79.62.1 默认作物识别: cropId 空时扫描选区 min 角列 (minX,minZ) 从 minY→maxY 找第一个内置作物 → 自动设种子 id
        // (Y±1 扩展后 min 角可能是耕地下方, 需扫列找作物方块)
        String cropId = "";
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
            for (int y = msg.minY(); y <= msg.maxY(); y++) {
                scan.set(msg.minX(), y, msg.minZ());
                String seedId = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.CropRegistry
                        .seedIdFor(sl.getBlockState(scan).getBlock());
                if (seedId != null) { cropId = seedId; break; }
            }
        }
        // v79.62.1: 右键收获作物 (甜浆果/发光浆果) 自动设 harvestMode=right
        String harvestMode = "left";
        if (!cropId.isEmpty()) {
            net.minecraft.world.item.Item seedItem = resolveSeedItemStatic(cropId);
            if (seedItem != null) {
                net.minecraft.world.level.block.Block cropBlock = null;
                if (seedItem instanceof net.minecraft.world.item.ItemNameBlockItem ib) cropBlock = ib.getBlock();
                if (cropBlock != null && CropRegistry.isRightClickHarvestable(cropBlock.defaultBlockState())) {
                    harvestMode = "right";
                }
            }
        }
        FarmRegionStorage.FarmRegion r = FarmRegionStorage.of(
                msg.minX(), msg.minY(), msg.minZ(), msg.maxX(), msg.maxY(), msg.maxZ(),
                cropId, harvestMode, sx, sy, sz, hx, hy, hz);
        FarmRegionStorage.addRegion(uuid, r);
        FarmRegionStorage.save(LittleMaidMoreAction.CONFIG_DIR);
        FarmRegionSyncPacket.sendToPlayer(player, uuid, FarmRegionStorage.getFor(uuid));
        // 不清木棍标记 — 允许玩家标记一次箱子后连续绑定多个区域 (每个区域用当前木棍上的箱子)
//? if !1.20.1 {
        held.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
//?}
        StringBuilder sb = new StringBuilder("§a已绑定种植区域: " + (msg.maxX() - msg.minX() + 1) + "×"
                + (msg.maxY() - msg.minY() + 1) + "×" + (msg.maxZ() - msg.minZ() + 1));
        if (sx >= 0) sb.append(" §7种子源: " + sx + "," + sy + "," + sz);
        if (hx >= 0) sb.append(" §7目标箱: " + hx + "," + hy + "," + hz);
        sb.append(" §7(GUI 里补作物 id / 左·右手)");
        player.sendSystemMessage(Component.literal(sb.toString()));
    }

    /** cropId → Item (双平台注册表) */
    private static net.minecraft.world.item.Item resolveSeedItemStatic(String cropId) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(cropId);
        if (rl == null) return null;
//? if 1.20.1 {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
//?} else {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
//?}
    }

    /** 客户端: 右键女仆时发送当前选区 (maidUuid + 归一化 AABB 端点, 半开) */
    public static void sendToServer(String maidUuid, net.minecraft.world.phys.AABB aabb) {
        LmaNetwork.sender.sendToServer(new FarmRegionBindPacket(maidUuid,
                (int) aabb.minX, (int) aabb.minY, (int) aabb.minZ,
                (int) aabb.maxX - 1, (int) aabb.maxY - 1, (int) aabb.maxZ - 1));
    }
}

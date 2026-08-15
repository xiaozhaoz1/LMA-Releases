package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if 1.20.1 {
import net.minecraftforge.network.NetworkEvent;
//?}
//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * v79.25.2: 女仆列表查询包 (C→S) — 打开 MaidListScreen 时请求服务端全维度扫描玩家拥有的全部女仆
 * (用户裁定: "显示自己有的女仆不是搜索周围女仆" — 旧版 64 格 AABB 扫描只能看到附近女仆)。
 * 服务端遍历所有维度 getAllEntities 过滤 ownerUUID → 回 {@link MaidListResponsePacket} (按距离排序)。
 * <p>NET-H1 (v79.50): 全维度扫描 O(实体数) 昂贵 — 服务端入口挂 per-player 20t 节流
 * ({@link C2SThrottle}), 开屏 + 窗口 resize 重发等连发查询超频直接丢弃 (响应放大一并消除)。
 */
//? if 1.20.1 {
public final class MaidListQueryPacket {
//?} else {
public final class MaidListQueryPacket implements CustomPacketPayload {
//?}

    /** NET-H1 防刷: 同 player 每 20t (1 秒) 最多处理 1 次查询 — 全维度扫描不可高频 */
    private static final long THROTTLE_TICKS = 20L;
    private static final String THROTTLE_KEY = "maid_list_query";

    private MaidListQueryPacket() {
    }

    public static void encode(MaidListQueryPacket msg, FriendlyByteBuf buf) {
        // 无字段 — 请求方 player 从网络 context 获取
    }

    public static MaidListQueryPacket decode(FriendlyByteBuf buf) {
        return new MaidListQueryPacket();
    }

    //? if 1.20.1 {
    public static void handle(MaidListQueryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            // NET-H1: per-player 20t 节流 — 连发 (开屏 + resize 重发) 直接丢弃
            if (!C2SThrottle.allow(player, THROTTLE_KEY, THROTTLE_TICKS)) {
                return;
            }
            sendListTo(player);
        });
        ctx.get().setPacketHandled(true);
    }
    //?}
    //? if !1.20.1 {
    public static final CustomPacketPayload.Type<MaidListQueryPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_list_query"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, MaidListQueryPacket> STREAM_CODEC =
            PacketCodecs.wrap(MaidListQueryPacket::encode, MaidListQueryPacket::decode);

    public static void handlePayload(MaidListQueryPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            // NET-H1: per-player 20t 节流 — 连发 (开屏 + resize 重发) 直接丢弃
            if (!C2SThrottle.allow(player, THROTTLE_KEY, THROTTLE_TICKS)) {
                return;
            }
            sendListTo(player);
        });
    }
    //?}

    /** 服务端全维度扫描: 存活 + ownerUUID == 请求玩家 → 按距离排序回传 */
    private static void sendListTo(ServerPlayer player) {
        List<MaidListResponsePacket.MaidEntry> entries = new ArrayList<>();
        for (ServerLevel lvl : player.server.getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) {
                if (e instanceof EntityMaid m && m.isAlive() && m.getOwnerUUID() != null
                        && m.getOwnerUUID().equals(player.getUUID())) {
                    // level = exp/120 (TLM GUI 同款换算契约) + 生命值 — 列表行 Lv/❤ 数据
                    // envsense = per-maid 环境感知开关 (默认开: 无键视为开, 显式 false = 关)
                    var pd = m.getPersistentData();
                    boolean envsense = !pd.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.ENVSENSE_ENABLED)
                            || pd.getBoolean(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.ENVSENSE_ENABLED);
                    entries.add(new MaidListResponsePacket.MaidEntry(m.getUUID(),
                            m.getName().getString(),
                            lvl.dimension().location().toString(),
                            m.distanceToSqr(player),
                            m.getExperience() / 120,
                            m.getHealth(),
                            m.getMaxHealth(),
                            envsense));
                }
            }
        }
        entries.sort(Comparator.comparingDouble(MaidListResponsePacket.MaidEntry::distSqr));
        MaidListResponsePacket.sendToPlayer(player, entries);
    }

    /** 客户端发送请求 (MaidListScreen init 调用) */
    public static void sendToServer() {
        LmaNetwork.sender.sendToServer(new MaidListQueryPacket());
    }
}

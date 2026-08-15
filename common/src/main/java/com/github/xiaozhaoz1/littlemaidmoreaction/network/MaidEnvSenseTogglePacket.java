package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

import java.util.UUID;
import java.util.function.Supplier;

/**
 * v79.47: per-maid 环境感知开关包 (C→S) — MaidListScreen 按钮 → 服务端翻转 PD
 * {@code lma_envsense_enabled} (默认开语义: 无键视为开, 显式 false = 关; 翻转 = 切换)。
 * <p>NET-H1 (v79.50): 翻转前全维度扫 owned 女仆 O(实体数) — 服务端入口挂 per-player
 * 20t 节流 ({@link C2SThrottle}), 连发切换直接丢弃。
 */
//? if 1.20.1 {
public record MaidEnvSenseTogglePacket(UUID maidId) {
//?} else {
public record MaidEnvSenseTogglePacket(UUID maidId) implements CustomPacketPayload {
//?}

    /** NET-H1 防刷: 同 player 每 20t (1 秒) 最多处理 1 次切换 — 全维度扫描不可高频 */
    private static final long THROTTLE_TICKS = 20L;
    private static final String THROTTLE_KEY = "envsense_toggle";

    public static void encode(MaidEnvSenseTogglePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidId());
    }

    public static MaidEnvSenseTogglePacket decode(FriendlyByteBuf buf) {
        return new MaidEnvSenseTogglePacket(buf.readUUID());
    }

    //? if 1.20.1 {
    public static void handle(MaidEnvSenseTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // NET-H1: per-player 20t 节流 — 连发切换直接丢弃
            if (!C2SThrottle.allow(player, THROTTLE_KEY, THROTTLE_TICKS)) return;
            toggle(player, msg.maidId());
        });
        ctx.get().setPacketHandled(true);
    }
    //?}
    //? if !1.20.1 {
    public static final CustomPacketPayload.Type<MaidEnvSenseTogglePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_envsense_toggle"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, MaidEnvSenseTogglePacket> STREAM_CODEC =
            PacketCodecs.wrap(MaidEnvSenseTogglePacket::encode, MaidEnvSenseTogglePacket::decode);

    public static void handlePayload(MaidEnvSenseTogglePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            // NET-H1: per-player 20t 节流 — 连发切换直接丢弃
            if (!C2SThrottle.allow(player, THROTTLE_KEY, THROTTLE_TICKS)) return;
            toggle(player, msg.maidId());
        });
    }
    //?}

    /** 服务端: 全维度找 owned 女仆 → 翻转 PD 开关 (默认开语义: 无键/true → false; false → true) */
    private static void toggle(ServerPlayer player, UUID maidId) {
        for (ServerLevel lvl : player.server.getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) {
                if (!(e instanceof EntityMaid m)) continue;
                if (!m.isAlive() || !m.getUUID().equals(maidId)) continue;
                if (m.getOwnerUUID() == null || !m.getOwnerUUID().equals(player.getUUID())) continue;
                var pd = m.getPersistentData();
                boolean cur = pd.contains(TaskKeys.ENVSENSE_ENABLED)
                        && pd.getBoolean(TaskKeys.ENVSENSE_ENABLED);
                pd.putBoolean(TaskKeys.ENVSENSE_ENABLED, !cur);
                return;
            }
        }
    }

    /** 客户端发送 (MaidListScreen 按钮) */
    public static void sendToServer(UUID maidId) {
        LmaNetwork.sender.sendToServer(new MaidEnvSenseTogglePacket(maidId));
    }
}

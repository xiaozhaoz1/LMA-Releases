package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
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
 * v79.62.3: 五子棋「直接判你赢」开关包 (C→S) — MaidOtherSettingsScreen。
 * action: {@link #ACTION_QUERY}=0 查当前状态 (服务端回 {@link MaidGomokuStatePacket});
 * {@link #ACTION_TOGGLE}=1 翻转女仆 PD {@code lma_gomoku_instant_win}。
 * 判赢 hook (GomokuInstantWinHandler) 读同一 PD 键。
 */
//? if 1.20.1 {
public record MaidGomokuTogglePacket(int action, UUID maidId) {
//?} else {
public record MaidGomokuTogglePacket(int action, UUID maidId) implements CustomPacketPayload {
//?}

    public static final int ACTION_QUERY = 0;
    public static final int ACTION_TOGGLE = 1;

    /** 防刷: 同 player 每 10t 最多处理 1 次 (PD 翻转廉价, 节流 10t) */
    private static final long THROTTLE_TICKS = 10L;
    private static final String THROTTLE_KEY = "gomoku_instant_win";

    public static void encode(MaidGomokuTogglePacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action());
        buf.writeUUID(msg.maidId());
    }

    public static MaidGomokuTogglePacket decode(FriendlyByteBuf buf) {
        return new MaidGomokuTogglePacket(buf.readByte(), buf.readUUID());
    }

    //? if 1.20.1 {
    public static void handle(MaidGomokuTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!C2SThrottle.allow(player, THROTTLE_KEY, THROTTLE_TICKS)) return;
            process(player, msg);
        });
        ctx.get().setPacketHandled(true);
    }
    //?}
    //? if !1.20.1 {
    public static final CustomPacketPayload.Type<MaidGomokuTogglePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_gomoku_toggle"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, MaidGomokuTogglePacket> STREAM_CODEC =
            PacketCodecs.wrap(MaidGomokuTogglePacket::encode, MaidGomokuTogglePacket::decode);

    public static void handlePayload(MaidGomokuTogglePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;
            if (!C2SThrottle.allow(player, THROTTLE_KEY, THROTTLE_TICKS)) return;
            process(player, msg);
        });
    }
    //?}

    /** PD 键 (女仆 PersistentData) */
    public static final String PD_KEY = "lma_gomoku_instant_win";

    /** 服务端处理器: 查/翻转 → 回 S2C 状态包 */
    private static void process(ServerPlayer player, MaidGomokuTogglePacket msg) {
        EntityMaid maid = findOwned(player, msg.maidId());
        if (maid == null) return;
        if (msg.action() == ACTION_TOGGLE) {
            boolean cur = isEnabled(maid);
            maid.getPersistentData().putBoolean(PD_KEY, !cur);
        }
        boolean state = isEnabled(maid);
        LmaNetwork.sender.sendToPlayer(player, new MaidGomokuStatePacket(state));
    }

    /** 全维度找 owned 女仆 */
    private static EntityMaid findOwned(ServerPlayer player, UUID maidId) {
        for (ServerLevel lvl : player.server.getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) {
                if (!(e instanceof EntityMaid m)) continue;
                if (!m.isAlive() || !m.getUUID().equals(maidId)) continue;
                if (m.getOwnerUUID() == null || !m.getOwnerUUID().equals(player.getUUID())) continue;
                return m;
            }
        }
        return null;
    }

    /** 服务端读当前状态 (判赢 hook 用) */
    public static boolean isEnabled(EntityMaid maid) {
        if (maid == null) return false;
        return maid.getPersistentData().contains(PD_KEY) && maid.getPersistentData().getBoolean(PD_KEY);
    }

    /** 客户端发送 */
    public static void sendToServer(int action, UUID maidId) {
        LmaNetwork.sender.sendToServer(new MaidGomokuTogglePacket(action, maidId));
    }
}

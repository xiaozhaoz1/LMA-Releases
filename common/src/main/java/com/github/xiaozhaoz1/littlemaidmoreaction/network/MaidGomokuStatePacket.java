package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
 * v79.62.3: 五子棋「直接判你赢」状态包 (S→C) — 服务端回执当前开关值,
 * MaidOtherSettingsScreen 收后刷新按钮文字 (错题集: 点了后台真变 + 界面回显)。
 */
//? if 1.20.1 {
public record MaidGomokuStatePacket(boolean enabled) {
//?} else {
public record MaidGomokuStatePacket(boolean enabled) implements CustomPacketPayload {
//?}

    /** 客户端静态缓存 — 屏 tick 轮询 (MaidListResponsePacket 同模式) */
    private static volatile boolean lastState = false;

    public static void encode(MaidGomokuStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled());
    }

    public static MaidGomokuStatePacket decode(FriendlyByteBuf buf) {
        return new MaidGomokuStatePacket(buf.readBoolean());
    }

    //? if 1.20.1 {
    public static void handle(MaidGomokuStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> lastState = msg.enabled());
        ctx.get().setPacketHandled(true);
    }
    //?}
    //? if !1.20.1 {
    public static final CustomPacketPayload.Type<MaidGomokuStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_gomoku_state"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, MaidGomokuStatePacket> STREAM_CODEC =
            PacketCodecs.wrap(MaidGomokuStatePacket::encode, MaidGomokuStatePacket::decode);

    public static void handlePayload(MaidGomokuStatePacket msg, IPayloadContext ctx) {
        lastState = msg.enabled();
    }
    //?}

    public static boolean getLastState() {
        return lastState;
    }

    /** 客户端发送 (给服务端确认收到 — 一般不用; 保留对称) */
    public static void sendToServer(boolean enabled) {
        LmaNetwork.sender.sendToServer(new MaidGomokuStatePacket(enabled));
    }
}

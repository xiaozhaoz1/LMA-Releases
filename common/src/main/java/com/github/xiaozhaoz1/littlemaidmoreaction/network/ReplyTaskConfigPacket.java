package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.LmaTaskConfigContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
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
 * 通用任务配置响应包 (ID 6, S→C)。
 *
 * <p>服务端→客户端: 携带配置 NBT 快照。
 * 客户端: 当前屏幕如果是 LmaTaskConfigContainer → updateConfig(cfg)。
 *
 * <p>v79.6: specs 参数契约字段随 ParamSpec 删除 (v77.4 AutoConfig 退役后恒 null)。
 */
//? if 1.20.1 {
public record ReplyTaskConfigPacket(int maidId, String taskType, CompoundTag config) {
//?} else {
public record ReplyTaskConfigPacket(int maidId, String taskType, CompoundTag config) implements CustomPacketPayload {
//?}

    public static void encode(ReplyTaskConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId);
        buf.writeUtf(msg.taskType);
        buf.writeNbt(msg.config);
    }

    public static ReplyTaskConfigPacket decode(FriendlyByteBuf buf) {
        int maidId = buf.readInt();
        String taskType = buf.readUtf();
        CompoundTag config = buf.readNbt();
        return new ReplyTaskConfigPacket(maidId, taskType, config);
    }

//? if 1.20.1 {
    public static void handle(ReplyTaskConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (player.containerMenu instanceof LmaTaskConfigContainer menu
                && menu.getMaid() != null
                && menu.getMaid().getId() == msg.maidId) {
                menu.updateConfig(msg.config);
            }
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<ReplyTaskConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("littlemaidmoreaction", "reply_task_config"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, ReplyTaskConfigPacket> STREAM_CODEC =
        PacketCodecs.wrap(ReplyTaskConfigPacket::encode, ReplyTaskConfigPacket::decode);

    public static void handlePayload(ReplyTaskConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (player.containerMenu instanceof LmaTaskConfigContainer menu
                && menu.getMaid() != null
                && menu.getMaid().getId() == msg.maidId) {
                menu.updateConfig(msg.config);
            }
        });
    }
//?}
}

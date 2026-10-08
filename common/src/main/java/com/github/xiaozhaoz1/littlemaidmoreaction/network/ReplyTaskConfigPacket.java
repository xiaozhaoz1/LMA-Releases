package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.client.TaskConfigReplyClientHandler;
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
        // 输入防御 (2026-09-21 守则 §3): readNbt 可返回 null (空 NBT / 跨版本载荷形状不符) ⇒ 直传 handler 会
        //   NPE 或静默错 ✗ (同源错题 #194 的 S2C 侧变体) ⇒ 兜底成空 tag + 统一前缀 warn ⇒ 一条 grep 可查 ✓
        if (config == null) {
            LittleMaidMoreAction.LOGGER.warn("[NET-GUARD] reply_task_config 的 NBT 为空 (空包/跨版本载荷) ⇒ 用空配置兜底: maid={} task={}",
                    maidId, taskType);
            config = new CompoundTag();
        }
        return new ReplyTaskConfigPacket(maidId, taskType, config);
    }

//? if 1.20.1 {
    public static void handle(ReplyTaskConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> TaskConfigReplyClientHandler.apply(msg.maidId, msg.config));
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<ReplyTaskConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "reply_task_config"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, ReplyTaskConfigPacket> STREAM_CODEC =
        PacketCodecs.wrap(ReplyTaskConfigPacket::encode, ReplyTaskConfigPacket::decode);

    public static void handlePayload(ReplyTaskConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> TaskConfigReplyClientHandler.apply(msg.maidId, msg.config));
    }
//?}
}

package com.github.xiaozhaoz1.littlemaidmoreaction.network;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if 1.20.1 {
import net.minecraftforge.network.NetworkEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.network.PacketDistributor;
//?} else {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

import java.util.function.Supplier;

/**
 * 通用任务配置请求包 (ID 5, C→S)。
 *
 * <p>客户端请求服务端发送指定任务的配置数据。
 * 服务端: 读 maid → TaskRegistry.get(taskType) → Pipeline.getConfigNbt(maid) → 回复 ReplyTaskConfigPacket。
 */
//? if 1.20.1 {
public final class RequestTaskConfigPacket {
//?} else {
public final class RequestTaskConfigPacket implements CustomPacketPayload {
//?}

    private final int maidId;
    private final String taskType;

    public RequestTaskConfigPacket(int maidId, String taskType) {
        this.maidId = maidId;
        this.taskType = taskType;
    }

    public static void encode(RequestTaskConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId);
        buf.writeUtf(msg.taskType);
    }

    public static RequestTaskConfigPacket decode(FriendlyByteBuf buf) {
        return new RequestTaskConfigPacket(buf.readInt(), buf.readUtf());
    }

//? if 1.20.1 {
    public static void handle(RequestTaskConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity e = player.serverLevel().getEntity(msg.maidId);
            if (!(e instanceof EntityMaid maid)) return;
            // 鉴权 (M-2, 对齐 OpenMaidListPacket 四连): UUID 级比较 (错题 #130 isOwnedBy 引用比较陷阱) + 距离 ≤8 格
            if (maid.getOwnerUUID() == null || !maid.getOwnerUUID().equals(player.getUUID())) return;
            if (maid.distanceToSqr(player) > 64.0D) return;

            TaskRegistry.TaskHandler handler = TaskRegistry.get(msg.taskType);
            if (handler == null) return;

            TaskPipeline pipeline = handler.pipeline();
            // 配置维度拆分 — 未实现 TaskConfigurable 的管线无配置面
            CompoundTag config = pipeline instanceof com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable t
                    ? t.getConfigNbt(maid) : new CompoundTag();

            LmaNetwork.sender.sendToPlayer(player, new ReplyTaskConfigPacket(msg.maidId, msg.taskType, config));
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<RequestTaskConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "request_task_config"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, RequestTaskConfigPacket> STREAM_CODEC =
        PacketCodecs.wrap(RequestTaskConfigPacket::encode, RequestTaskConfigPacket::decode);

    public static void handlePayload(RequestTaskConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            Entity e = player.serverLevel().getEntity(msg.maidId);
            if (!(e instanceof EntityMaid maid)) return;
            // 鉴权 (M-2, 对齐 OpenMaidListPacket 四连): UUID 级比较 (错题 #130 isOwnedBy 引用比较陷阱) + 距离 ≤8 格
            if (maid.getOwnerUUID() == null || !maid.getOwnerUUID().equals(player.getUUID())) return;
            if (maid.distanceToSqr(player) > 64.0D) return;

            TaskRegistry.TaskHandler handler = TaskRegistry.get(msg.taskType);
            if (handler == null) return;

            TaskPipeline pipeline = handler.pipeline();
            // 配置维度拆分 — 未实现 TaskConfigurable 的管线无配置面
            CompoundTag config = pipeline instanceof com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable t
                    ? t.getConfigNbt(maid) : new CompoundTag();

            LmaNetwork.sender.sendToPlayer(player, new ReplyTaskConfigPacket(msg.maidId, msg.taskType, config));
        });
    }
//?}


    /** 客户端发送配置请求 */
    public static void send(int maidId, String taskType) {
        LmaNetwork.sender.sendToServer(new RequestTaskConfigPacket(maidId, taskType));
    }
}

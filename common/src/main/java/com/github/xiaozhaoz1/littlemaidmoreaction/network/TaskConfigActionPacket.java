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
//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

import java.util.function.Supplier;

/**
 * 通用任务配置动作包 (C→S, ID 3) — 配置 GUI 修改任务配置的通用入口。
 *
 * <p>模块化设计: 任何任务的配置 GUI 通过本包发送动作,
 * 服务端委托 {@link TaskPipeline#handleConfigAction(EntityMaid, byte, CompoundTag)} 处理。
 * 引擎通用动作常量见 {@link TaskPipeline} (ACTION_TOGGLE / ACTION_SET_INT / ACTION_REMOVE);
 * 任务自定义动作由各 Pipeline 定义并覆写 handleConfigAction。
 *
 * <p>替换 v67 早期的 BlockInteractConfigPacket (任务特定包)。
 */
//? if 1.20.1 {
public final class TaskConfigActionPacket {
//?} else {
public final class TaskConfigActionPacket implements CustomPacketPayload {
//?}

    private final int maidId;
    private final String taskType;
    private final byte action;
    private final CompoundTag payload;

    public TaskConfigActionPacket(int maidId, String taskType, byte action, CompoundTag payload) {
        this.maidId = maidId;
        this.taskType = taskType;
        this.action = action;
        this.payload = payload;
    }

    public static void encode(TaskConfigActionPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId);
        buf.writeUtf(msg.taskType);
        buf.writeByte(msg.action);
        buf.writeNbt(msg.payload);
    }

    public static TaskConfigActionPacket decode(FriendlyByteBuf buf) {
        // 读序与 encode 严格对称 (字节级 round-trip 契约); null NBT 判空在 handle/handlePayload (空 payload 守卫)
        return new TaskConfigActionPacket(buf.readInt(), buf.readUtf(), buf.readByte(), buf.readNbt());
    }

//? if 1.20.1 {
    public static void handle(TaskConfigActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
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
            // 配置维度拆分 — 未实现 TaskConfigurable 的管线忽略配置动作
            if (handler.pipeline() instanceof com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable tc) {
                if (msg.payload == null) return;   // 双保险 (decode 已兜底, 防其余构造路径)
                tc.handleConfigAction(maid, msg.action, msg.payload);
            }
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<TaskConfigActionPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "task_config_action"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, TaskConfigActionPacket> STREAM_CODEC =
        PacketCodecs.wrap(TaskConfigActionPacket::encode, TaskConfigActionPacket::decode);

    public static void handlePayload(TaskConfigActionPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            Entity e = player.serverLevel().getEntity(msg.maidId);
            if (!(e instanceof EntityMaid maid)) return;
            // 鉴权 (M-2, 对齐 OpenMaidListPacket 四连): UUID 级比较 (错题 #130 isOwnedBy 引用比较陷阱) + 距离 ≤8 格
            if (maid.getOwnerUUID() == null || !maid.getOwnerUUID().equals(player.getUUID())) return;
            if (maid.distanceToSqr(player) > 64.0D) return;

            TaskRegistry.TaskHandler handler = TaskRegistry.get(msg.taskType);
            if (handler == null) return;
            // 配置维度拆分 — 未实现 TaskConfigurable 的管线忽略配置动作
            if (handler.pipeline() instanceof com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable tc) {
                if (msg.payload == null) return;   // 双保险 (decode 已兜底, 防其余构造路径)
                tc.handleConfigAction(maid, msg.action, msg.payload);
            }
        });
    }
//?}

    /** 客户端发送 (C→S) */
    public static void send(int maidId, String taskType, byte action, CompoundTag payload) {
        LmaNetwork.sender.sendToServer(new TaskConfigActionPacket(maidId, taskType, action, payload));
    }
}

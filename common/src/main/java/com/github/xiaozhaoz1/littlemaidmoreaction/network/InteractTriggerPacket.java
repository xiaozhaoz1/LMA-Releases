package com.github.xiaozhaoz1.littlemaidmoreaction.network;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
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
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 任务手动触发包 (C→S) — 引擎级按键触发。
 *
 * <p>客户端按键 → 发送本包 → 服务端扫描玩家周围女仆 (范围见
 * {@link MoreActionConfig#BI_TRIGGER_RANGE}) → 按当前任务分发到
 * {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline#onPlayerTrigger}。
 * 任何任务覆写 onPlayerTrigger 即可响应按键, 无需改本包。
 */
//? if 1.20.1 {
public final class InteractTriggerPacket {
//?} else {
public final class InteractTriggerPacket implements CustomPacketPayload {
//?}

    private InteractTriggerPacket() {}

    public static void encode(InteractTriggerPacket msg, FriendlyByteBuf buf) {
        // 无字段 — player 从 NetworkEvent.Context 获取
    }

    public static InteractTriggerPacket decode(FriendlyByteBuf buf) {
        return new InteractTriggerPacket();
    }

//? if 1.20.1 {
    public static void handle(InteractTriggerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ServerLevel world = player.serverLevel();

            // v67.2: 扫描范围 Cloth Config 配置 (默认 10 格)
            AABB aabb = player.getBoundingBox().inflate(ActiveTaskConfig.BI_TRIGGER_RANGE.get());
            for (EntityMaid maid : world.getEntitiesOfClass(EntityMaid.class, aabb,
                m -> m.isAlive() && m.isOwnedBy(player))) {
                TaskRegistry.TaskHandler handler = TaskRegistry.get(FlowTaskData.getTask(maid));
                if (handler != null) handler.pipeline().onPlayerTrigger(maid, player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<InteractTriggerPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "interact_trigger"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, InteractTriggerPacket> STREAM_CODEC = StreamCodec.of(
        (ByteBuf buf, InteractTriggerPacket msg) -> encode(msg, (FriendlyByteBuf) buf),
        (ByteBuf buf) -> decode((FriendlyByteBuf) buf));

    public static void handlePayload(InteractTriggerPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel world = player.serverLevel();
            AABB aabb = player.getBoundingBox().inflate(ActiveTaskConfig.BI_TRIGGER_RANGE.get());
            for (EntityMaid maid : world.getEntitiesOfClass(EntityMaid.class, aabb,
                m -> m.isAlive() && m.isOwnedBy(player))) {
                TaskRegistry.TaskHandler handler = TaskRegistry.get(FlowTaskData.getTask(maid));
                if (handler != null) handler.pipeline().onPlayerTrigger(maid, player);
            }
        });
    }
//?}

    /** 客户端发送触发请求 */
    public static void sendToServer() {
        LmaNetwork.sender.sendToServer(new InteractTriggerPacket());
    }
}

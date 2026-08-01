package littlemaidmoreaction.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import littlemaidmoreaction.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 任务手动触发包 (C→S) — 引擎级按键触发。
 *
 * <p>客户端按键 → 发送本包 → 服务端扫描玩家周围女仆 (范围见
 * {@link MoreActionConfig#BI_TRIGGER_RANGE}) → 按当前任务分发到
 * {@link littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline#onPlayerTrigger}。
 * 任何任务覆写 onPlayerTrigger 即可响应按键, 无需改本包。
 */
public final class InteractTriggerPacket {

    private InteractTriggerPacket() {}

    public static void encode(InteractTriggerPacket msg, FriendlyByteBuf buf) {
        // 无字段 — player 从 NetworkEvent.Context 获取
    }

    public static InteractTriggerPacket decode(FriendlyByteBuf buf) {
        return new InteractTriggerPacket();
    }

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

    /** 客户端发送触发请求 */
    public static void sendToServer() {
        LittleMaidMoreAction.NETWORK.sendToServer(new InteractTriggerPacket());
    }
}

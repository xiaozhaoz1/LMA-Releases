package littlemaidmoreaction.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 通用任务配置请求包 (ID 5, C→S)。
 *
 * <p>客户端请求服务端发送指定任务的配置数据。
 * 服务端: 读 maid → TaskRegistry.get(taskType) → Pipeline.getConfigNbt(maid) → 回复 ReplyTaskConfigPacket。
 */
public final class RequestTaskConfigPacket {

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

    public static void handle(RequestTaskConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity e = player.serverLevel().getEntity(msg.maidId);
            if (!(e instanceof EntityMaid maid)) return;
            // v67.3: 权限 — 仅女仆所有者可读配置 (对齐 TaskConfigActionPacket)
            if (!maid.isOwnedBy(player)) return;

            TaskRegistry.TaskHandler handler = TaskRegistry.get(msg.taskType);
            if (handler == null) return;

            TaskPipeline pipeline = handler.pipeline();
            CompoundTag config = pipeline.getConfigNbt(maid);

            LittleMaidMoreAction.NETWORK.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ReplyTaskConfigPacket(msg.maidId, msg.taskType, config));
        });
        ctx.get().setPacketHandled(true);
    }

    /** 客户端发送配置请求 */
    public static void send(int maidId, String taskType) {
        LittleMaidMoreAction.NETWORK.sendToServer(new RequestTaskConfigPacket(maidId, taskType));
    }
}

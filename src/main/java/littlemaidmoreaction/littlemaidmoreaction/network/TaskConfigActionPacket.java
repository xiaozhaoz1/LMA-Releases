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
public final class TaskConfigActionPacket {

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
        return new TaskConfigActionPacket(buf.readInt(), buf.readUtf(), buf.readByte(), buf.readNbt());
    }

    public static void handle(TaskConfigActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity e = player.serverLevel().getEntity(msg.maidId);
            if (!(e instanceof EntityMaid maid)) return;
            // 权限: 仅女仆所有者可修改配置 (对齐 BlockInteractConfigMenu.stillValid)
            if (!maid.isOwnedBy(player)) return;

            TaskRegistry.TaskHandler handler = TaskRegistry.get(msg.taskType);
            if (handler == null) return;
            handler.pipeline().handleConfigAction(maid, msg.action, msg.payload);
        });
        ctx.get().setPacketHandled(true);
    }

    /** 客户端发送 (C→S) */
    public static void send(int maidId, String taskType, byte action, CompoundTag payload) {
        LittleMaidMoreAction.NETWORK.sendToServer(new TaskConfigActionPacket(maidId, taskType, action, payload));
    }
}

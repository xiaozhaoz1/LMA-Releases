package littlemaidmoreaction.littlemaidmoreaction.network;

import littlemaidmoreaction.littlemaidmoreaction.task.gui.LmaTaskConfigContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 通用任务配置响应包 (ID 6, S→C)。
 *
 * <p>服务端→客户端: 携带配置 NBT 快照。
 * 客户端: 当前屏幕如果是 LmaTaskConfigContainer → updateConfig(cfg)。
 */
public record ReplyTaskConfigPacket(int maidId, String taskType, CompoundTag config) {

    public static void encode(ReplyTaskConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId);
        buf.writeUtf(msg.taskType);
        buf.writeNbt(msg.config);
    }

    public static ReplyTaskConfigPacket decode(FriendlyByteBuf buf) {
        return new ReplyTaskConfigPacket(buf.readInt(), buf.readUtf(), buf.readNbt());
    }

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
}

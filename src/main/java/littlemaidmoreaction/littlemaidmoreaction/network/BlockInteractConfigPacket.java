package littlemaidmoreaction.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.pipeline.BlockInteractPipeline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * BlockInteract C→S 配置包 (ID 3) — 定时器开关/间隔调整/清除绑定。
 */
public final class BlockInteractConfigPacket {

    static final byte TOGGLE_TIMER = 0;
    static final byte SET_INTERVAL = 1;
    static final byte CLEAR_BIND = 2;

    private final int maidId;
    private final byte action;
    private final int value;

    public BlockInteractConfigPacket(int maidId, byte action, int value) {
        this.maidId = maidId;
        this.action = action;
        this.value = value;
    }

    public static void encode(BlockInteractConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId);
        buf.writeByte(msg.action);
        buf.writeInt(msg.value);
    }

    public static BlockInteractConfigPacket decode(FriendlyByteBuf buf) {
        return new BlockInteractConfigPacket(buf.readInt(), buf.readByte(), buf.readInt());
    }

    public static void handle(BlockInteractConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity e = player.serverLevel().getEntity(msg.maidId);
            if (!(e instanceof EntityMaid maid)) return;

            CompoundTag cfg = BlockInteractPipeline.config(maid);
            switch (msg.action) {
                case TOGGLE_TIMER -> {
                    boolean cur = cfg.getBoolean(BlockInteractPipeline.KEY_TIMER_ENABLED);
                    cfg.putBoolean(BlockInteractPipeline.KEY_TIMER_ENABLED, !cur);
                }
                case SET_INTERVAL -> cfg.putInt(BlockInteractPipeline.KEY_TIMER_INTERVAL, Math.max(1, msg.value));
                case CLEAR_BIND -> cfg.remove(BlockInteractPipeline.KEY_POS);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // ── 客户端发送 ──

    public static void toggleTimer(int maidId) {
        LittleMaidMoreAction.NETWORK.sendToServer(new BlockInteractConfigPacket(maidId, TOGGLE_TIMER, 0));
    }
    public static void setInterval(int maidId, int interval) {
        LittleMaidMoreAction.NETWORK.sendToServer(new BlockInteractConfigPacket(maidId, SET_INTERVAL, interval));
    }
    public static void clearBind(int maidId) {
        LittleMaidMoreAction.NETWORK.sendToServer(new BlockInteractConfigPacket(maidId, CLEAR_BIND, 0));
    }
}

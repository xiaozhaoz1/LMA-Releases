package littlemaidmoreaction.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * v7 动画数据同步包 — 将服务端 PlayAnimAction 写入的 PersistentData 同步到客户端。
 *
 * <p>在单机/专用服务器中，服务端 EntityMaid 和客户端 EntityMaid 是不同的对象，
 * PersistentData 不会自动同步。此包在 PlayAnimAction 写入数据后立即发送，
 * 客户端收到后将对应 key 写入客户端 EntityMaid 的 PersistentData，
 * 使得 {@code LmaMagicCastingProvider} 能读取到动画请求。
 */
public final class LmaAnimSyncMessage {
    private final int maidId;
    private final CompoundTag animData;

    public LmaAnimSyncMessage(int maidId, CompoundTag animData) {
        this.maidId = maidId;
        this.animData = animData;
    }

    public static void encode(LmaAnimSyncMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId);
        buf.writeNbt(msg.animData);
    }

    public static LmaAnimSyncMessage decode(FriendlyByteBuf buf) {
        return new LmaAnimSyncMessage(buf.readInt(), buf.readNbt());
    }

    public static void handle(LmaAnimSyncMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(LmaAnimSyncMessage msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(msg.maidId) instanceof EntityMaid maid) {
            CompoundTag clientData = maid.getPersistentData();
            // 合并服务端动画数据到客户端
            for (String key : msg.animData.getAllKeys()) {
                clientData.put(key, msg.animData.get(key).copy());
            }
        }
    }

    /**
     * 向追踪指定女仆的所有客户端发送 v7 动画数据。
     */
    public static void sendToTracking(EntityMaid maid, CompoundTag animData) {
        if (maid.level().isClientSide()) return;
        LittleMaidMoreAction.NETWORK.send(
            PacketDistributor.TRACKING_ENTITY.with(() -> maid),
            new LmaAnimSyncMessage(maid.getId(), animData));
    }
}

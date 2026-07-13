package littlemaidmoreaction.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.screen.MaidRuleListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * v8.7 打开独立规则编辑器网络包 — 服务端→客户端。
 *
 * <p>由 {@code OpenMaidEditorAction} 在服务端发送，
 * 客户端收到后打开 {@link MaidRuleListScreen}。</p>
 */
public final class OpenMaidEditorMessage {
    private final int maidId;

    public OpenMaidEditorMessage(int maidId) {
        this.maidId = maidId;
    }

    public static void encode(OpenMaidEditorMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maidId);
    }

    public static OpenMaidEditorMessage decode(FriendlyByteBuf buf) {
        return new OpenMaidEditorMessage(buf.readInt());
    }

    public static void handle(OpenMaidEditorMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenMaidEditorMessage msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(msg.maidId) instanceof EntityMaid maid) {
            Minecraft.getInstance().setScreen(new MaidRuleListScreen(maid));
        }
    }

    /**
     * 向指定玩家发送打开独立规则编辑器的请求。
     */
    public static void sendToPlayer(EntityMaid maid, ServerPlayer player) {
        if (maid.level().isClientSide()) return;
        LittleMaidMoreAction.NETWORK.send(
            PacketDistributor.PLAYER.with(() -> player),
            new OpenMaidEditorMessage(maid.getId()));
    }
}

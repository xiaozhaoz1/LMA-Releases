package littlemaidmoreaction.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import littlemaidmoreaction.littlemaidmoreaction.task.pipeline.BlockInteractPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.service.BlockInteractService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 右键交互手动触发包 (C→S)。
 *
 * <p>客户端按键 → 发送本包 → 服务端扫描玩家10格内女仆 → 执行交互。
 */
public final class InteractTriggerPacket {

    /** 10格扫描范围 */
    private static final double TRIGGER_RANGE = 10.0;

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

            AABB aabb = player.getBoundingBox().inflate(TRIGGER_RANGE);
            for (EntityMaid maid : world.getEntitiesOfClass(EntityMaid.class, aabb,
                m -> m.isAlive() && m.isOwnedBy(player))) {
                if (!"block_interact".equals(FlowTaskData.getTask(maid))) continue;
                CompoundTag cfg = BlockInteractPipeline.config(maid);
                if (!cfg.contains(BlockInteractPipeline.KEY_POS)) continue;
                BlockPos pos = NbtUtils.readBlockPos(cfg.getCompound(BlockInteractPipeline.KEY_POS));
                BlockInteractService.interact(world, maid, pos);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** 客户端发送触发请求 */
    public static void sendToServer() {
        LittleMaidMoreAction.NETWORK.sendToServer(new InteractTriggerPacket());
    }
}

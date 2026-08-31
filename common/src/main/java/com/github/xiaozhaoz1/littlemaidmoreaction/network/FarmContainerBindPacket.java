package com.github.xiaozhaoz1.littlemaidmoreaction.network;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
 * 容器角色绑定包 (C→S, v79.62 统一容器菜单) — 客户端容器绑定屏选角色 →
 * 服务端把「角色 + 容器坐标」写到玩家主手标记物品 (木棍) CUSTOM_DATA →
 * 之后右键女仆 (按任务) 交付到女仆 PD.
 *
 * <p>角色: seed=种子源 (farm) / harvest=收获目标 (farm) / take=取出源 (arm_transfer) /
 * deposit=放入目标 (arm_transfer).
 * 木棍 NBT 键: {@code farm_seed} / {@code farm_harvest} (BlockPos compound) +
 * 既有 {@code take} / {@code deposit}.
 */
//? if 1.20.1 {
public record FarmContainerBindPacket(String role, int x, int y, int z) {
//?} else {
public record FarmContainerBindPacket(String role, int x, int y, int z) implements CustomPacketPayload {
//?}

    public static void encode(FarmContainerBindPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.role(), 16);
        buf.writeInt(msg.x());
        buf.writeInt(msg.y());
        buf.writeInt(msg.z());
    }

    public static FarmContainerBindPacket decode(FriendlyByteBuf buf) {
        return new FarmContainerBindPacket(buf.readUtf(16), buf.readInt(), buf.readInt(), buf.readInt());
    }

//? if 1.20.1 {
    public static void handle(FarmContainerBindPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) bindContainer(player, msg);
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<FarmContainerBindPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "farm_container_bind"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, FarmContainerBindPacket> STREAM_CODEC =
        PacketCodecs.wrap(FarmContainerBindPacket::encode, FarmContainerBindPacket::decode);

    public static void handlePayload(FarmContainerBindPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) bindContainer(player, msg);
        });
    }
//?}

    /** 服务端: 校验主手标记物品 + 容器 → 写木棍 CUSTOM_DATA (role → BlockPos compound) */
    private static void bindContainer(ServerPlayer player, FarmContainerBindPacket msg) {
        if (msg.role().isEmpty() || msg.role().length() > 16) return;
        ItemStack held = player.getMainHandItem();
        if (!StickBindUtil.isMarkItem(held)) {
            player.sendSystemMessage(ComponentOf("§c主手需持有标记物品 (默认木棍)"));
            return;
        }
        BlockPos pos = new BlockPos(msg.x(), msg.y(), msg.z());
        if (player.level().isLoaded(pos) && !StickBindUtil.isContainer(player.level(), pos)) {
            player.sendSystemMessage(ComponentOf("§c目标方块不是容器"));
            return;
        }
        // 写主手 CUSTOM_DATA
        CompoundTag tag;
//? if 1.20.1 {
        tag = held.getOrCreateTag();
//?} else {
        var cd = held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        tag = cd.copyTag();
//?}
        String key = switch (msg.role()) {
            case "seed" -> "farm_seed";
            case "harvest" -> "farm_harvest";
            case "take" -> "take";
            case "deposit" -> "deposit";
            default -> null;
        };
        if (key == null) return;
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(tag, key, pos);
//? if !1.20.1 {
        held.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
//?}
        player.sendSystemMessage(ComponentOf("§a已标记" + roleName(msg.role()) + ": " + pos.toShortString()
                + " §7(右键女仆交付)"));
    }

    private static String roleName(String role) {
        return switch (role) {
            case "seed" -> "种子源箱";
            case "harvest" -> "收获目标箱";
            case "take" -> "取出源箱";
            case "deposit" -> "放入目标箱";
            default -> role;
        };
    }

    private static net.minecraft.network.chat.Component ComponentOf(String s) {
        return net.minecraft.network.chat.Component.literal(s);
    }

    /** 客户端: 选角色绑定当前主手标记物品到容器坐标 */
    public static void sendToServer(String role, BlockPos pos) {
        LmaNetwork.sender.sendToServer(new FarmContainerBindPacket(role, pos.getX(), pos.getY(), pos.getZ()));
    }
}

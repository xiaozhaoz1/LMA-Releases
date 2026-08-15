package com.github.xiaozhaoz1.littlemaidmoreaction.network;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
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
 * 通用按键触发包 (C→S, v79.51 KeyTrigger 线路) — 客户端按键 → 携带 keyId 发本包 →
 * 服务端查 {@link KeyTriggerRegistry} → 对玩家周围 owned 女仆 (范围见
 * {@link ActiveTaskConfig#BI_TRIGGER_RANGE}) 逐一回调 handler。
 *
 * <p>首个消费者 block_interact (恢复 v67 手动触发语义)。任意任务/系统用
 * {@link KeyTriggerRegistry#register(String, KeyTriggerHandler)} 注册即可响应按键, 无需改本包。
 * <p>NET-H1 (v79.50): C2S 服务端挂 per-player 20t 节流 ({@link C2SThrottle}) — 连发按键超频直接丢弃。
 */
//? if 1.20.1 {
public record InteractTriggerPacket(String keyId) {
//?} else {
public record InteractTriggerPacket(String keyId) implements CustomPacketPayload {
//?}

    /** NET-H1 防刷: 同 player 每 20t (1 秒) 最多放行 1 次按键触发 (AABB 扫描不可高频) */
    private static final long THROTTLE_TICKS = 20L;
    private static final String THROTTLE_KEY = "key_trigger";

    public static void encode(InteractTriggerPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.keyId());
    }

    public static InteractTriggerPacket decode(FriendlyByteBuf buf) {
        return new InteractTriggerPacket(buf.readUtf());
    }

//? if 1.20.1 {
    public static void handle(InteractTriggerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            dispatch(player, msg.keyId());
        });
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<InteractTriggerPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "interact_trigger"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, InteractTriggerPacket> STREAM_CODEC =
        PacketCodecs.wrap(InteractTriggerPacket::encode, InteractTriggerPacket::decode);

    public static void handlePayload(InteractTriggerPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            dispatch(player, msg.keyId());
        });
    }
//?}

    /** 服务端分发 (双平台共用): 节流 → 查注册表 (未注册 id 静默) → 范围扫描 owned 女仆逐一回调 */
    private static void dispatch(ServerPlayer player, String keyId) {
        if (!C2SThrottle.allow(player, THROTTLE_KEY, THROTTLE_TICKS)) {
            return;
        }
        KeyTriggerHandler handler = KeyTriggerRegistry.get(keyId);
        if (handler == null) {
            return;
        }
        ServerLevel world = player.serverLevel();
        // 扫描范围 Cloth Config 配置 (默认 10 格)
        AABB aabb = player.getBoundingBox().inflate(ActiveTaskConfig.BI_TRIGGER_RANGE.get());
        for (EntityMaid maid : world.getEntitiesOfClass(EntityMaid.class, aabb,
                m -> m.isAlive() && m.isOwnedBy(player))) {
            handler.handle(maid, player);
        }
    }

    /** 客户端发送触发请求 (keyId = KeyTriggerRegistry 注册 id) */
    public static void sendToServer(String keyId) {
        LmaNetwork.sender.sendToServer(new InteractTriggerPacket(keyId));
    }
}

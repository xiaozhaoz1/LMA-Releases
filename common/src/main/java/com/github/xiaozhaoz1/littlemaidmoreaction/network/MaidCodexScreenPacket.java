package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.MaidCodexScreenOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
//? if 1.20.1 {
import net.minecraftforge.network.NetworkEvent;
//?}
//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * v79.47: 图鉴界面包 (S→C) — 图鉴书右键, 服务端合并玩家全部女仆击杀计数后直发,
 * 客户端 handle 打开 {@code MaidCodexScreen} (零 C2S, 数据随包)。
 */
//? if 1.20.1 {
public final class MaidCodexScreenPacket {
//?} else {
public final class MaidCodexScreenPacket implements CustomPacketPayload {
//?}

    /** 单包计数条目上限 — S2C 客户端信任边界 */
    private static final int MAX_COUNTS = 1024;
    /** 纯 JDK 日志 — decode 路径可被纯 JVM 测试触发 (错题 #174) */
    private static final System.Logger LOG = System.getLogger("LMA-Net-MaidCodex");

    private final Map<String, Integer> counts;

    public MaidCodexScreenPacket(Map<String, Integer> counts) {
        this.counts = counts;
    }

    public Map<String, Integer> getCounts() {
        return counts;
    }

    public static void encode(MaidCodexScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.counts.size());
        for (var e : msg.counts.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    public static MaidCodexScreenPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_COUNTS) {
            LOG.log(System.Logger.Level.WARNING, "[LMA] 图鉴包条目数异常 ({0}), 丢弃", n);
            return new MaidCodexScreenPacket(Map.of());
        }
        Map<String, Integer> counts = new LinkedHashMap<>(n);
        for (int i = 0; i < n; i++) {
            counts.put(buf.readUtf(), buf.readVarInt());
        }
        return new MaidCodexScreenPacket(counts);
    }

    // v79.50: 图鉴屏打开走接口注入 (MaidCodexScreenOpener) — 字节码禁 Screen 引用
    // (DEDICATED_SERVER RuntimeDistCleaner 拦截实证), 客户端入口注入实现
    public static MaidCodexScreenOpener opener;

    //? if 1.20.1 {
    public static void handle(MaidCodexScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (opener != null) opener.openCodex(msg.counts);
        });
        ctx.get().setPacketHandled(true);
    }
    //?}
    //? if !1.20.1 {
    public static final CustomPacketPayload.Type<MaidCodexScreenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_codex_screen"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, MaidCodexScreenPacket> STREAM_CODEC =
            PacketCodecs.wrap(MaidCodexScreenPacket::encode, MaidCodexScreenPacket::decode);

    public static void handlePayload(MaidCodexScreenPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (opener != null) opener.openCodex(msg.counts);
        });
    }
    //?}

    /** 服务端直发 (图鉴书右键 → 打开界面) */
    public static void sendTo(ServerPlayer player, Map<String, Integer> counts) {
        LmaNetwork.sender.sendToPlayer(player, new MaidCodexScreenPacket(counts));
    }
}

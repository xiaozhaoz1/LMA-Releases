package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.client.AnimFileSyncClientHandler;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader;
import net.minecraft.network.FriendlyByteBuf;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 动画文件同步包 (v79.18, S→C) — 专用服务器把 {@code config/animations/*.animation.json}
 * 推送给客户端, 解决「服务器端自定义动画文件客户端接收不到」问题。
 *
 * <p>发送: 玩家加入 (PlayerLoggedInEvent) → {@link #pushAllTo} 全量推送 (每文件一包)。
 * 接收: 客户端校验文件名/大小/JSON → 写入本地 config/animations/ →
 * {@link StartupLoader#reload()} + {@code DynamicAnimationResources.reload()} 重载资源 →
 * {@code client.AnimationResourceRegistrar.remergeAll()} 热合并进 TLM geckolib ISS AnimationFile。
 *
 * <p>2026-09-21: 接收侧 (校验之后的落盘/防抖/重载链) 外移至
 * {@link AnimFileSyncClientHandler} —— 包定义只留数据 + 编解码 + 发送/处理入口, 不引客户端类。
 * 纯校验 {@link #isValidFileName} 与大小上限留在本类 (零依赖纯函数, 单测直接用)。
 *
 * <p>纯 S2C: neoforge 分支拒绝 serverbound 伪造包; forge 分支注册为 PLAY_TO_CLIENT。
 */
//? if 1.20.1 {
public final class AnimFileSyncPacket {
//?} else {
public final class AnimFileSyncPacket implements CustomPacketPayload {
//?}
    /** 文件名白名单后缀 (与 StartupLoader 扫描规则一致) */
    private static final String SUFFIX = ".animation.json";
    /** 单文件大小上限 (512KB) — 防滥用 */
    public static final int MAX_BYTES = 512 * 1024;
    /** 文件名长度上限 — 防超长名写盘失败 */
    private static final int MAX_NAME_LEN = 64;
    /** Windows 保留设备名 (含 COM1-9/LPT1-9, 大小写不敏感) — 恶意服务器推此类文件名会写盘失败/命中设备 */
    private static final Set<String> RESERVED_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final String fileName;
    private final byte[] content;

    public AnimFileSyncPacket(String fileName, byte[] content) {
        this.fileName = fileName;
        this.content = content;
    }

    public static void encode(AnimFileSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.fileName);
        buf.writeByteArray(msg.content);
    }

    public static AnimFileSyncPacket decode(FriendlyByteBuf buf) {
        return new AnimFileSyncPacket(buf.readUtf(), buf.readByteArray());
    }

//? if 1.20.1 {
    public static void handle(AnimFileSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> AnimFileSyncClientHandler.handle(msg.fileName, msg.content));
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<AnimFileSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "anim_file_sync"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, AnimFileSyncPacket> STREAM_CODEC =
        PacketCodecs.wrap(AnimFileSyncPacket::encode, AnimFileSyncPacket::decode);

    public static void handlePayload(AnimFileSyncPacket msg, IPayloadContext ctx) {
        // 纯 S2C — 拒绝客户端→服务器伪造包
        if (ctx.flow().isServerbound()) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 拒绝 serverbound 动画文件包");
            return;
        }
        ctx.enqueueWork(() -> AnimFileSyncClientHandler.handle(msg.fileName, msg.content));
    }
//?}

    // ======================== 接收侧校验 (纯函数, 客户端处理器与单测共用) ========================

    /**
     * 文件名校验: 白名单后缀 + 禁路径分隔符/.. /冒号 (防路径遍历) +
     * 长度上限 + Windows 保留设备名 (防写盘失败/命中设备)。
     *
     * <p>2026-09-21 起为 public (客户端处理器 {@link AnimFileSyncClientHandler} 跨包调用);
     * 本方法保持**零依赖纯函数**, 单测 {@code AnimFileSyncPacketTest} 直接调 (错题 #174)。
     */
    public static boolean isValidFileName(String name) {
        if (name == null || name.length() > MAX_NAME_LEN) return false;
        if (!name.toLowerCase(Locale.ROOT).endsWith(SUFFIX)) return false;
        if (name.contains("\\") || name.contains("/") || name.contains("..") || name.contains(":")) return false;
        int dot = name.indexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return !RESERVED_DEVICE_NAMES.contains(base.toUpperCase(Locale.ROOT));
    }

    // ======================== 发送侧 (服务器) ========================

    /**
     * 服务器全量推送 config/animations/ 下全部动画文件给指定玩家 (每文件一包)。
     * 玩家加入事件调用; 跳过超大小文件, 单个读失败不影响其余。
     */
    public static void pushAllTo(ServerPlayer player) {
        int sent = 0;
        Path dir = LittleMaidMoreAction.CONFIG_DIR.resolve("animations");
        for (String file : StartupLoader.getAnimationFiles()) {
            Path p = dir.resolve(file);
            try {
                if (!Files.isRegularFile(p)) continue;
                byte[] bytes = Files.readAllBytes(p);
                if (bytes.length > MAX_BYTES) {
                    LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 跳过超大小文件: {}", file);
                    continue;
                }
                LmaNetwork.sender.sendToPlayer(player, new AnimFileSyncPacket(file, bytes));
                sent++;
            } catch (IOException e) {
                LittleMaidMoreAction.LOGGER.error("[LMA/AnimSync] 读取动画文件失败: {}", file, e);
            }
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/AnimSync] 推送 {} 个动画文件给 {}", sent, player.getGameProfile().getName());
    }
}

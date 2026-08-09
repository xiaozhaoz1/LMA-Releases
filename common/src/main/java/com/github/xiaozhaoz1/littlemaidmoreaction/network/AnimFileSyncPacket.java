package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationResourceRegistrar;
import com.github.xiaozhaoz1.littlemaidmoreaction.resource.DynamicAnimationResources;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
//?} else {
import net.neoforged.api.distmarker.Dist;
//?}
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.OnlyIn;
//?}
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 动画文件同步包 (v79.18, S→C) — 专用服务器把 {@code config/animations/*.animation.json}
 * 推送给客户端, 解决「服务器端自定义动画文件客户端接收不到」问题。
 *
 * <p>发送: 玩家加入 (PlayerLoggedInEvent) → {@link #pushAllTo} 全量推送 (每文件一包)。
 * 接收: 客户端校验文件名/大小/JSON → 写入本地 config/animations/ →
 * {@link StartupLoader#reload()} + {@link DynamicAnimationResources#reload()} 重载资源 →
 * {@link AnimationResourceRegistrar#remergeAll()} 热合并进 TLM geckolib ISS AnimationFile。
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
    /** v79.26.2 防抖窗口 (毫秒) — 动画文件包全到齐后再统一 reload */
    private static final long FLUSH_DELAY_MS = 2000L;
    /** 最近一次落盘时间戳 (0 = 无 pending) — 防抖合并 7 次全量 reload → 1 次 */
    private static long pendingWriteMs = 0L;

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
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }
//?}
//? if !1.20.1 {
    public static final CustomPacketPayload.Type<AnimFileSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "anim_file_sync"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, AnimFileSyncPacket> STREAM_CODEC = StreamCodec.of(
        (ByteBuf buf, AnimFileSyncPacket msg) -> encode(msg, (FriendlyByteBuf) buf),
        (ByteBuf buf) -> decode((FriendlyByteBuf) buf));

    public static void handlePayload(AnimFileSyncPacket msg, IPayloadContext ctx) {
        // 纯 S2C — 拒绝客户端→服务器伪造包
        if (ctx.flow().isServerbound()) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 拒绝 serverbound 动画文件包");
            return;
        }
        ctx.enqueueWork(() -> handleClient(msg));
    }
//?}

    // ======================== 接收侧 (客户端) ========================

    /**
     * 客户端处理: 校验 → 落盘 → 重载资源 → 热合并 ISS。
     * 坏文件 (非法 JSON / 超大小 / 非法文件名) 逐项拒绝, 不影响已有动画。
     */
    @OnlyIn(Dist.CLIENT)
    private static void handleClient(AnimFileSyncPacket msg) {
        if (!isValidFileName(msg.fileName)) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 拒绝非法文件名: {}", msg.fileName);
            return;
        }
        if (msg.content.length > MAX_BYTES) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 文件超大小上限 ({} bytes): {}", msg.content.length, msg.fileName);
            return;
        }
        // merge 只捕 ChainedJsonException — 坏 JSON 落盘会导致客户端加载崩溃, 先校验
        String json = new String(msg.content, StandardCharsets.UTF_8);
        try {
            com.google.gson.JsonParser.parseString(json);
        } catch (com.google.gson.JsonParseException e) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 拒绝非法 JSON: {}", msg.fileName);
            return;
        }
        try {
            Path dir = LittleMaidMoreAction.CONFIG_DIR.resolve("animations");
            Files.createDirectories(dir);
            Files.write(dir.resolve(msg.fileName), msg.content);
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/AnimSync] 写入动画文件失败: {}", msg.fileName, e);
            return;
        }
        // v79.26.2 卡顿修复: 只落盘不立即 reload — 服务端 pushAllTo 一次连发 7 包, 旧实现每包
        // 全量 reload 链 (StartupLoader + DynamicAnimationResources + remergeAll + YsmInject 528 次
        // 磁盘 IO) 在渲染线程阻塞 ~5.5 秒/包 × 7 = 40 秒 (加载世界卡很久日志实证)。
        // 现: 防抖 2 秒无新包 → flushPending 统一 reload 一次 (由 YsmReloadListener.onClientTick 驱动)。
        pendingWriteMs = System.currentTimeMillis();
        LittleMaidMoreAction.LOGGER.info("[LMA/AnimSync] 已接收动画文件: {}", msg.fileName);
    }

    /**
     * v79.26.2: 防抖刷新 — 客户端每 tick 调用 (YsmReloadListener.onClientTick 挂载)。
     * 落盘后 2 秒无新包 = 全批已到 → 执行一次完整 reload 链 (7 次全量重载 → 1 次)。
     */
    public static void flushPending() {
        if (pendingWriteMs == 0L) {
            return;
        }
        if (System.currentTimeMillis() - pendingWriteMs < FLUSH_DELAY_MS) {
            return;
        }
        pendingWriteMs = 0L;
        StartupLoader.reload();
        DynamicAnimationResources resources = DynamicAnimationResources.instance;
        if (resources != null) {
            resources.reload();
        }
        AnimationResourceRegistrar.remergeAll();
        // v79.18: 玩家加入时 YSM 模型包文件必已生成 (构造期存在竞态) → 在此重试 YSM 动画注入 (幂等;
        // v79.26.2 指纹快检: 源未变零 IO)
        com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmAnimInjector.injectHaqiIfNeeded();
        LittleMaidMoreAction.LOGGER.info("[LMA/AnimSync] 动画批量注册完成");
    }

    /**
     * 文件名校验: 白名单后缀 + 禁路径分隔符/.. /冒号 (防路径遍历)。
     */
    static boolean isValidFileName(String name) {
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(SUFFIX)) return false;
        return !name.contains("\\") && !name.contains("/") && !name.contains("..") && !name.contains(":");
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

package com.github.xiaozhaoz1.littlemaidmoreaction.network.client;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.client.AnimationResourceRegistrar;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.AnimFileSyncPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.resource.DynamicAnimationResources;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 动画文件同步包 — 客户端接收侧处理 (2026-09-21 由 {@code AnimFileSyncPacket} 外移)。
 *
 * <p>链路: 校验 (文件名/大小/JSON) → 落盘 {@code config/animations/} → **防抖** (2s) →
 * 统一执行一次完整 reload 链 ({@code StartupLoader.reload} + {@code DynamicAnimationResources.reload}
 * + {@code AnimationResourceRegistrar.remergeAll} + YSM 动画注入重试)。
 *
 * <p><b>为什么防抖</b> (原注释逐字保留): 服务端 {@code pushAllTo} 一次连发 7 包,
 * 旧实现每包全量 reload 链 (528 次磁盘 IO) 在渲染线程阻塞 ~5.5 秒/包 × 7 = 40 秒 (加载世界卡很久, 日志实证)。
 *
 * <p><b>纯校验留包定义</b>: {@code AnimFileSyncPacket.isValidFileName} 留在网络包类里 ——
 * 它是零依赖纯函数且被单测 {@code AnimFileSyncPacketTest} 直接调 (错题 #174 铁律: 纯逻辑不进客户端类,
 * 否则单测被 MC 类加载拖下水)。
 *
 * <p>无世界/未进服不影响本链路 (只碰 config 目录与资源重载); 由 {@code YsmReloadListener.onClientTick} 每 tick 驱动。
 */
@OnlyIn(Dist.CLIENT)
public final class AnimFileSyncClientHandler {

    /** 防抖窗口 (毫秒) — 动画文件包全到齐后再统一 reload */
    private static final long FLUSH_DELAY_MS = 2000L;
    /** 最近一次落盘时间戳 (0 = 无 pending) — 防抖合并 7 次全量 reload → 1 次 */
    private static long pendingWriteMs = 0L;

    private AnimFileSyncClientHandler() {}

    /**
     * 客户端处理: 校验 → 落盘 → 打防抖戳 (真正的 reload 交给 {@link #flushPending})。
     * 坏文件 (非法 JSON / 超大小 / 非法文件名) 逐项拒绝, 不影响已有动画。
     */
    public static void handle(String fileName, byte[] content) {
        if (!AnimFileSyncPacket.isValidFileName(fileName)) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 拒绝非法文件名: {}", fileName);
            return;
        }
        if (content.length > AnimFileSyncPacket.MAX_BYTES) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 文件超大小上限 ({} bytes): {}", content.length, fileName);
            return;
        }
        // merge 只捕 ChainedJsonException — 坏 JSON 落盘会导致客户端加载崩溃, 先校验
        String json = new String(content, StandardCharsets.UTF_8);
        try {
            com.google.gson.JsonParser.parseString(json);
        } catch (com.google.gson.JsonParseException e) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimSync] 拒绝非法 JSON: {}", fileName);
            return;
        }
        try {
            Path dir = LittleMaidMoreAction.CONFIG_DIR.resolve("animations");
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), content);
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/AnimSync] 写入动画文件失败: {}", fileName, e);
            return;
        }
        // 卡顿修复: 只落盘不立即 reload — 服务端 pushAllTo 一次连发 7 包, 旧实现每包
        // 全量 reload 链 (StartupLoader + DynamicAnimationResources + remergeAll + YsmInject 528 次
        // 磁盘 IO) 在渲染线程阻塞 ~5.5 秒/包 × 7 = 40 秒 (加载世界卡很久日志实证)。
        // 现: 防抖 2 秒无新包 → flushPending 统一 reload 一次 (由 YsmReloadListener.onClientTick 驱动)。
        pendingWriteMs = System.currentTimeMillis();
        LittleMaidMoreAction.LOGGER.info("[LMA/AnimSync] 已接收动画文件: {}", fileName);
    }

    /**
     * 防抖刷新 — 客户端每 tick 调用 (YsmReloadListener.onClientTick 挂载)。
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
        // 玩家加入时 YSM 模型包文件必已生成 (构造期存在竞态) → 在此重试 YSM 动画注入 (幂等;
        // 指纹快检: 源未变零 IO)
        com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmAnimInjector.injectHaqiIfNeeded();
        LittleMaidMoreAction.LOGGER.info("[LMA/AnimSync] 动画批量注册完成");
    }
}

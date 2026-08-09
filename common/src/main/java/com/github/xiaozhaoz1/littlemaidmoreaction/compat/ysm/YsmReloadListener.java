package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm;

import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationResourceRegistrar;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YSM 动画注入重载监听 (v79.18) — 客户端资源重载 (启动/F3+T) 时执行:
 *
 * <p>prepare (并行阶段): ① YSM 模型包文件已生成 → 注入 tlm.animation.json (构造期竞态兜底)
 * ② 直接把 LMA 动画合并进 {@link GeckoModelLoader#DEFAULT_ISS_ANIMATION_FILE} —
 * **绕过 DefaultGeckoAnimationEvent** (v79.18 实测: neoforge 上事件链路不可靠, listener 注册成功
 * + TLM post 执行但事件从不分发, 5 轮排查无果 — 见记忆 lma-v79.18-haqi-ysm-anim 错题 #74/#77)。
 *
 * <p>apply (全部 prepare 完成后): TLM CustomPackLoader.reloadPacks 已用 DEFAULT_ISS 构建完
 * 模型 AnimationFile (GeckoLibCache) — 遍历补全 LMA 动画 (播放时控制器从模型文件查名)。
 * 此时再合并仍会被下次重载覆盖, 但 prepare 的 DEFAULT_ISS 合并已保证模型文件含动画。</p>
 *
 * <p><b>v79.25 通用化</b> (用户裁定: "网络应该是通用的支持动画同步不是只支持单一动画同步"):
 * 不再硬编码 4 个源文件 — 遍历 {@link StartupLoader#getAnimationFiles()} 全部动画文件,
 * 从磁盘 config/littlemaidmoreaction/animations/ 读取 (与 AnimFileSyncPacket 网络同步落盘同路径) —
 * 新动画文件零代码接入 TLM geckolib 渲染通道。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class YsmReloadListener extends SimplePreparableReloadListener<Void> {

    /** tick 延迟补全已执行标志 (幂等 — 只补全一次) */
    private static boolean complemented = false;

    /**
     * 源文件解析缓存 (v79.26 卡顿修复) — 每动画文件只磁盘读取 + JSON/Molang 解析一次。
     * 原实现每模型文件每次全量重读重解析: GeckoLibCache 123 模型 × 8 动画 = 984 次磁盘 IO
     * 全在 Render thread 同步跑 4.3 秒 (进游戏卡顿日志风暴实证)。
     * 现: 磁盘 IO + 解析 984 → 8 次; 剩余为纯内存 putAnimation 合并。
     */
    private static final Map<String, AnimationFile> PARSED_SOURCES = new ConcurrentHashMap<>();

    /**
     * 幂等去重 (v79.26) — 同 AnimationFile 实例的同源文件只合并一次。
     * 用引用相等 (IdentityHashMap): 资源重载 (F3+T) 后 GeckoLibCache 重建 → 新实例 → 自动重合并;
     * 内容相等但实例不同不跳过 (record equals 值比较会误跳, 动画更新不生效 — 故不用值比较)。
     */
    private static final Map<AnimationFile, Set<String>> MERGED_SOURCES = new IdentityHashMap<>();

    /**
     * v79.18: 客户端 tick 延迟补全 — TLM 模型加载是异步的 (ReloadResourceEvent →
     * CompletableFuture.supplyAsync, 实测首次启动晚于 reload listener 16 秒),
     * apply 阶段 GeckoLibCache 可能为空 (补全 0 个 → 首次启动不生效, F3+T 才生效)。
     * 检测到模型文件非空即补全一次 (幂等)。由入口注册 ClientTickEvent.Post 驱动。
     */
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick() {
        // v79.26.2: 动画文件包防抖刷新 — 每 tick 检查 (2 秒无新包才执行, 渲染线程不再卡包)
        com.github.xiaozhaoz1.littlemaidmoreaction.network.AnimFileSyncPacket.flushPending();
        if (complemented) return;
        var files = GeckoLibCache.getInstance().getAnimations().values();
        if (files.isEmpty()) return;
        complemented = true;
        LittleMaidMoreAction.LOGGER.info("[LMA/YsmReload] tick 补全 {} 个模型文件", files.size());
        for (AnimationFile file : files) {
            mergeAllInto(file);
        }
    }

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        YsmAnimInjector.injectHaqiIfNeeded();
        // 绕过事件链路: 合并进 DEFAULT_MAID + DEFAULT_ISS (registerMaidAnimations 会把两者合进模型文件;
        // 1.5.3 magic casting 播放链查询类型未实证, 双类型双保险 — 用户提示 "MAIN 和 ISS 要区分")
        mergeAllInto(GeckoModelLoader.DEFAULT_MAID_ANIMATION_FILE);
        mergeAllInto(GeckoModelLoader.DEFAULT_ISS_ANIMATION_FILE);
        AnimationResourceRegistrar.remergeAll();
        return null;
    }

    @Override
    protected void apply(Void data, ResourceManager resourceManager, ProfilerFiller profiler) {
        // 模型文件已构建 (所有 prepare 完成) — 补全 (首次启动时序竞争兜底: prepare 合并可能晚于 TLM 读 DEFAULT_*)
        var files = GeckoLibCache.getInstance().getAnimations().values();
        LittleMaidMoreAction.LOGGER.info("[LMA/YsmReload] apply: 补全 {} 个模型文件", files.size());
        for (AnimationFile file : files) {
            mergeAllInto(file);
        }
    }

    /**
     * 合并全部 LMA 动画文件 (config/animations/*.animation.json — v79.25 通用化, 磁盘读取)
     * 进目标 AnimationFile。haqi (对女仆) / maimeng (对主人) + 原版模型 vanilla 版等全部覆盖;
     * 自定义动画文件放 config 即自动进 TLM geckolib 渲染通道 (网络同步落盘同路径)。
     */
    private static void mergeAllInto(AnimationFile target) {
        if (target == null) return;
        Set<String> done;
        synchronized (MERGED_SOURCES) {
            done = MERGED_SOURCES.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet());
        }
        for (String file : StartupLoader.getAnimationFiles()) {
            if (!done.add(file)) continue;  // 同实例同源已合并 — 跳过 (重复合并 = 纯浪费)
            AnimationFile src = parseCached(file);
            if (src == null) continue;
            src.animations().forEach(target::putAnimation);  // 纯内存合并 (TLM mergeAnimationFile 内部逻辑等价)
            LittleMaidMoreAction.LOGGER.debug("[LMA/YsmReload] 合并 {} 进 {}",
                    file,
                    target == GeckoModelLoader.DEFAULT_ISS_ANIMATION_FILE ? "DEFAULT_ISS" : "模型文件");
        }
    }

    /** 源文件解析缓存 — 磁盘读取 + JSON 解析每源文件仅一次 (线程安全懒加载) */
    private static AnimationFile parseCached(String file) {
        AnimationFile cached = PARSED_SOURCES.get(file);
        if (cached != null) return cached;
        Path p = StartupLoader.getAnimDir().resolve(file);
        AnimationFile parsed = new AnimationFile();
        try (InputStream in = Files.newInputStream(p)) {
            // public API: 解析进空文件即得解析结果 (等价 TLM private getAnimationFile)
            GeckoModelLoader.mergeAnimationFile(in, parsed);
            PARSED_SOURCES.put(file, parsed);
            return parsed;
        } catch (IOException | RuntimeException e) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/YsmReload] 读取动画失败: {} — {}", file, e.getMessage());
            return null;
        }
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;

import java.io.IOException;
import java.io.InputStream;

/**
 * 动画资源注册器 — 自定义动画扫描、TLM 注册。
 *
 * <p>从 MoreActionAPI 拆分 (v7)。
 * v7.1: 移除旧 AnimationState 注册（已由 MagicCasting Provider 替代）。
 * v79.18: 缓存 ISS AnimationFile 引用 + 热重合并 (S2C 动画文件同步后调用)。</p>
 */
public final class AnimationResourceRegistrar {

    /** 缓存 ISS AnimationFile 引用 (mutable) — S2C 文件同步后热合并入口 */
    @OnlyIn(Dist.CLIENT)
    private static AnimationFile cachedIISSFile;

    /** FORGE 总线：扫描 config/animations/ 目录，注册 .animation.json 到 TLM */
    public static void registerCustomAnimations(DefaultGeckoAnimationEvent event) {
        LittleMaidMoreAction.LOGGER.info("[LMA/Registrar] DefaultGeckoAnimationEvent 到达 (thread={})", Thread.currentThread().getName());
        cachedIISSFile = event.getAnimationFile(DefaultGeckoAnimationEvent.AnimationType.ISS);
        for (String file : com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader.getAnimationFiles()) {
            registerAnimation(event,
                    ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "animations/" + file));
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/Registrar] 注册 {} 个动画到 TLM",
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader.getAnimationFiles().size());
    }

    @OnlyIn(Dist.CLIENT)
    public static void scanCustomAnimations() {
        LittleMaidMoreAction.LOGGER.info("[LMA/Registrar] 动画就绪 ({} 文件)",
                com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader.getAnimationFiles().size());
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerAnimation(DefaultGeckoAnimationEvent event, ResourceLocation path) {
        event.addAnimation(DefaultGeckoAnimationEvent.AnimationType.ISS, path);
        if (MoreActionConfig.DEBUG_MODE.get()) {
            LittleMaidMoreAction.LOGGER.debug("[LMA/Registrar] 注册动画资源: {}", path);
        }
    }

    /**
     * 热重合并 — 新收到的动画文件已重载进资源管理器后,
     * 重新合并全部动画文件进启动时缓存的 ISS AnimationFile (与启动注册同一路径)。
     * geckolib 解析失败逐文件捕获, 不影响其余动画。
     */
    /** ISS 未缓存 WARN 只打一次 — 后续重复 reload 静默 (日志风暴防护) */
    @OnlyIn(Dist.CLIENT)
    private static boolean warnedIssUncached = false;

    @OnlyIn(Dist.CLIENT)
    public static void remergeAll() {
        if (cachedIISSFile == null) {
            // 实证: neoforge 事件链路不可靠 → 启动时可能一直未缓存, 每包 WARN 刷屏 (日志实证 7 次)
            if (!warnedIssUncached) {
                warnedIssUncached = true;
                LittleMaidMoreAction.LOGGER.warn("[LMA/Registrar] ISS AnimationFile 未缓存, 跳过热合并");
            }
            return;
        }
        int merged = 0;
        for (String file : com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader.getAnimationFiles()) {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "animations/" + file);
            try (InputStream stream = Minecraft.getInstance().getResourceManager().open(rl)) {
                GeckoModelLoader.mergeAnimationFile(stream, cachedIISSFile);
                merged++;
            } catch (IOException | RuntimeException e) {
                LittleMaidMoreAction.LOGGER.warn("[LMA/Registrar] 热合并失败: {} — {}", file, e.getMessage());
            }
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/Registrar] 热合并完成 — {} 个动画", merged);
    }
}

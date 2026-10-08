package com.github.xiaozhaoz1.littlemaidmoreaction.compat;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;

/**
 * Numen (言出法随) 兼容 (v73) — 存在性检测 + 共存声明。
 *
 * <p><b>诚实标注</b>: LMA AI 操控走 TLM AI 环 (对话/配置/工具注册 — 1.5.3 实证),
 * 与 Numen 无运行时依赖; Numen 的价值 = 28 工具模式参考 (已吸收进 compat/ai/tool 工具设计)。
 * 本类仅检测 Numen 是否安装, 用于日志/任务提示 (轻量共存, 不做跨 mod 文件操作)。
 */
public final class NumenCompat {

    private static volatile Boolean INSTALLED;

    private NumenCompat() {}

    /** Numen mod 本体是否安装 (modId = "numen", 惰性缓存) */
    public static boolean isInstalled() {
        Boolean cached = INSTALLED;
        if (cached != null) return cached;
//? if 1.20.1 {
        boolean loaded = net.minecraftforge.fml.ModList.get().isLoaded("numen");
//?} else {
        boolean loaded = net.neoforged.fml.ModList.get().isLoaded("numen");
//?}
        INSTALLED = loaded;
        if (loaded) {
            LittleMaidMoreAction.LOGGER.info("[NumenCompat] Numen 已安装 — LMA AI 工具 (TLM AI 环) 与其共存, 模式参考吸收");
        }
        return loaded;
    }
}

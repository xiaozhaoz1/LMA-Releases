package com.github.xiaozhaoz1.littlemaidmoreaction.compat;

/**
 * YSM (Yes Steve Model) 兼容 (v75.1) — 存在性 + 模块开关检测。
 *
 * <p>v75 石板化管线双门控 (用户要求): 假人 = 玩家实体 → 玩家渲染 + YSM 模型
 * (假人自动继承女仆模型靠 `/yes_steve_model model set` 指令)。YSM 未装 → 假人只能
 * 显示玩家皮肤 — 变身功能应禁用并提示。modId = "yes_steve_model" (neoforge.mods.toml 实证)。
 *
 * <p>2026-08-11c: isInstalled() 并入 CompatToggle 门控 ("ysm" 模块项 — CompatRegistry 模块表
 * 单一事实源, GUI 可开关) — 模块关闭 = 未安装语义 (假人桥 TRANSFORM_ACTIVATOR / ai_control
 * 前置提示经 isInstalled 全链生效)。INSTALLED 惰性缓存保留 — 与 CompatToggle "注册期生效
 * (重启应用)" 语义一致。
 */
public final class YsmCompat {

    private static volatile Boolean INSTALLED;

    private YsmCompat() {}

    /** YSM mod 是否安装且模块启用 (modId = "yes_steve_model", 惰性缓存; neoforge 含 OpenYSM) */
    public static boolean isInstalled() {
        Boolean cached = INSTALLED;
        if (cached != null) return cached;
//? if 1.20.1 {
        boolean loaded = net.minecraftforge.fml.ModList.get().isLoaded("yes_steve_model");
//?} else {
        boolean loaded = net.neoforged.fml.ModList.get().isLoaded("yes_steve_model")
                || net.neoforged.fml.ModList.get().isLoaded("openysm");   // OpenYSM 开源版
//?}
        INSTALLED = loaded && CompatToggle.isModuleEnabled("ysm");
        return INSTALLED;
    }

    /** OpenYSM 开源版 (org.openysm API — 假人模型直接设置必需; 混淆版无此 API) */
    public static boolean isOpenYsm() {
//? if 1.20.1 {
        return false;
//?} else {
        return net.neoforged.fml.ModList.get().isLoaded("openysm");
//?}
    }

    /** 石板化管线是否可用 (双门控: Numen + YSM) */
    public static boolean isPipelineReady() {
        return NumenCompat.isInstalled() && isInstalled();
    }
}

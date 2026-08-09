package com.github.xiaozhaoz1.littlemaidmoreaction.compat;

/**
 * YSM (Yes Steve Model) 兼容 (v75.1) — 存在性检测。
 *
 * <p>v75 石板化管线双门控 (用户要求): 假人 = 玩家实体 → 玩家渲染 + YSM 模型
 * (假人自动继承女仆模型靠 `/yes_steve_model model set` 指令)。YSM 未装 → 假人只能
 * 显示玩家皮肤 — 变身功能应禁用并提示。modId = "yes_steve_model" (neoforge.mods.toml 实证)。
 */
public final class YsmCompat {

    private static volatile Boolean INSTALLED;

    private YsmCompat() {}

    /** YSM mod 是否安装 (modId = "yes_steve_model", 惰性缓存) */
    public static boolean isInstalled() {
        Boolean cached = INSTALLED;
        if (cached != null) return cached;
//? if 1.20.1 {
        boolean loaded = net.minecraftforge.fml.ModList.get().isLoaded("yes_steve_model");
//?} else {
        boolean loaded = net.neoforged.fml.ModList.get().isLoaded("yes_steve_model")
                || net.neoforged.fml.ModList.get().isLoaded("openysm");   // v75.3: OpenYSM 开源版
//?}
        INSTALLED = loaded;
        return loaded;
    }

    /** v75.3: OpenYSM 开源版 (org.openysm API — 假人模型直接设置必需; 混淆版无此 API) */
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

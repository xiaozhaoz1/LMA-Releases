package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.client.gui.screens.Screen;

/**
 * 屏生命周期日志 (v79.63) — 「界面打不开」类问题的**取证点**。
 *
 * <p><b>为什么需要</b>: 界面打不开有三种静默失败形态, 光看现象分不清 —
 * ① 打开请求根本没到 (入口/按键/包) ② 到了但 opener 未注册/工厂返回 null (无反应)
 * ③ 屏构造成功但渲染崩 (客户端栈)。本类把 ①② 变成**日志可见**, ③ 由客户端栈暴露。
 *
 * <p><b>级别</b>: 正常路径 debug (默认不刷屏); 失败路径 **warn** (打不开时必须留痕)。
 *
 * <p><b>兼容层门控契约</b> (用户裁定 2026-09-11): 兼容屏的日志点必须**先过模块门控** —
 * 未启用该模块/未加载该 mod 时**直接 return**, 不执行任何逻辑 (见 {@link #compatConstructed})。
 * 这样别人在没有该 mod 的环境测试时, 这段代码永不执行。
 */
public final class ScreenLifecycleLog {

    private ScreenLifecycleLog() {}

    /** 打开请求到达入口 */
    public static void requested(String key) {
        LittleMaidMoreAction.LOGGER.debug("[Screen] open 请求 key={}", key);
    }

    /** 打开失败: 未注册 opener 或工厂返回 null → 用户视角 = 点了没反应 (必须 warn) */
    public static void openFailed(String key) {
        LittleMaidMoreAction.LOGGER.warn("[Screen] 打开失败: key={} — 未注册 opener 或工厂返回 null (用户视角=无反应)", key);
    }

    /** 屏构造/打开完成 (到这一步屏会显示; 若仍空白 → 看渲染栈) */
    public static void constructed(String key, Screen screen) {
        LittleMaidMoreAction.LOGGER.debug("[Screen] 已打开 key={} screen={}", key,
                screen == null ? "null" : screen.getClass().getSimpleName());
    }

    /**
     * 兼容层屏的构造日志 — **门控优先**: 模块未启用则直接返回, 不产生任何副作用。
     *
     * @param moduleEnabled 由兼容层调用方传入 (`CompatToggle.isModuleEnabled(modId) && ModList.isLoaded(modId)`),
     *                      避免本类反向依赖 compat (分层: screen 不 import compat)
     */
    public static void compatConstructed(String screenName, boolean moduleEnabled) {
        if (!moduleEnabled) return;   // 无该 mod / 模块关闭 → 不执行 (用户裁定: 别人测试时不执行)
        LittleMaidMoreAction.LOGGER.debug("[Screen] 兼容屏构造 screen={} (模块已启用)", screenName);
    }
}

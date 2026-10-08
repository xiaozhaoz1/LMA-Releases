package com.github.xiaozhaoz1.littlemaidmoreaction.api.input;

/**
 * {@link StickBindProvider} 的静态访问点 (facade; 2026-09-21 接口倒置).
 *
 * <p><b>未注入 ⇒ 抛 {@code IllegalStateException}</b> (用户裁定: fail-fast, **不静默降级**)。
 * 理由: 3 个方法都是"读配置值 / 纯函数换算", 一旦缺失就说明**注册链断了**(真缺陷) ——
 * 静默返回 null 会表现为"右键没反应/绑定失效"这类难查症状, 不如直接暴露。
 *
 * <p><b>时序</b>: 注入在 mod 构造期 ({@code init.LmaRegistrar.init()}, 双平台入口都调) ;
 * 消费在事件/运行时 (右键女仆/方块入口) ⇒ 正常链路必已注入 ✓。
 * 且本 provider 不碰 {@code DeferredRegister}/holder (纯值+纯函数) ⇒ **无注册时序约束**
 * (与 {@code NavigationMemoryProvider} 需延迟取值器的情况不同)。
 *
 * <p>同包成对: {@link StickBindProvider} (接口) + 本类 (访问点) —— 见 {@code api/input/}。
 */
public final class StickBindAccess {

    /** 实现 — 启动期写入 (见 {@link #install}); 运行期只读 */
    private static volatile StickBindProvider impl;

    private StickBindAccess() {}

    /** 启动期注入 (幂等; null 忽略, 防误调用清空) */
    public static void install(StickBindProvider provider) {
        if (provider != null) impl = provider;
    }

    /** 标记物品 id (未注入 ⇒ ISE) */
    public static String markItemId() {
        return provider().markItemId();
    }

    /** 绑定物品 id (未注入 ⇒ ISE) */
    public static String bindItemId() {
        return provider().bindItemId();
    }

    /** 任务类型 (未注入 ⇒ ISE) */
    public static String taskTypeOf(String uidPath) {
        return provider().taskTypeOf(uidPath);
    }

    private static StickBindProvider provider() {
        StickBindProvider p = impl;
        if (p == null) {
            throw new IllegalStateException("StickBindAccess 未注入 — init/LmaRegistrar.init() 必须在 mod 构造期调 install");
        }
        return p;
    }
}

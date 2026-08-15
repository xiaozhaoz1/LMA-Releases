package com.github.xiaozhaoz1.littlemaidmoreaction.api;

/**
 * 原版兼容层共享常量。
 * 所有 execute/ 类中散落的魔法数字集中至此。
 */
public final class VanillaConstants {
    private VanillaConstants() {}

    // === 搜索 ===
    public static final int SEARCH_VERTICAL = 4;

    // === 合成 ===
    public static final int RECIPE_MAX_DEPTH = 10;
    public static final int CRAFT_BATCH_SIZE = 1;

    // === 唱片机 ===
    public static final int JUKEBOX_PLAY_TICKS = 6000;   // 5 分钟
    public static final int JUKEBOX_PICKUP_TICKS = 20;    // 1 秒

    // === 熔炉 ===
    public static final int FURNACE_INPUT_LIMIT = 8;
    public static final int FURNACE_FUEL_LIMIT = 64;

    // === 导航 ===
    public static final int NAV_CHECK_INTERVAL = 100;     // Brain 行为检查间隔 (tick)
    // (NAV_TIMEOUT_TICKS 已删 — v79.26.6 配置化 CHAIN_NAV_TIMEOUT 后唯一使用方
    //  AbstractFunctionalBlockInteraction 已清理, v79.49)
    public static final double ARRIVE_DIST_SQR = 9.0;     // 3 格球 — 通用到达/挖穿水平门 (WorkStation/
                                                          // LmaFlowCoordinationBehavior/digUp; 挖矿破块
                                                          // 门已分家 MINE_DIG_DIST_SQR — v79.58)
    public static final double ONE_AWAY_DIST_SQR = 3.0;   // 1 格邻域 (3x3x3 相邻格) — 寻路到达判定
                                                          // (用户: "至少要走到矿旁边" — 回归早期行为)
    public static final double MINE_DIG_DIST_SQR = 16.0;  // 4 格球 — 挖矿可破块门 (v79.58 用户裁定
                                                          // "可挖掘距离调成 4, 完全覆盖" — TLM
                                                          // destroyBlock 无距离限制实测 (EntityMaid
                                                          // destroyBlock 直破), 3 格边界抖动白蓄力;
                                                          // 仅挖矿用, 不连带 WorkStation 等)
    public static final double DIG_DIRECT_DIST_SQR = 16.0; // 4 格球 — 扫描目标直接开脉 (v79.58 跟随
                                                           // 破块门 — 4 格内直接挖, 不走寻路)

    // === 任务 ===
    public static final int TASK_DEFAULT_TIMEOUT = 1200;  // 60 秒
}

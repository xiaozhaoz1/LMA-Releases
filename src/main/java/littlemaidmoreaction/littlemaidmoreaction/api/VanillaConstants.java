package littlemaidmoreaction.littlemaidmoreaction.api;

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
    public static final int NAV_TIMEOUT_TICKS = 600;      // 30 秒
    public static final double ARRIVE_DIST_SQR = 9.0;     // 3 格

    // === 任务 ===
    public static final int TASK_DEFAULT_TIMEOUT = 1200;  // 60 秒
}

package littlemaidmoreaction.littlemaidmoreaction.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组 Forge 配置文件。
 *
 * 战斗参数已迁移至规则引擎 JSON 预设（RuleActionStorage.createDefaultRules），
 * 此处仅保留规则引擎总开关和调试模式。
 */
public final class MoreActionConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CUSTOM_RULES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    // ── 连锁采集 (v36) ──
    public static final ForgeConfigSpec.IntValue CHAIN_MAX_BLOCKS;
    public static final ForgeConfigSpec.IntValue CHAIN_BREAK_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue CHAIN_WOOD_NATURE_CHECK;
    public static final ForgeConfigSpec.IntValue CHAIN_NO_AXE_MULTIPLIER;

    // ── 环境感知 (v37) ──
    public static final ForgeConfigSpec.IntValue ENV_SCAN_INTERVAL;
    public static final ForgeConfigSpec.IntValue ENV_DEFAULT_RADIUS;
    public static final ForgeConfigSpec.IntValue ENV_MAX_HITS;
    // ── 环境感知阈值 (v37.1, 默认对齐 TLM) ──
    public static final ForgeConfigSpec.DoubleValue ENV_COLD_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue ENV_HOT_THRESHOLD;
    // ── 环境感知扩展 (v37.2) ──
    public static final ForgeConfigSpec.IntValue ENV_PLAYER_GATE_RADIUS;
    public static final ForgeConfigSpec.IntValue ENV_DARKNESS_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue ENV_STRUCTURE_ENABLED;
    public static final ForgeConfigSpec.IntValue ENV_STRUCTURE_INTERVAL;
    public static final ForgeConfigSpec.IntValue ENV_STRUCTURE_RADIUS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("custom_rules");
        CUSTOM_RULES_ENABLED = b
                .comment("规则引擎总开关。关闭后所有预设及自定义规则均不触发")
                .define("enabled", true);
        b.pop();

        b.push("debug");
        DEBUG_MODE = b
                .comment("调试模式：日志 + 聊天栏输出")
                .define("debug_mode", false);
        b.pop();

        b.push("chain_harvest");
        CHAIN_MAX_BLOCKS = b
                .comment("连锁采集(砍树/挖矿)单次最大方块数")
                .defineInRange("max_blocks", 64, 1, 1024);
        CHAIN_BREAK_INTERVAL = b
                .comment("每破坏一块的间隔 (tick)")
                .defineInRange("break_interval_ticks", 5, 1, 100);
        CHAIN_WOOD_NATURE_CHECK = b
                .comment("砍树前校验天然树(原木需连接非手放树叶)，防止女仆拆玩家木建筑")
                .define("wood_nature_check", true);
        CHAIN_NO_AXE_MULTIPLIER = b
                .comment("v36.1 无斧砍树减速倍率 (破坏间隔 × 此值)")
                .defineInRange("no_axe_interval_multiplier", 4, 1, 10);
        b.pop();

        b.push("env_sense");
        ENV_SCAN_INTERVAL = b
                .comment("环境感知扫描间隔 (tick)，默认 200 = 10秒")
                .defineInRange("scan_interval_ticks", 200, 20, 1200);
        ENV_DEFAULT_RADIUS = b
                .comment("无工作范围时的默认扫描半径")
                .defineInRange("default_radius", 16, 4, 64);
        ENV_MAX_HITS = b
                .comment("每感知器命中结果上限")
                .defineInRange("max_hits_per_sensor", 32, 1, 256);
        ENV_COLD_THRESHOLD = b
                .comment("太冷判定阈值 (女仆位置温度低于此值触发 env_too_cold, TLM COLD 档默认 0.15)")
                .defineInRange("cold_threshold", 0.15, -1.0, 2.0);
        ENV_HOT_THRESHOLD = b
                .comment("太热判定阈值 (女仆位置温度高于此值触发 env_too_hot, TLM 判热默认 1.0)")
                .defineInRange("hot_threshold", 1.0, 0.0, 2.0);
        ENV_PLAYER_GATE_RADIUS = b
                .comment("玩家门控半径: 仅此范围内的女仆参与环境感知, 0=不门控 (v37.2)")
                .defineInRange("player_gate_radius", 20, 0, 256);
        ENV_DARKNESS_THRESHOLD = b
                .comment("黑暗判定亮度阈值 (低于此值触发 env_darkness, 怪物生成亮度默认 7)")
                .defineInRange("darkness_threshold", 7, 0, 15);
        ENV_STRUCTURE_ENABLED = b
                .comment("结构探测总开关 (村庄/矿井/前哨站, findNearestMapStructure 较慢)")
                .define("structure_enabled", true);
        ENV_STRUCTURE_INTERVAL = b
                .comment("结构探测间隔 (tick), 默认 24000 = 1 MC 天")
                .defineInRange("structure_interval_ticks", 24000, 1200, 168000);
        ENV_STRUCTURE_RADIUS = b
                .comment("结构探测半径 (区块), 越大越慢")
                .defineInRange("structure_radius_chunks", 8, 1, 32);
        b.pop();

        SPEC = b.build();
    }
}

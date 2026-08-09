package com.github.xiaozhaoz1.littlemaidmoreaction.config;

//? if 1.20.1 {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
import net.neoforged.neoforge.common.ModConfigSpec;
//?}

import java.util.HashMap;
import java.util.Map;

/**
 * 被动任务配置段 (v67.7) — {@code config/littlemaidmoreaction/passive.toml}。
 *
 * <p>覆盖环境感知 (EnvSense) 11 项 — 感知器扫描/阈值/结构探测/总开关。
 * 保存统一走 {@link MoreActionConfig#saveAll()} (三段 Spec 唯一落盘入口)。
 */
public final class PassiveTaskConfig {
    /** 被动段 Spec (config/littlemaidmoreaction/passive.toml) */
//? if 1.20.1 {
    public static final ForgeConfigSpec PASSIVE_SPEC;
//?} else {
    public static final ModConfigSpec PASSIVE_SPEC;
//?}

    // ── 环境感知 (v37) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue ENV_SCAN_INTERVAL;
//?} else {
    public static final ModConfigSpec.IntValue ENV_SCAN_INTERVAL;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue ENV_DEFAULT_RADIUS;
//?} else {
    public static final ModConfigSpec.IntValue ENV_DEFAULT_RADIUS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue ENV_MAX_HITS;
//?} else {
    public static final ModConfigSpec.IntValue ENV_MAX_HITS;
//?}
    // ── 环境感知阈值 (v37.1, 默认对齐 TLM) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue ENV_COLD_THRESHOLD;
//?} else {
    public static final ModConfigSpec.DoubleValue ENV_COLD_THRESHOLD;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue ENV_HOT_THRESHOLD;
//?} else {
    public static final ModConfigSpec.DoubleValue ENV_HOT_THRESHOLD;
//?}
    // ── 环境感知扩展 (v37.2) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue ENV_PLAYER_GATE_RADIUS;
//?} else {
    public static final ModConfigSpec.IntValue ENV_PLAYER_GATE_RADIUS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue ENV_DARKNESS_THRESHOLD;
//?} else {
    public static final ModConfigSpec.IntValue ENV_DARKNESS_THRESHOLD;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue ENV_STRUCTURE_ENABLED;
//?} else {
    public static final ModConfigSpec.BooleanValue ENV_STRUCTURE_ENABLED;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue ENV_STRUCTURE_INTERVAL;
//?} else {
    public static final ModConfigSpec.IntValue ENV_STRUCTURE_INTERVAL;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue ENV_STRUCTURE_RADIUS;
//?} else {
    public static final ModConfigSpec.IntValue ENV_STRUCTURE_RADIUS;
//?}
    // ── v77.4: 假人随机台词/语音节拍 (LMA 独立配置, 不碰 TLM 全局 EmojiCheckRate) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue COMPANION_CHAT_RATE;
//?} else {
    public static final ModConfigSpec.IntValue COMPANION_CHAT_RATE;
//?}
    // ── v77.9: 假人随机台词气泡/语音总开关 ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue COMPANION_CHAT_ENABLED;
//?} else {
    public static final ModConfigSpec.BooleanValue COMPANION_CHAT_ENABLED;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue COMPANION_VOICE_ENABLED;
//?} else {
    public static final ModConfigSpec.BooleanValue COMPANION_VOICE_ENABLED;
//?}

    // ── v63: 全局总开关 ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue ENVSENSE_ENABLED;
//?} else {
    public static final ModConfigSpec.BooleanValue ENVSENSE_ENABLED;
//?}
    // ── v79: 被动 tick 预算 ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue PASSIVE_TICK_BUDGET;
//?} else {
    public static final ModConfigSpec.IntValue PASSIVE_TICK_BUDGET;
//?}
    // ── v79.9: 哈气任务 ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue HAQI_ENABLED;
//?} else {
    public static final ModConfigSpec.BooleanValue HAQI_ENABLED;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue HAQI_CHANCE;
//?} else {
    public static final ModConfigSpec.DoubleValue HAQI_CHANCE;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue HAQI_DURATION_TICKS;
//?} else {
    public static final ModConfigSpec.IntValue HAQI_DURATION_TICKS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue HAQI_VOLUME;
//?} else {
    public static final ModConfigSpec.DoubleValue HAQI_VOLUME;
//?}
    // ── v79.17: 哈气挥击 ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue HAQI_HIT_CHANCE;
//?} else {
    public static final ModConfigSpec.DoubleValue HAQI_HIT_CHANCE;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue HAQI_HIT_DAMAGE;
//?} else {
    public static final ModConfigSpec.DoubleValue HAQI_HIT_DAMAGE;
//?}

    // ── v79.20: 哈气对主人变体 (独立二级开关 + 独立 5 项配置; 总开关控整个管道) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue HAQI_ENABLED_TO_OWNER;
//?} else {
    public static final ModConfigSpec.BooleanValue HAQI_ENABLED_TO_OWNER;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue HAQI_CHANCE_TO_OWNER;
//?} else {
    public static final ModConfigSpec.DoubleValue HAQI_CHANCE_TO_OWNER;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue HAQI_DURATION_TICKS_TO_OWNER;
//?} else {
    public static final ModConfigSpec.IntValue HAQI_DURATION_TICKS_TO_OWNER;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue HAQI_VOLUME_TO_OWNER;
//?} else {
    public static final ModConfigSpec.DoubleValue HAQI_VOLUME_TO_OWNER;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue HAQI_HIT_CHANCE_TO_OWNER;
//?} else {
    public static final ModConfigSpec.DoubleValue HAQI_HIT_CHANCE_TO_OWNER;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue HAQI_HIT_DAMAGE_TO_OWNER;
//?} else {
    public static final ModConfigSpec.DoubleValue HAQI_HIT_DAMAGE_TO_OWNER;
//?}

    /** v67.11: 本段 ConfigValue 句柄注册表 (path → value, 前缀 "passive.") — 配置同步用 */
//? if 1.20.1 {
    public static final Map<String, ForgeConfigSpec.ConfigValue<?>> PASSIVE_VALUES = new HashMap<>();
//?} else {
    public static final Map<String, ModConfigSpec.ConfigValue<?>> PASSIVE_VALUES = new HashMap<>();
//?}

    static {
//? if 1.20.1 {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
//?} else {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
//?}

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
        ENVSENSE_ENABLED = b
                .comment("环境感知总开关: false=女仆不接收任何环境信号 (v63)")
                .define("enabled", false);
        b.pop();

        b.push("tick_budget");
        PASSIVE_TICK_BUDGET = b
                .comment("每女仆每 tick 最多执行的被动管线数, 0=不限; 超预算时环形轮转 (v79)")
                .defineInRange("passive_tick_budget", 2, 0, 16);
        b.pop();

        // ── v79.9: 哈气任务 ──
        b.push("haqi");
        HAQI_ENABLED = b
                .comment("哈气任务总开关 (默认关闭; 开启后女仆靠近其他女仆时概率触发)")
                .define("enabled", false);
        HAQI_CHANCE = b
                .comment("哈气触发概率 (0.0-1.0, 默认 0.1 = 10%)")
                .defineInRange("chance", 0.1, 0.0, 1.0);
        HAQI_DURATION_TICKS = b
                .comment("哈气基础看着时长 (tick, 默认 60 = 3 秒; 总时长 = 基础 + 音频时长)")
                .defineInRange("duration_ticks", 60, 20, 1200);
        HAQI_VOLUME = b
                .comment("哈气音频音量 (0.0-2.0, 默认 1.0)")
                .defineInRange("volume", 1.0, 0.0, 2.0);
        HAQI_HIT_CHANCE = b
                .comment("哈气挥击概率 (0.0-1.0, 默认 0.3 = 30%; LOOK 期间概率挥击目标一下)")
                .defineInRange("hit_chance", 0.3, 0.0, 1.0);
        HAQI_HIT_DAMAGE = b
                .comment("哈气挥击伤害 (默认 1.0 = 一点血; 真实攻击链但不致命)")
                .defineInRange("hit_damage", 1.0, 0.0, 100.0);
        // ── v79.20: 哈气对主人变体 (独立二级开关; 需总开关开启, 旁边无女仆且主人在 2 格内时概率触发) ──
        HAQI_ENABLED_TO_OWNER = b
                .comment("哈气对主人变体开关 (默认关闭; 对女仆哈气总开关之上再加开, 只控制对主人哈气)")
                .define("enabled_to_owner", false);
        HAQI_CHANCE_TO_OWNER = b
                .comment("对主人哈气触发概率 (0.0-1.0, 默认 0.1 = 10%; 旁边无女仆时对 2 格内主人掷骰)")
                .defineInRange("chance_to_owner", 0.1, 0.0, 1.0);
        HAQI_DURATION_TICKS_TO_OWNER = b
                .comment("对主人哈气看着时长 (tick, 默认 60 = 3 秒; 客户端语音文件实际时长服务端不可知, 总时长=此值)")
                .defineInRange("duration_ticks_to_owner", 60, 20, 1200);
        HAQI_VOLUME_TO_OWNER = b
                .comment("对主人哈气音频音量 (0.0-2.0, 默认 1.0; littlemaid_peco 声音包 idle 子集随机)")
                .defineInRange("volume_to_owner", 1.0, 0.0, 2.0);
        HAQI_HIT_CHANCE_TO_OWNER = b
                .comment("对主人哈气挥击概率 (0.0-1.0, 默认 0.3 = 30%; LOOK 期间概率拍主人一下)")
                .defineInRange("hit_chance_to_owner", 0.3, 0.0, 1.0);
        HAQI_HIT_DAMAGE_TO_OWNER = b
                .comment("对主人哈气挥击伤害 (默认 1.0 = 一点血; 主人不反击)")
                .defineInRange("hit_damage_to_owner", 1.0, 0.0, 100.0);
        b.pop();

        b.push("companion");
        COMPANION_CHAT_ENABLED = b
                .comment("假人随机台词气泡总开关 (默认 true)")
                .define("chat_enabled", true);
        COMPANION_VOICE_ENABLED = b
                .comment("假人随机语音总开关 (需女仆配 TLM 语音包, 默认 true)")
                .define("voice_enabled", true);
        COMPANION_CHAT_RATE = b
                .comment("假人随机台词/语音节拍 (tick), 默认 1200=60s; 最小 20=1s (v77.4 调测用)")
                .defineInRange("chat_rate_ticks", 1200, 20, 24000);
        b.pop();

        PASSIVE_SPEC = b.build();
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_SCAN_INTERVAL);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_DEFAULT_RADIUS);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_MAX_HITS);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_COLD_THRESHOLD);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_HOT_THRESHOLD);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_PLAYER_GATE_RADIUS);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_DARKNESS_THRESHOLD);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_STRUCTURE_ENABLED);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_STRUCTURE_INTERVAL);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", COMPANION_CHAT_ENABLED);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", COMPANION_VOICE_ENABLED);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", COMPANION_CHAT_RATE);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENV_STRUCTURE_RADIUS);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", ENVSENSE_ENABLED);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", PASSIVE_TICK_BUDGET);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_ENABLED);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_CHANCE);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_DURATION_TICKS);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_VOLUME);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_HIT_CHANCE);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_HIT_DAMAGE);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_ENABLED_TO_OWNER);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_CHANCE_TO_OWNER);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_DURATION_TICKS_TO_OWNER);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_VOLUME_TO_OWNER);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_HIT_CHANCE_TO_OWNER);
        MoreActionConfig.reg(PASSIVE_VALUES, "passive", HAQI_HIT_DAMAGE_TO_OWNER);
    }

    private PassiveTaskConfig() {}
}

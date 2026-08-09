package com.github.xiaozhaoz1.littlemaidmoreaction.config;

//? if 1.20.1 {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
import net.neoforged.neoforge.common.ModConfigSpec;
//?}

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主动任务配置段 (v67.7) — {@code config/littlemaidmoreaction/active.toml}。
 *
 * <p>覆盖 8 个主动任务: 连锁采集(砍树/挖矿) / 合成 / 熔炉 / 唱片机 / 搬运 / 敲钟 / 右键交互。
 * 保存统一走 {@link MoreActionConfig#saveAll()} (三段 Spec 唯一落盘入口)。
 */
public final class ActiveTaskConfig {
    /** 主动任务段 Spec (config/littlemaidmoreaction/active.toml) */
//? if 1.20.1 {
    public static final ForgeConfigSpec ACTIVE_SPEC;
//?} else {
    public static final ModConfigSpec ACTIVE_SPEC;
//?}

    // ── 连锁采集 (v36) ──
    // v36.2: 破坏间隔改为按挖掘等级查表 (ToolJudge.harvestIntervalTicks)，不再配置
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue CHAIN_MAX_BLOCKS;
//?} else {
    public static final ModConfigSpec.IntValue CHAIN_MAX_BLOCKS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue CHAIN_WOOD_NATURE_CHECK;
//?} else {
    public static final ModConfigSpec.BooleanValue CHAIN_WOOD_NATURE_CHECK;
//?}
    // v67.4: 硬编码常量 → 配置
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue CHAIN_SCAN_INTERVAL;
//?} else {
    public static final ModConfigSpec.IntValue CHAIN_SCAN_INTERVAL;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue CHAIN_MAX_DISTANCE;
//?} else {
    public static final ModConfigSpec.IntValue CHAIN_MAX_DISTANCE;
//?}
    // v79.26.6: 挖矿兜底行为参数配置化 — 垂直挖穿/导航看门狗
    // (原 ChainHarvestExecute 行内魔法数 + 类内常量);
    // v79.26.8e: 垫柱触发 (CHAIN_PILLAR_TRIGGER)/面前挖穿 (CHAIN_FRONT_DIG_DIST) 退役 —
    // 垫柱链删 (用户裁定), 面前挖穿删, 桥/阶梯由 BridgeCoordinator 固定逻辑无配置
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue CHAIN_DIG_DOWN_DEPTH;
//?} else {
    public static final ModConfigSpec.IntValue CHAIN_DIG_DOWN_DEPTH;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue CHAIN_NAV_TIMEOUT;
//?} else {
    public static final ModConfigSpec.IntValue CHAIN_NAV_TIMEOUT;
//?}
    // v79.26.8d: 卡方块自救 (maid_useful_task MaidSelfRescueBehavior 移植)
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue CHAIN_SELF_RESCUE;
//?} else {
    public static final ModConfigSpec.BooleanValue CHAIN_SELF_RESCUE;
//?}
    // v79.26.7: CHAIN_SKIP_TTL 退役 — 跳过集刷新分档死值 (TLM 60t / 激进 1s, 用户裁定), 见 ChainHarvestExecute

    // ── 右键交互 (v67.2) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<String> BI_MARK_ITEM;
//?} else {
    public static final ModConfigSpec.ConfigValue<String> BI_MARK_ITEM;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<String> BI_BIND_ITEM;
//?} else {
    public static final ModConfigSpec.ConfigValue<String> BI_BIND_ITEM;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue BI_INTERACT_DISTANCE;
//?} else {
    public static final ModConfigSpec.DoubleValue BI_INTERACT_DISTANCE;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue BI_TRIGGER_RANGE;
//?} else {
    public static final ModConfigSpec.DoubleValue BI_TRIGGER_RANGE;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue BI_TIMER_DEFAULT_INTERVAL;
//?} else {
    public static final ModConfigSpec.IntValue BI_TIMER_DEFAULT_INTERVAL;
//?}

    // ── 合成任务 (v67.2/v67.3) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<String> CRAFT_DEFAULT_PRODUCT;
//?} else {
    public static final ModConfigSpec.ConfigValue<String> CRAFT_DEFAULT_PRODUCT;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue CRAFT_MAX_PRODUCTS;
//?} else {
    public static final ModConfigSpec.IntValue CRAFT_MAX_PRODUCTS;
//?}

    // ── 熔炉 (v67.2/v67.3) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FURNACE_BLACKLIST;
//?} else {
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FURNACE_BLACKLIST;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FURNACE_WHITELIST;
//?} else {
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FURNACE_WHITELIST;
//?}

    // ── 唱片机 (v67.3) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue JUKEBOX_WAIT_TICKS;
//?} else {
    public static final ModConfigSpec.IntValue JUKEBOX_WAIT_TICKS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> JUKEBOX_BLACKLIST;
//?} else {
    public static final ModConfigSpec.ConfigValue<List<? extends String>> JUKEBOX_BLACKLIST;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> JUKEBOX_WHITELIST;
//?} else {
    public static final ModConfigSpec.ConfigValue<List<? extends String>> JUKEBOX_WHITELIST;
//?}

    // ── 搬运 (v67.3) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ARM_BLACKLIST;
//?} else {
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ARM_BLACKLIST;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ARM_WHITELIST;
//?} else {
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ARM_WHITELIST;
//?}

    // ── 敲钟 (v67.5) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue BELL_VOLUME;
//?} else {
    public static final ModConfigSpec.DoubleValue BELL_VOLUME;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue BELL_PITCH;
//?} else {
    public static final ModConfigSpec.DoubleValue BELL_PITCH;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue BELL_RING_INTERVAL;
//?} else {
    public static final ModConfigSpec.IntValue BELL_RING_INTERVAL;
//?}

    // ── AI 操控 (v74) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<String> AI_LLM_PROVIDER;
//?} else {
    public static final ModConfigSpec.ConfigValue<String> AI_LLM_PROVIDER;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<String> AI_VOICE;
//?} else {
    public static final ModConfigSpec.ConfigValue<String> AI_VOICE;
//?}

    // ── 连锁采集方块黑白名单 (v67.3, 方块 id) ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COLLECT_BLACKLIST;
//?} else {
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COLLECT_BLACKLIST;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COLLECT_WHITELIST;
//?} else {
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COLLECT_WHITELIST;
//?}

    /** v67.11: 本段 ConfigValue 句柄注册表 (path → value, 前缀 "active.") — 配置同步用 */
//? if 1.20.1 {
    public static final Map<String, ForgeConfigSpec.ConfigValue<?>> ACTIVE_VALUES = new HashMap<>();
//?} else {
    public static final Map<String, ModConfigSpec.ConfigValue<?>> ACTIVE_VALUES = new HashMap<>();
//?}

    static {
//? if 1.20.1 {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
//?} else {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
//?}

        b.push("chain_harvest");
        CHAIN_MAX_BLOCKS = b
                .comment("连锁采集(砍树/挖矿)单次最大方块数")
                .defineInRange("max_blocks", 64, 1, 1024);
        CHAIN_WOOD_NATURE_CHECK = b
                .comment("砍树前校验天然树(原木需连接非手放树叶)，防止女仆拆玩家木建筑")
                .define("wood_nature_check", true);
        CHAIN_SCAN_INTERVAL = b
                .comment("无目标扫描间隔 (tick), 20 = 1 秒 (v79.19m 用户: 挖矿寻路每秒刷新; 原 60 = 3 秒 → 有矿也等 3 秒)")
                .defineInRange("scan_interval_ticks", 20, 20, 1200);
        CHAIN_MAX_DISTANCE = b
                .comment("采集距离上限 (格): 连锁 BFS 搜索与整脉破坏范围 (v67.4)")
                .defineInRange("max_distance_blocks", 32, 4, 128);
        CHAIN_DIG_DOWN_DEPTH = b
                .comment("垂直挖穿深度 (格): 目标矿在脚下≤此深度时挖脚下石头逐层下, 头顶≤同深度时向上挖穿矿正下方整列 (v79.26.8e: 3→6; v79.26.8g: 上下双向; v79.19o 原硬编码 3)")
                .defineInRange("dig_down_depth", 6, 1, 8);
        CHAIN_NAV_TIMEOUT = b
                .comment("导航看门狗超时 (tick): 寻路超时未达目标则跳过重试 (v79.26.6 配置化, 原 NAV_TIMEOUT_TICKS 常量; 默认 240 = 12 秒)")
                .defineInRange("nav_timeout_ticks", 240, 40, 2400);
        CHAIN_SELF_RESCUE = b
                .comment("卡方块自救: 女仆被埋/卡住时自动瞬破窒息方块脱困 (v79.26.8d 参考 maid_useful_task)")
                .define("self_rescue", true);
        // v79.26.8e: 垫柱触发高度/面前挖穿距离配置退役 (用户: "不用垫方块了, 只要挖上下能挖到的就行了" —
        // 垫柱链全删, 面前挖穿删 — 走路全 TLM, 只挖垂直; 桥/阶梯固定逻辑无配置)
        b.pop();

        b.push("chain_harvest_filter");
        COLLECT_BLACKLIST = b
                .comment("采集黑名单: 女仆不砍/不挖的方块id列表")
                .defineListAllowEmpty(List.of("blacklist"), List.<String>of(),
                        o -> o instanceof String);
        COLLECT_WHITELIST = b
                .comment("采集白名单: 非空时只砍/只挖名单内方块")
                .defineListAllowEmpty(List.of("whitelist"), List.<String>of(),
                        o -> o instanceof String);
        b.pop();

        b.push("block_interact");
        BI_MARK_ITEM = b
                .comment("右键方块标记物品 (物品id, 如 minecraft:stick)")
                .define("mark_item", "minecraft:stick");
        BI_BIND_ITEM = b
                .comment("右键女仆绑定物品 (物品id, 如 minecraft:stick)")
                .define("bind_item", "minecraft:stick");
        BI_INTERACT_DISTANCE = b
                .comment("女仆右键交互距离 (格)")
                .defineInRange("interact_distance", 5.0, 1.0, 16.0);
        BI_TRIGGER_RANGE = b
                .comment("按键触发扫描范围 (格, 玩家周围女仆)")
                .defineInRange("trigger_range", 10.0, 5.0, 64.0);
        BI_TIMER_DEFAULT_INTERVAL = b
                .comment("定时器默认间隔 (tick, 200=10秒)")
                .defineInRange("timer_default_interval", 200, 20, 12000);
        b.pop();

        b.push("craft_chain");
        CRAFT_DEFAULT_PRODUCT = b
                .comment("默认产物: 无目标时使用该物品作为合成目标 (物品id)")
                .define("default_product", "");
        CRAFT_MAX_PRODUCTS = b
                .comment("产物数量上限: 女仆累计合成达到上限后停止, -1=无限 (per-maid 设置可覆盖)")
                .defineInRange("max_products", -1, -1, 1024);
        b.pop();

        b.push("furnace");
        FURNACE_BLACKLIST = b
                .comment("熔炉黑名单: 女仆不烧炼的物品id列表")
                .defineListAllowEmpty(List.of("blacklist"), List.<String>of(),
                        o -> o instanceof String);
        FURNACE_WHITELIST = b
                .comment("熔炉白名单: 非空时只烧炼名单内物品 (per-maid 名单可覆盖)")
                .defineListAllowEmpty(List.of("whitelist"), List.<String>of(),
                        o -> o instanceof String);
        b.pop();

        b.push("jukebox");
        JUKEBOX_WAIT_TICKS = b
                .comment("唱片播放等待时长 (tick, 6000=5分钟, 播放完才换碟)")
                .defineInRange("wait_ticks", 6000, 20, 24000);
        JUKEBOX_BLACKLIST = b
                .comment("唱片黑名单: 女仆不播放的唱片id列表")
                .defineListAllowEmpty(List.of("blacklist"), List.<String>of(),
                        o -> o instanceof String);
        JUKEBOX_WHITELIST = b
                .comment("唱片白名单: 非空时只播放名单内唱片")
                .defineListAllowEmpty(List.of("whitelist"), List.<String>of(),
                        o -> o instanceof String);
        b.pop();

        b.push("arm_transfer");
        ARM_BLACKLIST = b
                .comment("搬运黑名单: 女仆不搬运的物品id列表")
                .defineListAllowEmpty(List.of("blacklist"), List.<String>of(),
                        o -> o instanceof String);
        ARM_WHITELIST = b
                .comment("搬运白名单: 非空时只搬运名单内物品")
                .defineListAllowEmpty(List.of("whitelist"), List.<String>of(),
                        o -> o instanceof String);
        b.pop();

        b.push("bell_ring");
        BELL_VOLUME = b
                .comment("敲钟音量 (v67.5)")
                .defineInRange("volume", 1.0, 0.0, 2.0);
        BELL_PITCH = b
                .comment("敲钟音调 (1.0 = 原声, v67.5)")
                .defineInRange("pitch", 1.0, 0.5, 2.0);
        BELL_RING_INTERVAL = b
                .comment("两次敲钟最小间隔 (tick, 30 = 1.5秒, 行为层下限 30, v67.5)")
                .defineInRange("ring_interval_ticks", 30, 30, 12000);
        b.pop();

        b.push("ai_control");
        AI_LLM_PROVIDER = b
                .comment("AI 操控默认 LLM 模型名称 (Numen G 面板创建的模型条目名, 空=不绑定; per-maid 覆盖见 TLM 任务设置)")
                .define("llm_provider", "");
        AI_VOICE = b
                .comment("AI 操控默认声线名称 (Numen G 面板创建的声线条目名, 空=不绑定; per-maid 覆盖见 TLM 任务设置)")
                .define("voice", "");
        b.pop();

        ACTIVE_SPEC = b.build();
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CHAIN_MAX_BLOCKS);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CHAIN_WOOD_NATURE_CHECK);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CHAIN_SCAN_INTERVAL);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CHAIN_MAX_DISTANCE);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CHAIN_DIG_DOWN_DEPTH);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CHAIN_NAV_TIMEOUT);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CHAIN_SELF_RESCUE);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", COLLECT_BLACKLIST);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", COLLECT_WHITELIST);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", BI_MARK_ITEM);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", BI_BIND_ITEM);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", BI_INTERACT_DISTANCE);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", BI_TRIGGER_RANGE);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", BI_TIMER_DEFAULT_INTERVAL);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CRAFT_DEFAULT_PRODUCT);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", CRAFT_MAX_PRODUCTS);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", FURNACE_BLACKLIST);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", FURNACE_WHITELIST);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", JUKEBOX_WAIT_TICKS);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", JUKEBOX_BLACKLIST);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", JUKEBOX_WHITELIST);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", ARM_BLACKLIST);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", ARM_WHITELIST);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", BELL_VOLUME);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", BELL_PITCH);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", BELL_RING_INTERVAL);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", AI_LLM_PROVIDER);
        MoreActionConfig.reg(ACTIVE_VALUES, "active", AI_VOICE);
    }

    private ActiveTaskConfig() {}
}

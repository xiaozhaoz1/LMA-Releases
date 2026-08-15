package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk;

//? if 1.20.1 {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
import net.neoforged.neoforge.common.ModConfigSpec;
//?}

import java.util.HashMap;
import java.util.Map;

/**
 * 野生酒狐奶 / 酒狐奶桶配置段 (v79.6x) — {@code config/littlemaidmoreaction/kitsune_milk.toml}。
 *
 * <p>8 项: 主/副开关 + 三种效果时长 + 耐久 + 无敌时长 + CD。
 * 加好感 CD (5 分钟) 不进配置 (用户裁定) — {@code KitsuneMilkInteract} 硬编码常量。
 * 攻击伤害读哈气管线 {@code PassiveTaskConfig.HAQI_HIT_DAMAGE} (用户裁定)。
 */
public final class WildKitsuneMilkConfig {

//? if 1.20.1 {
    public static final ForgeConfigSpec KITSUNE_SPEC;
//?} else {
    public static final ModConfigSpec KITSUNE_SPEC;
//?}

    // ── 开关 ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue TOGGLE_ENABLED;
//?} else {
    public static final ModConfigSpec.BooleanValue TOGGLE_ENABLED;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue TOGGLE_WILD_EXTRA;
//?} else {
    public static final ModConfigSpec.BooleanValue TOGGLE_WILD_EXTRA;
//?}

    // ── 效果时长 ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue TAMED_RESISTANCE_TICKS;
//?} else {
    public static final ModConfigSpec.IntValue TAMED_RESISTANCE_TICKS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue TAMED_REGENERATION_TICKS;
//?} else {
    public static final ModConfigSpec.IntValue TAMED_REGENERATION_TICKS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue WILD_REGENERATION_TICKS;
//?} else {
    public static final ModConfigSpec.IntValue WILD_REGENERATION_TICKS;
//?}

    // ── 饰品 ──
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue BAUBLE_DURABILITY;
//?} else {
    public static final ModConfigSpec.IntValue BAUBLE_DURABILITY;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue WILD_INVINCIBLE_TICKS;
//?} else {
    public static final ModConfigSpec.IntValue WILD_INVINCIBLE_TICKS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue WILD_CD_TICKS;
//?} else {
    public static final ModConfigSpec.IntValue WILD_CD_TICKS;
//?}
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue WILD_MUSIC_VOLUME;
//?} else {
    public static final ModConfigSpec.DoubleValue WILD_MUSIC_VOLUME;
//?}

    /** 本段 ConfigValue 句柄注册表 (path → value, 前缀 "kitsune_milk.") */
//? if 1.20.1 {
    public static final Map<String, ForgeConfigSpec.ConfigValue<?>> KITSUNE_VALUES = new HashMap<>();
//?} else {
    public static final Map<String, ModConfigSpec.ConfigValue<?>> KITSUNE_VALUES = new HashMap<>();
//?}

    static {
//? if 1.20.1 {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
//?} else {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
//?}

        b.push("toggle");
        TOGGLE_ENABLED = b
                .comment("主开关: 空桶对已驯服(自己的)/未驯服女仆右键挤奶")
                .define("enabled", true);
        TOGGLE_WILD_EXTRA = b
                .comment("副开关: 额外开启野生酒狐奶 — 开=未驯服产野生奶, 关=未驯服也产酒狐奶桶")
                .define("wild_extra", false);
        b.pop();

        b.push("effect");
        TAMED_RESISTANCE_TICKS = b
                .comment("酒狐奶桶抗性提升 II 时长 (tick, 默认 200 = 10s)")
                .defineInRange("tamed_resistance_ticks", 200, 20, 12000);
        TAMED_REGENERATION_TICKS = b
                .comment("酒狐奶桶生命恢复 I 时长 (tick, 默认 200 = 10s)")
                .defineInRange("tamed_regeneration_ticks", 200, 20, 12000);
        WILD_REGENERATION_TICKS = b
                .comment("野生酒狐奶饮用生命恢复 I 时长 (tick, 默认 600 = 30s)")
                .defineInRange("wild_regeneration_ticks", 600, 20, 12000);
        b.pop();

        b.push("bauble");
        BAUBLE_DURABILITY = b
                .comment("酒狐奶桶饰品耐久 (受伤害触发掉 1 点, 默认 30)")
                .defineInRange("durability", 30, 1, 1000);
        WILD_INVINCIBLE_TICKS = b
                .comment("野生酒狐奶无敌时长 (tick, 默认 600 = 30s; 与音乐时长一致)")
                .defineInRange("wild_invincible_ticks", 600, 20, 12000);
        WILD_CD_TICKS = b
                .comment("野生酒狐奶无敌专属 CD (tick, 默认 12000 = 10 分钟)")
                .defineInRange("wild_cd_ticks", 12000, 100, 72000);
        WILD_MUSIC_VOLUME = b
                .comment("野生酒狐奶触发音乐音量 (0.0-2.0, 默认 0.5 = 50%)")
                .defineInRange("wild_music_volume", 0.5, 0.0, 2.0);
        b.pop();

        KITSUNE_SPEC = b.build();

        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", TOGGLE_ENABLED);
        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", TOGGLE_WILD_EXTRA);
        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", TAMED_RESISTANCE_TICKS);
        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", TAMED_REGENERATION_TICKS);
        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", WILD_REGENERATION_TICKS);
        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", BAUBLE_DURABILITY);
        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", WILD_INVINCIBLE_TICKS);
        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", WILD_CD_TICKS);
        com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig.reg(
                KITSUNE_VALUES, "kitsune_milk", WILD_MUSIC_VOLUME);
    }

    private WildKitsuneMilkConfig() {}
}

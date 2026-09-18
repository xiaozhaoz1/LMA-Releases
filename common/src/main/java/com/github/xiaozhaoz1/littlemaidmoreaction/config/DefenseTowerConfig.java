package com.github.xiaozhaoz1.littlemaidmoreaction.config;

//? if 1.20.1 {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
import net.neoforged.neoforge.common.ModConfigSpec;
//?}

/**
 * 防御塔配置段 (v79.62.3) — {@code config/littlemaidmoreaction/defense_tower.toml}。
 *
 * <p>全局默认值 (Cloth 设置屏「防御塔设置」分类可改); GUI 每塔覆盖 (塔 NBT 存自己的 radius/shootMode)。
 * 保存统一走 {@link MoreActionConfig#saveAll()} (四段 Spec 唯一落盘入口)。
 */
public final class DefenseTowerConfig {

    /** 防御塔段 Spec (config/littlemaidmoreaction/defense_tower.toml) */
//? if 1.20.1 {
    public static final ForgeConfigSpec SPEC;
//?} else {
    public static final ModConfigSpec SPEC;
//?}

    /** 全局默认伤害 (0.5 心 = 1.0; 默认 2.0 = 1 心) — GUI 每塔可覆盖 */
//? if 1.20.1 {
    public static final ForgeConfigSpec.DoubleValue DAMAGE;
//?} else {
    public static final ModConfigSpec.DoubleValue DAMAGE;
//?}

    /** 全局默认索敌半径 (格; 默认 16, 上限 64 对齐 EntityScanCache 区块覆盖) — GUI 每塔可覆盖 */
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue RADIUS;
//?} else {
    public static final ModConfigSpec.IntValue RADIUS;
//?}

    /** 射击节奏 (tick; 20 = 1 秒/发) */
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue FIRE_INTERVAL;
//?} else {
    public static final ModConfigSpec.IntValue FIRE_INTERVAL;
//?}

    static {
//? if 1.20.1 {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
//?} else {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
//?}

        b.push("defense_tower");
        DAMAGE = b
                .comment("防御塔全局默认伤害 (每发; 默认 2.0 = 1 心). GUI 每塔可覆盖")
                .defineInRange("damage", 2.0, 0.5, 100.0);
        RADIUS = b
                .comment("防御塔全局默认索敌半径 (格; 默认 16, 最大 64). GUI 每塔可覆盖")
                .defineInRange("radius", 16, 4, 64);
        FIRE_INTERVAL = b
                .comment("防御塔射击节奏 (tick; 20 = 1 秒/发)")
                .defineInRange("fire_interval", 20, 10, 200);
        b.pop();

        SPEC = b.build();
    }

    private DefenseTowerConfig() {}
}

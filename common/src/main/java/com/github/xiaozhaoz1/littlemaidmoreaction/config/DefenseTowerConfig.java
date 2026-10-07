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

    // ── 手办动作显示 (v79.65, 用户裁定: 瞄准持续 + 开火触发; 与 StatueAnimation 模组避让) ──

    /** 手办动作总开关 (关 = 与旧行为完全一致, 不写任何动画键) */
//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue ANIM_ENABLED;
//?} else {
    public static final ModConfigSpec.BooleanValue ANIM_ENABLED;
//?}

    /** 瞄准时循环播放的动画名 — 弓/弩 (默认 = TLM 原版模型实测的"拉弓"名; 空 = 不播) */
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<String> AIM_ANIM;
//?} else {
    public static final ModConfigSpec.ConfigValue<String> AIM_ANIM;
//?}

    /** 开火瞬间播放一次的动画名 — 弓/弩 (**默认空 = 只"松手"**, 与真实女仆一致; 空 = 不播) */
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<String> FIRE_ANIM;
//?} else {
    public static final ModConfigSpec.ConfigValue<String> FIRE_ANIM;
//?}

    // v79.66n (用户裁定): **御币(弹幕)攻击已删除** ⇒ 弹幕专用动画键 (AIM_ANIM_DANMAKU / FIRE_ANIM_DANMAKU) 一并移除 ✓

    /** 开火动作时长 (tick; INSTANT 单次播放窗口) */
//? if 1.20.1 {
    public static final ForgeConfigSpec.IntValue ANIM_TICKS;
//?} else {
    public static final ModConfigSpec.IntValue ANIM_TICKS;
//?}

    /** 手办是否渲染手里武器: auto(默认, 模型自带弓就不画) / always / never */
//? if 1.20.1 {
    public static final ForgeConfigSpec.ConfigValue<String> SHOW_WEAPON;
//?} else {
    public static final ModConfigSpec.ConfigValue<String> SHOW_WEAPON;
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
        // ── 手办动作显示 (瞄准持续 + 开火触发; 默认名 = TLM/YSM 同名的 ISS 约定) ──
        ANIM_ENABLED = b
                .comment("手办动作显示总开关 (瞄准循环 + 开火单次). 关 = 与旧行为一致")
                .define("anim_enabled", true);
        AIM_ANIM = b
                .comment("弓/弩 瞄准时循环播放的动画名 (默认 use_mainhand:bow = TLM 原版模型的拉弓; 空 = 不播)."
                        + " 需模型实现该动画; 无此名字典的模型请改 iss:charge_arrow (施法蓄力)")
                .define("aim_anim", "use_mainhand:bow");
        FIRE_ANIM = b
                .comment("弓/弩 开火瞬间播放一次的动画名. **默认空 = 只松手** (拉弓状态结束 = 真实女仆的放箭表现) ✓;"
                        + " 若模型有专用释放动作可填 (实测 wine_fox/05 的 swing:bow 依赖模型变量 v.qh,"
                        + " 手办无该变量时会退化成空挥手 ✗ — 换其它名字前建议先实机确认)")
                .define("fire_anim", "");
        ANIM_TICKS = b
                .comment("开火动作时长 (tick; 单次播放窗口)")
                .defineInRange("anim_ticks", 20, 5, 200);
        SHOW_WEAPON = b
                .comment("手办是否渲染手里的武器: auto(默认) = 模型自带弓就不画(如圣女酒狐), 否则画;"
                        + " always = 总是画; never = 从不画")
                .define("show_weapon", "auto");
        b.pop();

        SPEC = b.build();
    }

    private DefenseTowerConfig() {}
}

package com.github.xiaozhaoz1.littlemaidmoreaction.config;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;

//? if 1.20.1 {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
import net.neoforged.neoforge.common.ModConfigSpec;
//?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模组 Forge 配置 — 通用段入口 (v67.7 拆 3 类; v72 Phase 5: 规则引擎退役, 总开关已删)。
 *
 * <p>本类保留调试模式 (common.toml) + 三段 Spec 统一保存入口。
 *
 * <p>三段配置 (v67.6 拆文件, v67.7 拆类):
 * <ul>
 *   <li>{@link MoreActionConfig} — 通用: 调试 → {@code littlemaidmoreaction-common.toml}</li>
 *   <li>{@link ActiveTaskConfig} — 主动任务 23 项 → {@code littlemaidmoreaction/active.toml}</li>
 *   <li>{@link PassiveTaskConfig} — 环境感知 11 项 → {@code littlemaidmoreaction/passive.toml}</li>
 * </ul>
 */
public final class MoreActionConfig {
    /** 通用段 Spec (config/littlemaidmoreaction-common.toml) */
//? if 1.20.1 {
    public static final ForgeConfigSpec SPEC;
//?} else {
    public static final ModConfigSpec SPEC;
//?}

//? if 1.20.1 {
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;
//?} else {
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;
//?}

    /** 通用段 ConfigValue 句柄注册表 (path → value, 前缀 "common.") — 配置同步用 */
//? if 1.20.1 {
    public static final Map<String, ForgeConfigSpec.ConfigValue<?>> COMMON_VALUES = new HashMap<>();
//?} else {
    public static final Map<String, ModConfigSpec.ConfigValue<?>> COMMON_VALUES = new HashMap<>();
//?}

    static {
//? if 1.20.1 {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
//?} else {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
//?}

        b.push("debug");
        DEBUG_MODE = b
                .comment("调试模式：日志 + 聊天栏输出")
                .define("debug_mode", false);
        b.pop();

        SPEC = b.build();
        reg(COMMON_VALUES, "common", DEBUG_MODE);
    }

    private MoreActionConfig() {}

    /** 三段 Spec 统一落盘 — Cloth 屏/编辑器保存的唯一入口 */
    public static void saveAll() {
        SPEC.save();
        ActiveTaskConfig.ACTIVE_SPEC.save();
        PassiveTaskConfig.PASSIVE_SPEC.save();
        com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.KITSUNE_SPEC.save();
    }

    // ── 配置同步 (专用服务器) — ConfigValue 句柄注册表 ──

//? if 1.20.1 {
    public static <T> void reg(Map<String, ForgeConfigSpec.ConfigValue<?>> map, String prefix,
//?} else {
    public static <T> void reg(Map<String, ModConfigSpec.ConfigValue<?>> map, String prefix,
//?}
//? if 1.20.1 {
                                ForgeConfigSpec.ConfigValue<T> value) {
//?} else {
                                ModConfigSpec.ConfigValue<T> value) {
//?}
        map.put(prefix + "." + String.join(".", value.getPath()), value);
    }

    /** 全量句柄 (common + active + passive) */
//? if 1.20.1 {
    public static Map<String, ForgeConfigSpec.ConfigValue<?>> allValues() {
//?} else {
    public static Map<String, ModConfigSpec.ConfigValue<?>> allValues() {
//?}
//? if 1.20.1 {
        Map<String, ForgeConfigSpec.ConfigValue<?>> all = new HashMap<>(COMMON_VALUES);
//?} else {
        Map<String, ModConfigSpec.ConfigValue<?>> all = new HashMap<>(COMMON_VALUES);
//?}
        all.putAll(ActiveTaskConfig.ACTIVE_VALUES);
        all.putAll(PassiveTaskConfig.PASSIVE_VALUES);
        all.putAll(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.KITSUNE_VALUES);
        return all;
    }

    /** 配置值条目 (path + 原始值) — 同步快照单元 */
    public record ConfigValueEntry(String path, Object value) {}

    /** 全量快照 (35 项当前值) */
    public static List<ConfigValueEntry> snapshot() {
        List<ConfigValueEntry> out = new ArrayList<>();
        allValues().forEach((path, cv) -> out.add(new ConfigValueEntry(path, cv.get())));
        return out;
    }

    /** 应用快照 (仅值, 不落盘 — 服务端落盘由调用方 saveAll) */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void applySnapshot(List<ConfigValueEntry> entries) {
//? if 1.20.1 {
        Map<String, ForgeConfigSpec.ConfigValue<?>> all = allValues();
//?} else {
        Map<String, ModConfigSpec.ConfigValue<?>> all = allValues();
//?}
        for (ConfigValueEntry e : entries) {
//? if 1.20.1 {
            ForgeConfigSpec.ConfigValue<?> cv = all.get(e.path());
//?} else {
            ModConfigSpec.ConfigValue<?> cv = all.get(e.path());
//?}
            if (cv == null) {
                LittleMaidMoreAction.LOGGER.warn("[LMA] 配置同步跳过未知路径: {}", e.path());
                continue;
            }
            Object current = cv.get();
            if (e.value() != null && current != null && !current.getClass().equals(e.value().getClass())) {
                LittleMaidMoreAction.LOGGER.warn("[LMA] 配置同步类型不匹配: {} ({} vs {})",
                        e.path(), current.getClass().getSimpleName(), e.value().getClass().getSimpleName());
                continue;
            }
//? if 1.20.1 {
            ((ForgeConfigSpec.ConfigValue) cv).set(e.value());
//?} else {
            ((ModConfigSpec.ConfigValue) cv).set(e.value());
//?}
        }
    }
}

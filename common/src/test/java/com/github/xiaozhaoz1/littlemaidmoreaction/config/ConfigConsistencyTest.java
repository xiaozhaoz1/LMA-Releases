package com.github.xiaozhaoz1.littlemaidmoreaction.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 配置三向一致守卫 (2026-08-11c 批次 C) — define ↔ ACTIVE_VALUES/PASSIVE_VALUES 双向一致。
 *
 * <p>规则: 每新增一个 define 字段 (ForgeConfigSpec.ConfigValue 族) 必须同步
 * {@link MoreActionConfig#reg} 注册进 VALUES map, 否则配置同步 (专用服务器) 与
 * Cloth GUI 快照会漏项。断言 = 反射数 define 字段数 vs VALUES.size():
 * 新增 define 忘 reg → 字段数 > 注册数, 测试红; reg 多余 → 编译期引用死字段必错。
 *
 * <p>纯 JVM 安全性: 配置类 clinit 仅实例化 ForgeConfigSpec.Builder (Forge 库类,
 * 零 MC 注册表依赖) — 质量门已实证 MoreActionConfig clinit 纯 JVM 安全
 * (仅 LogManager+Pattern), Active/Passive 同构 (v79.50 批次 4 复盘结论);
 * 测试仅运行于 forge 节点 (forge/build.gradle sourceSets.test 配置, neoforge 无 test 任务)。
 *
 * <p>GUI 项数一致性 (ClothSettingsScreen 手写逐项枚举, 无 ACTIVE_VALUES 循环引用,
 * Screen 是客户端类纯 JVM 不可加载) — 无法自动关联, 人工核对条目数
 * (**2026-09-16 实测: `grep -c "eb.start"` → ClothSettingsScreen 45 / TaskSettingsScreen 43**;
 * 旧值 36/27 已严重漂移 — 本轮补杂项+稀有群系+dam_fill 时顺手更正)。
 *
 * <p>⚠ 本注释不会自动过期 —— 增删 GUI 项后**必须重跑上面那条 grep** 并回写。
 * 见批次报告 fix-registry-c-2026-08-11c.md。
 */
class ConfigConsistencyTest {

    /** 统计类中 declare 的 ConfigValue 字段数 (静态字段, 类型为 ForgeConfigSpec.ConfigValue 族) */
    private static int configValueFieldCount(Class<?> c) {
        int n = 0;
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (ForgeConfigSpec.ConfigValue.class.isAssignableFrom(f.getType())) n++;
        }
        return n;
    }

    @Test
    @DisplayName("MoreActionConfig: define 字段数 == COMMON_VALUES 注册数 (v79.61 批 C5 补)")
    void common_define_vs_values() {
        assertEquals(MoreActionConfig.COMMON_VALUES.size(), configValueFieldCount(MoreActionConfig.class),
                "新增 define 必须同步 MoreActionConfig.reg(COMMON_VALUES, ...)");
    }

    @Test
    @DisplayName("allValues: 四表合并完整 (v79.61 批 C5 补 — 防忘 putAll; v79.6x 追加 kitsune 段)")
    void allValues_merges_all() {
        int total = MoreActionConfig.COMMON_VALUES.size()
                + ActiveTaskConfig.ACTIVE_VALUES.size()
                + PassiveTaskConfig.PASSIVE_VALUES.size()
                + com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.KITSUNE_VALUES.size();
        assertEquals(total, MoreActionConfig.allValues().size(),
                "allValues() 必须合并四张 VALUES map");
    }

    @Test
    @DisplayName("ActiveTaskConfig: define 字段数 == ACTIVE_VALUES 注册数")
    void active_define_vs_values() {
        assertEquals(ActiveTaskConfig.ACTIVE_VALUES.size(), configValueFieldCount(ActiveTaskConfig.class),
                "新增 define 必须同步 MoreActionConfig.reg(ACTIVE_VALUES, ...)");
    }

    @Test
    @DisplayName("PassiveTaskConfig: define 字段数 == PASSIVE_VALUES 注册数")
    void passive_define_vs_values() {
        assertEquals(PassiveTaskConfig.PASSIVE_VALUES.size(), configValueFieldCount(PassiveTaskConfig.class),
                "新增 define 必须同步 MoreActionConfig.reg(PASSIVE_VALUES, ...)");
    }

    @Test
    @DisplayName("ACTIVE_VALUES: 注册 key == reg 段前缀 + 句柄路径 (段前缀不错配)")
    void active_values_path_prefix() {
        ActiveTaskConfig.ACTIVE_VALUES.forEach((key, v) -> {
            String expected = "active." + String.join(".", v.getPath());
            assertEquals(expected, key, "reg 前缀必须与本段一致: " + key);
        });
    }

    @Test
    @DisplayName("PASSIVE_VALUES: 注册 key == reg 段前缀 + 句柄路径")
    void passive_values_path_prefix() {
        PassiveTaskConfig.PASSIVE_VALUES.forEach((key, v) -> {
            String expected = "passive." + String.join(".", v.getPath());
            assertEquals(expected, key, "reg 前缀必须与本段一致: " + key);
        });
    }

    @Test
    @DisplayName("KITSUNE_VALUES: 注册 key == kitsune_milk 段前缀 + 句柄路径 (v79.6x)")
    void kitsune_values_path_prefix() {
        com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.KITSUNE_VALUES
                .forEach((key, v) -> {
                    String expected = "kitsune_milk." + String.join(".", v.getPath());
                    assertEquals(expected, key, "reg 前缀必须与本段一致: " + key);
                });
    }
}

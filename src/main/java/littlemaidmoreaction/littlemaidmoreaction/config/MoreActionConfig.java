package littlemaidmoreaction.littlemaidmoreaction.config;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模组 Forge 配置 — 通用段入口 (v67.7 拆 3 类)。
 *
 * <p>战斗参数已迁移至规则引擎 JSON 预设（RuleActionStorage.createDefaultRules）。
 * 本类保留规则引擎总开关/调试模式 (common.toml) + 三段 Spec 统一保存入口。
 *
 * <p>三段配置 (v67.6 拆文件, v67.7 拆类):
 * <ul>
 *   <li>{@link MoreActionConfig} — 通用: 规则引擎/调试 → {@code littlemaidmoreaction-common.toml}</li>
 *   <li>{@link ActiveTaskConfig} — 主动任务 23 项 → {@code littlemaidmoreaction/active.toml}</li>
 *   <li>{@link PassiveTaskConfig} — 环境感知 11 项 → {@code littlemaidmoreaction/passive.toml}</li>
 * </ul>
 */
public final class MoreActionConfig {
    /** 通用段 Spec (config/littlemaidmoreaction-common.toml) */
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CUSTOM_RULES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    /** v67.11: 通用段 ConfigValue 句柄注册表 (path → value, 前缀 "common.") — 配置同步用 */
    public static final Map<String, ForgeConfigSpec.ConfigValue<?>> COMMON_VALUES = new HashMap<>();

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

        SPEC = b.build();
        reg(COMMON_VALUES, "common", CUSTOM_RULES_ENABLED);
        reg(COMMON_VALUES, "common", DEBUG_MODE);
    }

    private MoreActionConfig() {}

    /** v67.6: 三段 Spec 统一落盘 — Cloth 屏/编辑器保存的唯一入口 */
    public static void saveAll() {
        SPEC.save();
        ActiveTaskConfig.ACTIVE_SPEC.save();
        PassiveTaskConfig.PASSIVE_SPEC.save();
    }

    // ── v67.11: 配置同步 (专用服务器) — ConfigValue 句柄注册表 ──

    static <T> void reg(Map<String, ForgeConfigSpec.ConfigValue<?>> map, String prefix,
                                ForgeConfigSpec.ConfigValue<T> value) {
        map.put(prefix + "." + String.join(".", value.getPath()), value);
    }

    /** 全量句柄 (common + active + passive) */
    public static Map<String, ForgeConfigSpec.ConfigValue<?>> allValues() {
        Map<String, ForgeConfigSpec.ConfigValue<?>> all = new HashMap<>(COMMON_VALUES);
        all.putAll(ActiveTaskConfig.ACTIVE_VALUES);
        all.putAll(PassiveTaskConfig.PASSIVE_VALUES);
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
        Map<String, ForgeConfigSpec.ConfigValue<?>> all = allValues();
        for (ConfigValueEntry e : entries) {
            ForgeConfigSpec.ConfigValue<?> cv = all.get(e.path());
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
            ((ForgeConfigSpec.ConfigValue) cv).set(e.value());
        }
    }
}

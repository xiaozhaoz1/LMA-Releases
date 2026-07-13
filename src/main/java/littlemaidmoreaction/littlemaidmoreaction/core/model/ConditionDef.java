package littlemaidmoreaction.littlemaidmoreaction.core.model;

import java.util.Map;

/**
 * 一条条件定义 — 支持 key/val 两侧 math 运算。
 *
 * <p>123 个内置 key (v10):
 * 布尔 (80+): is_on_fire, is_in_water, is_owner_nearby, would_lethal, maid_has_shield,
 *   maid_has_weapon, is_mainhand_attack, maid_is_sitting, maid_home_mode, maid_is_pickup,
 *   target_is_boss, target_is_baby, target_is_player, world_is_night, world_is_raining, ...
 * 数值 (25+): health_ratio, distance, maid_health_ratio, favorability, random,
 *   maid_hunger, maid_armor, target_max_health, owner_distance, owner_health, ...
 * 字符串 (15+): target_type, target_name, damage_type, maid_task, dimension,
 *   maid_mainhand, maid_offhand, maid_has_effect, target_has_effect, ...
 *
 * <p>两种形态：</p>
 * <ul>
 *   <li>布尔条件 — 只有 {@code key}，如 {@code {"key":"is_on_fire"}}</li>
 *   <li>标准比较 — {@code key [math] op val [math]}，如 {@code {"key":"health_ratio","op":":<:","val":"0.5"}}</li>
 * </ul>
 *
 * <p>math 执行时机：</p>
 * <ul>
 *   <li>keyMath 在取值后、比较前应用到实际值（如 favorability - 100）</li>
 *   <li>valMath 同理应用到期望值</li>
 *   <li>math 支持 4 种算术：+ - * /</li>
 * </ul>
 *
 * @param key       条件键名
 * @param op        比较操作符，null 表示布尔条件
 * @param val       期望值，$ 前缀表示引用另一个运行时 key
 * @param keyMath   key 侧算术操作符
 * @param keyMathVal key 侧算术操作数值
 * @param valMath   val 侧算术操作符
 * @param valMathVal val 侧算术操作数值
 * @param params    条件参数，如效果ID等（空Map表示无参数）
 */
public record ConditionDef(
    String key,
    String op,
    String val,
    String keyMath,
    String keyMathVal,
    String valMath,
    String valMathVal,
    Map<String, String> params
) {
    public ConditionDef {
        if (params == null) params = Map.of();
    }

    /** 布尔条件 — 只有 key */
    public ConditionDef(String key) {
        this(key, null, null, null, null, null, null, Map.of());
    }

    /** 带参数的条件 — key + params (用于 task_active 等) */
    public ConditionDef(String key, Map<String, String> params) {
        this(key, null, null, null, null, null, null, params);
    }

    /** 简单比较 — key op val，无 math */
    public ConditionDef(String key, String op, String val) {
        this(key, op, val, null, null, null, null, Map.of());
    }

    /** 7-arg 向后兼容 — 无 params */
    public ConditionDef(String key, String op, String val,
                        String keyMath, String keyMathVal,
                        String valMath, String valMathVal) {
        this(key, op, val, keyMath, keyMathVal, valMath, valMathVal, Map.of());
    }

    /** op 为 null 表示布尔条件 */
    public boolean isBoolean() { return op == null; }

    /** val 以 $ 开头表示引用另一个运行时 key */
    public boolean isKeyRef() { return val != null && val.startsWith("$"); }

    /** key 侧有 math */
    public boolean hasKeyMath() { return keyMath != null && keyMathVal != null; }

    /** val 侧有 math */
    public boolean hasValMath() { return valMath != null && valMathVal != null; }
}

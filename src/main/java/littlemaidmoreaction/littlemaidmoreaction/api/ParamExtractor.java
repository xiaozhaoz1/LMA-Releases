package littlemaidmoreaction.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数提取器 — 桥接 impl/ 的 rawParams 与 I/O 原语。
 *
 * <p>利用 {@link TypedParam} 内置的 {@code parse()} 方法自动提取类型化参数，
 * 消除 63 个 action 文件中的手动 {@code parseDouble/parseInt/resolveTarget} 重复代码。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 条件 evaluate():
 * var p = ParamExtractor.from(rawParams, PARAMS);
 * return String.valueOf(MaidStateReader.getHealth(p.getMaid()));
 *
 * // 动作 execute():
 * var p = ParamExtractor.from(rawParams, PARAMS);
 * var t = p.resolveTarget(ctx.maid(), ctx.target());
 * if (t == null) return;
 * CombatOutput.heal(t, p.getDouble("amount"));
 * }</pre>
 *
 * <p>对标 {@code MaidStateReader} 的 final-utility-class 模式。</p>
 */
public final class ParamExtractor {
    private final Map<String, Object> values;
    private final Map<String, String> raw;

    private ParamExtractor(Map<String, Object> values, Map<String, String> raw) {
        this.values = Map.copyOf(values);
        this.raw = Map.copyOf(raw);
    }

    /**
     * 从 rawParams + TypedParam 列表构建提取器。
     *
     * <p>遍历所有 TypedParam，调用其内置 {@code parse()} 方法自动解析。
     * 解析失败时使用 TypedParam 定义的 {@code defaultValue()}。</p>
     *
     * @param raw    规则引擎传入的原始字符串参数
     * @param params 条件/动作声明的 TypedParam 元数据列表
     * @return 预解析的参数提取器
     */
    public static ParamExtractor from(Map<String, String> raw, List<TypedParam<?>> params) {
        var map = new HashMap<String, Object>();
        for (var p : params) {
            map.put(p.name(), p.parse(raw));
        }
        return new ParamExtractor(map, raw);
    }

    // === 类型安全访问器 ===

    /** 获取 double 值，param 不存在时返回 {@code def}。 */
    public double getDouble(String name, double def) {
        var v = values.get(name);
        return v instanceof Double d ? d : def;
    }

    /** 获取 double 值（TypedParam 已保证默认值，通常不需要 def 参数）。 */
    public double getDouble(String name) { return getDouble(name, 0.0); }

    /** 获取 int 值。 */
    public int getInt(String name, int def) {
        var v = values.get(name);
        return v instanceof Integer i ? i : def;
    }

    public int getInt(String name) { return getInt(name, 0); }

    /** 获取 String 值。 */
    public String getString(String name, String def) {
        var v = values.get(name);
        return v instanceof String s ? s : def;
    }

    public String getString(String name) { return getString(name, ""); }

    /** 获取 boolean 值。 */
    public boolean getBool(String name, boolean def) {
        var v = values.get(name);
        return v instanceof Boolean b ? b : def;
    }

    public boolean getBool(String name) { return getBool(name, false); }

    /** 获取原始 Map 引用（用于需要裸值的边缘情况）。 */
    public Map<String, String> raw() { return raw; }

    // === 目标解析 ===

    /**
     * 解析 "target" 参数为具体实体。
     *
     * <p>从 TypedParam 中读取名为 {@code "target"} 的 SelectParam 值：
     * <ul>
     * <li>{@code "self"} → 女仆自身</li>
     * <li>{@code "target"} → 上下文中的事件目标</li>
     * <li>{@code "owner"} → 女仆主人</li>
     * </ul>
     *
     * @param maid      规则上下文中的女仆
     * @param ctxTarget 规则上下文中的目标（可能为 null）
     * @return 解析后的实体，或 null
     */
    public LivingEntity resolveTarget(EntityMaid maid, LivingEntity ctxTarget) {
        String who = getString("target", "target");
        return switch (who) {
            case "self" -> maid;
            case "owner" -> (LivingEntity) maid.getOwner();
            default -> ctxTarget;
        };
    }

    // === 快捷方法（消除 ctx.maid() 样板代码） ===

    /**
     * 从 extractor 中获取女仆。
     * <p>这是 {@code ctx.maid()} 的语法糖，让条件 evaluate 可以完全无 ctx 引用。</p>
     *
     * @param maid 规则上下文中的女仆
     * @return 同一女仆实例（透传）
     */
    public static EntityMaid getMaid(EntityMaid maid) { return maid; }

    @Override
    public String toString() {
        return "ParamExtractor" + values;
    }
}

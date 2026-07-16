package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.MaidAttrRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ConditionDef} 可变构建器 — 替代旧 CondRow (v10)。
 *
 * <p>编辑器使用构建器编辑条件，保存时调用 {@link #build()} 生成不可变记录。</p>
 */
final class ConditionDefBuilder {
    String key, keyMath, keyMathVal, op, val, valMath, valMathVal;
    Map<String, String> params = new LinkedHashMap<>();

    ConditionDefBuilder(ConditionDef def) {
        this.key = def.key();
        this.keyMath = def.keyMath();
        this.keyMathVal = def.keyMathVal();
        this.op = def.op();
        this.val = def.val();
        this.valMath = def.valMath();
        this.valMathVal = def.valMathVal();
        this.params = new LinkedHashMap<>(def.params());
    }

    /** 深拷贝 — 编辑器使用副本编辑，保存时才同步回原始对象。 */
    ConditionDefBuilder copy() {
        return new ConditionDefBuilder(build());
    }

    /** 从旧 CondRow 式参数创建 (迁移兼容) */
    static ConditionDefBuilder fromParts(String key, String keyMath, String keyMathVal,
                                          String op, String val, String valMath, String valMathVal) {
        var b = new ConditionDefBuilder(new ConditionDef(key));
        b.keyMath = keyMath; b.keyMathVal = keyMathVal;
        b.op = op; b.val = val; b.valMath = valMath; b.valMathVal = valMathVal;
        return b;
    }

    /** 从旧 CondRow 式参数创建 (带 params — 迁移兼容) */
    static ConditionDefBuilder fromParts(String key, String keyMath, String keyMathVal,
                                          String op, String val, String valMath, String valMathVal,
                                          Map<String, String> params) {
        var b = fromParts(key, keyMath, keyMathVal, op, val, valMath, valMathVal);
        b.params = new LinkedHashMap<>(params != null ? params : Map.of());
        return b;
    }

    /** 从 ICondition 创建带默认参数的构建器 — 用于条件列表的 [+添加] */
    static ConditionDefBuilder fromICondition(ICondition cond) {
        var b = new ConditionDefBuilder(new ConditionDef(cond.key()));
        if (cond.valueType() != ConditionValueType.BOOL) {
            b.op = ":=:";
            b.val = cond.valueType() == ConditionValueType.NUM ? "0.5" : "";
        }
        for (var p : cond.params()) {
            b.params.put(p.name(), String.valueOf(p.defaultValue()));
        }
        return b;
    }

    ConditionDef build() {
        return new ConditionDef(key, op, val, keyMath, keyMathVal, valMath, valMathVal,
                Map.copyOf(params));
    }

    boolean isBool() { return op == null; }

    boolean isNum() {
        if (op == null) return false;
        return "num".equals(effectiveValueType());
    }

    private String effectiveValueType() {
        if ("maid_attr".equals(key)) {
            var e = MaidAttrRegistry.getDef(params.getOrDefault("attribute", ""));
            return e != null ? e.valueType() : "num";
        }
        var c = ConditionRegistry.get(key);
        if (c == null) return "num";
        return switch (c.valueType()) {
            case BOOL -> "bool"; case NUM -> "num"; case STR -> "str";
        };
    }

    void adaptOp() {
        var c = ConditionRegistry.get(key);
        if (c != null && c.valueType() == ConditionValueType.BOOL) { op = null; val = null; return; }
        if (op == null) op = ":=:";
    }

    String label() {
        if (isBool()) return key;
        var sb = new StringBuilder(key);
        if (isNum() && keyMath != null) sb.append(keyMath).append(keyMathVal);
        sb.append(" ").append(op).append(" ").append(val);
        if (isNum() && valMath != null) sb.append(valMath).append(valMathVal);
        return sb.toString();
    }

    String catTag() {
        return switch (effectiveValueType()) { case "bool" -> "B"; case "num" -> "N"; default -> "S"; };
    }

    int catColor() {
        return switch (effectiveValueType()) {
            case "bool" -> 0xFF8888FF; case "num" -> 0xFF88FF88; default -> 0xFFFFDD88;
        };
    }

    static String keyDisplayName(String key) {
        var c = ConditionRegistry.get(key);
        if (c != null) return c.displayName() + " (" + key + ")";
        return key;
    }
}

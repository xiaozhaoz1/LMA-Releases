package littlemaidmoreaction.littlemaidmoreaction.core.spi.param;

import java.util.List;
import java.util.Map;

/**
 * 类型安全参数定义 — 编译期类型安全的密封接口。
 *
 * <p>替代旧{@code ParamDef(ParamType, String)}弱类型设计，在编译期保证参数类型正确性。
 * 每种参数类型对应一个 record 子类型，通过 {@link TypedParamVisitor} 访问者模式实现多态分发。</p>
 *
 * <p>核心模块零 Minecraft/GUI 依赖，可在纯 Java 环境测试。</p>
 *
 * @param <T> 参数值的 Java 类型
 */
public sealed interface TypedParam<T> permits
        TypedParam.IntParam,
        TypedParam.DoubleParam,
        TypedParam.BoolParam,
        TypedParam.StringParam,
        TypedParam.SelectParam {

    /** 参数键名，对应 JSON 字段名 */
    String name();

    /** GUI 显示名 */
    String displayName();

    /** 默认值 */
    T defaultValue();

    /**
     * 从参数映射中解析此参数的值。
     *
     * @param data 参数名→参数值的映射
     * @return 解析后的值，解析失败或缺失时返回默认值
     */
    T parse(Map<String, String> data);

    /**
     * 接受访问者，用于多态分发。
     *
     * @param visitor 访问者
     * @param <R>     返回类型
     * @return 访问者的处理结果
     */
    <R> R accept(TypedParamVisitor<R> visitor);

    /**
     * TypedParam 访问者接口 — 实现 Visitor 模式。
     * <p>
     * 每个具体子类型对应一个 visit 方法，确保类型安全的多态操作。
     *
     * @param <R> 返回类型
     */
    interface TypedParamVisitor<R> {
        /** 访问 IntParam */
        R visit(IntParam param);

        /** 访问 DoubleParam */
        R visit(DoubleParam param);

        /** 访问 BoolParam */
        R visit(BoolParam param);

        /** 访问 StringParam */
        R visit(StringParam param);

        /** 访问 SelectParam */
        R visit(SelectParam param);
    }

    /**
     * 整数参数 — 从 Map 中解析 int 值。
     *
     * <p>解析失败（NumberFormatException）时返回 {@code defaultValue}。
     * 映射中不包含此参数时同样返回 {@code defaultValue}。</p>
     */
    record IntParam(
            String name,
            String displayName,
            Integer defaultValue
    ) implements TypedParam<Integer> {

        @Override
        public Integer parse(Map<String, String> data) {
            String raw = data.get(name());
            if (raw == null) return defaultValue;
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                // Log at debug level - config typos shouldn't crash the game
                // core/ 包零 Minecraft 依赖 — 使用 System.getLogger
                System.getLogger("TypedParam").log(System.Logger.Level.DEBUG,
                    "IntParam '" + name + "': invalid value, using default " + defaultValue);
                return defaultValue;
            }
        }

        @Override
        public <R> R accept(TypedParamVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    /**
     * 浮点数参数 — 从 Map 中解析 double 值。
     *
     * <p>解析失败（NumberFormatException）时返回 {@code defaultValue}。
     * 映射中不包含此参数时同样返回 {@code defaultValue}。</p>
     */
    record DoubleParam(
            String name,
            String displayName,
            Double defaultValue
    ) implements TypedParam<Double> {

        @Override
        public Double parse(Map<String, String> data) {
            String raw = data.get(name());
            if (raw == null) return defaultValue;
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                // core/ 包零 Minecraft 依赖 — 使用 System.getLogger
                System.getLogger("TypedParam").log(System.Logger.Level.DEBUG,
                    "DoubleParam '" + name + "': invalid value, using default " + defaultValue);
                return defaultValue;
            }
        }

        @Override
        public <R> R accept(TypedParamVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    /**
     * 布尔参数 — 使用 {@link Boolean#parseBoolean(String)} 解析。
     *
     * <p>{@code "true"}（不区分大小写）→ true，其他任意值（含 null）→ false。
     * 映射中不包含此参数时返回 {@code defaultValue}。</p>
     */
    record BoolParam(
            String name,
            String displayName,
            Boolean defaultValue
    ) implements TypedParam<Boolean> {

        @Override
        public Boolean parse(Map<String, String> data) {
            String raw = data.get(name());
            if (raw == null) return defaultValue;
            if ("true".equalsIgnoreCase(raw)) return true;
            if ("false".equalsIgnoreCase(raw)) return false;
            return defaultValue;
        }

        @Override
        public <R> R accept(TypedParamVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    /**
     * 字符串参数 — 返回原始字符串值。
     *
     * <p>映射中不包含此参数时返回 {@code defaultValue}。</p>
     */
    record StringParam(
            String name,
            String displayName,
            String defaultValue
    ) implements TypedParam<String> {

        @Override
        public String parse(Map<String, String> data) {
            return data.getOrDefault(name(), defaultValue);
        }

        @Override
        public <R> R accept(TypedParamVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    /**
     * 选择参数 — 值必须在预定义选项列表中。
     *
     * <p>如果提供的值不在 {@code options} 列表中，回退到 {@code defaultValue}。
     * 映射中不包含此参数时同样返回 {@code defaultValue}。</p>
     */
    record SelectParam(
            String name,
            String displayName,
            String defaultValue,
            List<String> options
    ) implements TypedParam<String> {

        public SelectParam {
            options = List.copyOf(options);
        }

        /**
         * 从映射中解析选择参数的值。
         *
         * @param data 参数名→参数值的映射
         * @return 如果值在 options 中则返回值，否则返回 defaultValue
         */
        @Override
        public String parse(Map<String, String> data) {
            String raw = data.get(name());
            if (raw == null) return defaultValue;
            if (options.contains(raw)) return raw;
            return defaultValue;
        }

        @Override
        public <R> R accept(TypedParamVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }
}

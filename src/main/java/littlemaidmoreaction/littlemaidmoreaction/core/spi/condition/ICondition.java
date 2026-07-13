package littlemaidmoreaction.littlemaidmoreaction.core.spi.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * v5 条件 SPI 接口 — Registry + Strategy 模式的核心抽象。
 *
 * <p>采用 v5 SPI 设计，相比旧版本增强：</p>
 *
 * <ul>
 *   <li>用 {@link ConditionCategory} 枚举替代 {@code String category()}</li>
 *   <li>用 {@link ConditionValueType} 枚举替代 {@code ConditionType conditionType()}</li>
 *   <li>增加 {@link #params()} 可选参数定义</li>
 *   <li>增加 {@link #isStatic()} 标记是否可在加载时预计算</li>
 *   <li>{@link #evaluate(RuleContext, Map)} 新增 {@code rawParams} 参数</li>
 * </ul>
 *
 * <p>每个条件 key 对应一个实现类，通过 {@code ConditionRegistry} 集中管理。</p>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry
 */
public interface ICondition {

    /**
     * 条件唯一 key，如 "health_ratio"。对应 JSON 配置中的 "key" 字段。
     *
     * @return 全局唯一的条件标识符
     */
    String key();

    /**
     * GUI 显示名称，如 "生命比例"。
     *
     * @return 本地化显示名
     */
    String displayName();

    /**
     * 条件分类，决定在编辑器中的分组位置。
     *
     * @return 条件分类枚举
     */
    ConditionCategory category();

    /**
     * 条件值类型，决定编辑器中操作符列表和输入控件。
     *
     * @return 值类型枚举
     */
    ConditionValueType valueType();

    /**
     * 条件自身的参数定义列表（可选）。
     *
     * <p>某些条件需要额外的配置参数，例如距离条件需要 {@code maxDistance}、
     * 任务条件需要 {@code taskName} 等。条件实现类可重写此方法返回参数列表，
     * 默认返回空列表表示无条件参数。</p>
     *
     * @return 参数定义列表，不可变
     */
    default List<TypedParam<?>> params() {
        return List.of();
    }

    /**
     * 是否为静态条件。
     *
     * <p>静态条件的值在加载时即可确定，不依赖运行时游戏状态，可以预计算。
     * 例如固定布尔常量、版本检查等。非静态条件每次评估都需重新计算。</p>
     *
     * @return true 表示可在加载时预计算
     */
    default boolean isStatic() {
        return false;
    }

    /**
     * 评估条件，从当前游戏状态中提取对应值。
     *
     * <p>返回值的字符串表示供后续操作符比较使用。例如数值条件返回 "12.5"、
     * 布尔条件返回 "true"/"false"、字符串条件返回原始字符串。</p>
     *
     * @param ctx       规则执行上下文
     * @param rawParams 用户为此条件配置的参数映射（键名→原始字符串值），
     *                  非条件自身的默认值或未配置时可传空映射
     * @return 条件值的字符串表示
     */
    String evaluate(RuleContext ctx, Map<String, String> rawParams);
}

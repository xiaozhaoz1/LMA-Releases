package littlemaidmoreaction.littlemaidmoreaction.core.spi.condition;

/**
 * 条件值类型枚举 — 决定编辑器中操作符和输入控件的展示行为。
 *
 * <p>替代 v4 中同名的 {@code ConditionType} 枚举，名称更精确以避免与
 * {@code ConditionCategory} 混淆。</p>
 *
 * <ul>
 *   <li>{@link #BOOL} — 布尔条件，无操作符，直接匹配 true/false</li>
 *   <li>{@link #NUM} — 数值条件，支持 6 种数值比较操作符</li>
 *   <li>{@link #STR} — 字符串条件，支持 5 种字符串匹配操作符</li>
 * </ul>
 *
 * @see ConditionCategory
 */
public enum ConditionValueType {
    BOOL,
    NUM,
    STR
}

package littlemaidmoreaction.littlemaidmoreaction.core.spi.condition;

/**
 * 条件分类枚举 — 标记条件所属的领域类别。
 *
 * <p>替代 v4 接口中 {@code String category()} 的字符串类型，在编译期保证分类的合法性。
 * 分类决定了条件在 GUI 编辑器中分组展示的位置。</p>
 *
 * <ul>
 *   <li>{@link #MAID} — 女仆自身状态相关条件</li>
 *   <li>{@link #TARGET} — 目标相关条件</li>
 *   <li>{@link #WORLD} — 世界/环境相关条件</li>
 *   <li>{@link #OWNER} — 主人相关条件</li>
 *   <li>{@link #META} — 元条件（逻辑组合、随机等）</li>
 * </ul>
 */
public enum ConditionCategory {
    MAID,
    TARGET,
    WORLD,
    OWNER,
    META
}

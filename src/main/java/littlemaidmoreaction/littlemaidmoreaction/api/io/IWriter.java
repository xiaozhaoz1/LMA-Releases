package littlemaidmoreaction.littlemaidmoreaction.api.io;

import java.util.Map;

/**
 * 统一状态写入器接口 — 所有 IO Writer 的公共契约。
 *
 * <p>每个 Writer 是单例，向目标实体应用状态变更。现有 static utility 类
 * (MaidStateWriter, CombatOutput, MovementOutput 等) 保持不变，
 * 额外实现此接口以支持扩展。</p>
 *
 * @param <S> 目标实体类型
 */
public interface IWriter<S> {
    /** 写入器分类: "maid_state" | "combat" | "world" | "effect" | "movement" | "visual" */
    String category();

    /** 目标实体类型 */
    Class<S> targetType();

    /** 向指定属性写入值 */
    <T> void write(S target, String property, T value);

    /** 批量写入 */
    default void writeAll(S target, Map<String, Object> properties) {
        for (var entry : properties.entrySet()) {
            write(target, entry.getKey(), entry.getValue());
        }
    }

    /** 是否支持指定属性 */
    default boolean supports(String property) {
        return true;
    }
}

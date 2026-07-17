package littlemaidmoreaction.littlemaidmoreaction.api.io;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 统一状态读取器接口 — 所有 IO Reader 的公共契约。
 *
 * <p>每个 Reader 是单例，从源实体读取类型化属性。现有 static utility 类
 * (MaidStateReader, TargetStateReader, WorldStateReader 等) 保持不变，
 * 额外实现此接口以支持扩展。</p>
 *
 * @param <S> 源实体类型 (EntityMaid, LivingEntity, Level 等)
 */
public interface IReader<S> {
    /** 读取器分类: "maid" | "world" | "target" | "item" | "tool" */
    String category();

    /** 源实体类型 */
    Class<S> sourceType();

    /** 主要扩展点: 通用类型化读取 */
    <T> T read(S source, String property, Class<T> type);

    /** 数值读取, 默认委托 read() */
    default Number readNumber(S source, String property) {
        return read(source, property, Number.class);
    }

    default float readFloat(S source, String property) {
        Number n = readNumber(source, property);
        return n != null ? n.floatValue() : 0f;
    }

    default int readInt(S source, String property) {
        Number n = readNumber(source, property);
        return n != null ? n.intValue() : 0;
    }

    default boolean readBool(S source, String property) {
        Boolean b = read(source, property, Boolean.class);
        return b != null && b;
    }

    default String readString(S source, String property) {
        Object val = read(source, property, Object.class);
        return val != null ? val.toString() : "";
    }

    /** 批量读取 */
    default Map<String, Object> readAll(S source, Set<String> properties) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String prop : properties) {
            result.put(prop, read(source, prop, Object.class));
        }
        return result;
    }
}

package littlemaidmoreaction.littlemaidmoreaction.core.model;

import java.util.Map;

/**
 * 规则动作序列中的一步 — 动作类型 ID + 参数。
 *
 * <p>JSON 格式：{@code {"type":"play_anim", "params":{"anim_name":"execution"}}}</p>
 *
 * @param typeId 动作类型标识符，通过动作注册中心解析
 * @param params 动作参数键值对
 */
public record ActionStep(String typeId, Map<String, String> params) {
    /** 仅指定类型，无参数 */
    public ActionStep(String typeId) {
        this(typeId, Map.of());
    }

    /** 便捷工厂: 变长键值对 → Map */
    public static ActionStep of(String typeId, String... kv) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) map.put(kv[i], kv[i + 1]);
        return new ActionStep(typeId, map);
    }
}

package littlemaidmoreaction.littlemaidmoreaction.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则定义 — 完整的"事件→条件→动作"触发链。
 *
 * <p>匹配流程：事件触发 → 规则启用？→ 概率通过？→ 冷却检查？→ 条件评估 → 执行动作序列</p>
 *
 * <p>使用扁平 conditions 列表 + matchMode (ALL/ANY) 匹配。</p>
 *
 * @param id            规则唯一标识
 * @param name          规则名称
 * @param enabled       是否启用
 * @param eventId       绑定的事件 ID
 * @param chance        触发概率 (0.0 ~ 1.0)
 * @param cooldown      冷却时间（tick）
 * @param priority      优先级，值越大优先级越高
 * @param matchMode     条件匹配模式
 * @param conditions    平铺条件列表
 * @param actions       动作序列
 * @param compat        依赖的 compat 模组列表 (v10, 默认空)
 */
public record RuleDef(
    int id,
    String name,
    boolean enabled,
    String eventId,
    double chance,
    int cooldown,
    int priority,
    MatchMode matchMode,
    List<ConditionDef> conditions,
    List<ActionStep> actions,
    List<String> compat
) {

    /** 紧凑构造器 — 默认值 + 防御拷贝 */
    public RuleDef {
        if (conditions == null) conditions = List.of();
        else conditions = List.copyOf(conditions);
        if (actions == null) actions = List.of();
        else actions = List.copyOf(actions);
        if (matchMode == null) matchMode = MatchMode.ALL;
        if (compat == null) compat = List.of();
        else compat = List.copyOf(compat);
    }

    // ===== 向后兼容构造器 (无 compat) =====

    public RuleDef(int id, String name, boolean enabled, String eventId,
                   double chance, int cooldown, int priority,
                   MatchMode matchMode, List<ConditionDef> conditions,
                   List<ActionStep> actions) {
        this(id, name, enabled, eventId, chance, cooldown, priority,
             matchMode, conditions, actions, List.of());
    }

    // ===== 便捷工厂 =====

    /** 最简 — 全部默认：启用、100%、无冷却、优先级0、ALL 模式 */
    public static RuleDef simple(int id, String name, String eventId,
                                  List<ConditionDef> conditions, List<ActionStep> actions) {
        return new RuleDef(id, name, true, eventId, 1.0, 0, 0,
                MatchMode.ALL, conditions, actions, List.of());
    }

    /** 完整 — 指定概率、冷却、优先级、匹配模式（无 compat） */
    public static RuleDef full(int id, String name, String eventId,
                                double chance, int cooldown, int priority, MatchMode mode,
                                List<ConditionDef> conditions, List<ActionStep> actions) {
        return new RuleDef(id, name, true, eventId, chance, cooldown, priority,
                mode, conditions, actions, List.of());
    }

    /** 完整 + compat — 指定依赖的兼容模组 */
    public static RuleDef full(int id, String name, String eventId,
                                double chance, int cooldown, int priority, MatchMode mode,
                                List<ConditionDef> conditions, List<ActionStep> actions,
                                List<String> compat) {
        return new RuleDef(id, name, true, eventId, chance, cooldown, priority,
                mode, conditions, actions, compat);
    }

    /**
     * 条件匹配所需最少条数。
     *
     * @return ALL 模式返回条件总数，ANY 模式返回 1
     */
    public int minMatch() {
        return matchMode == MatchMode.ALL ? conditions.size() : 1;
    }

    // ★ v10: 条件树 (ConditionNode) 功能已移除 — 未被使用
}

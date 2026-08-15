package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

/**
 * 任务类型 ↔ TLM uid 字符串换算 (纯函数 — 纯 JVM 可测)。
 *
 * <p>宿主类不可测故抽取 (v79.61x 测试补强): sanitize 原在 LmaTypedFlowTask (extends
 * LmaFlowTaskBase → MC 类链, 加载即炸), extractTaskType 原在 LmaTaskTypeRegistry
 * (静态 ICON_MAP 引 Items → 同样炸) — 错题 #174 铁律: 纯逻辑进纯类。
 *
 * <p>净化规则是外部注册命名约定的实现真相 (LMAT javadoc「净化撞 uid」):
 * 大写转小写、非常规字符换下划线 — "my task" 与 "my_task" 撞同一 uid。
 */
public final class TaskTypeUid {

    private TaskTypeUid() {}

    /** taskType → uid path 段净化: 小写, [a-z0-9_\-./] 之外替换为下划线, 空 → "unknown" */
    public static String sanitize(String raw) {
        String s = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-./]", "_");
        if (s.isEmpty()) s = "unknown";
        return s;
    }

    /** uid path → task_type (如 "task/craft_chain" → "craft_chain"); 非任务 path 且非 fallback → null */
    public static String extractTaskType(String uidPath) {
        if (uidPath == null) return null;
        String prefix = "task/";
        int idx = uidPath.indexOf(prefix);
        if (idx >= 0) {
            return uidPath.substring(idx + prefix.length());
        }
        if (uidPath.equals("flow_task")) return "flow_task";
        return null;
    }
}

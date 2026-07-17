package littlemaidmoreaction.littlemaidmoreaction.task;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 任务流程链 DAG (v35.4: Split×Join 双维度模型)。
 *
 * <pre>
 * SplitType (出度 — FROM完成后如何分发):
 *   SINGLE       A→B     A完成后触发B
 *   PARALLEL     A→B+C   A完成后B和C并行
 *   CONDITIONAL  A→B     满足条件时触发
 *   FALLBACK     A→B     A失败时走B
 *   REPEAT       A→A     A完成后循环自身
 *
 * JoinType (入度 — TO如何等待上游):
 *   ALL          A+B→C   全部上游完成才触发 (AND门)
 *   ANY          A∥B→C   任意一个上游完成就触发 (OR门)
 * </pre>
 */
public final class TaskFlowGraph {

    private static final Path FLOW_FILE = LittleMaidMoreAction.CONFIG_DIR.resolve("task_flow.json");
    private static final List<TaskEdge> EDGES = new CopyOnWriteArrayList<>();

    static { load(); }

    // ── CRUD ──

    public static void addEdge(String from, String to, SplitType split, JoinType join) {
        EDGES.removeIf(e -> e.from().equals(from) && e.to().equals(to));
        EDGES.add(new TaskEdge(from, to, split, join));
        save();
    }

    /** 快捷: SINGLE + ALL (最常用) */
    public static void addSingle(String from, String to) {
        addEdge(from, to, SplitType.SINGLE, JoinType.ALL);
    }

    /** 批量: 多个FROM→同TO (汇合) */
    public static void addJoin(List<String> fromTasks, String to, JoinType join) {
        for (String f : fromTasks) addEdge(f, to, SplitType.SINGLE, join);
    }

    /** 批量: 顺序链 t1→t2→t3→... */
    public static void addChain(List<String> tasks, SplitType split, JoinType join) {
        for (int i = 0; i < tasks.size() - 1; i++)
            addEdge(tasks.get(i), tasks.get(i + 1), split, join);
    }

    /** 清除所有边 */
    public static void clearAll() { EDGES.clear(); save(); }

    public static void removeEdge(String from, String to) {
        EDGES.removeIf(e -> e.from().equals(from) && e.to().equals(to));
        save();
    }

    public static List<TaskEdge> allEdges() { return List.copyOf(EDGES); }

    /** 某任务的上游 (前置依赖) */
    public static List<TaskEdge> upstream(String taskType) {
        return EDGES.stream().filter(e -> e.to().equals(taskType)).collect(Collectors.toList());
    }

    /** 某任务的下游 (后续任务) */
    public static List<TaskEdge> downstream(String taskType) {
        return EDGES.stream().filter(e -> e.from().equals(taskType)).collect(Collectors.toList());
    }

    /** 检查任务是否可触发: 上游都满足 */
    public static boolean canTrigger(String taskType) {
        List<TaskEdge> up = upstream(taskType);
        if (up.isEmpty()) return true;
        return up.stream().anyMatch(e -> e.join() == JoinType.ANY)
            || up.stream().allMatch(e -> e.join() == JoinType.ALL);
    }

    // ── JSON ──

    private static void load() {
        if (!Files.exists(FLOW_FILE)) return;
        try {
            String json = Files.readString(FLOW_FILE);
            EDGES.clear();
            int arrStart = json.indexOf('[');
            int arrEnd = json.lastIndexOf(']');
            if (arrStart < 0 || arrEnd <= arrStart) return;
            String inner = json.substring(arrStart + 1, arrEnd);
            for (String obj : inner.split("\\},\\s*\\{")) {
                obj = obj.replace("{", "").replace("}", "").trim();
                String from = extract(obj, "from");
                String to = extract(obj, "to");
                String split = extract(obj, "split");
                String join = extract(obj, "join");
                // 兼容旧格式: type=SEQUENTIAL → SINGLE+ALL, type=PARALLEL → PARALLEL+ALL
                String type = extract(obj, "type");
                if (from == null || to == null) continue;
                SplitType s = split != null ? SplitType.valueOf(split)
                    : type != null && type.equals("PARALLEL") ? SplitType.PARALLEL : SplitType.SINGLE;
                JoinType j = join != null ? JoinType.valueOf(join) : JoinType.ALL;
                EDGES.add(new TaskEdge(from, to, s, j));
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn("[TaskFlowGraph] load failed", e);
        }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder("{\"edges\":[");
        var it = EDGES.iterator();
        while (it.hasNext()) {
            var e = it.next();
            sb.append("{\"from\":\"").append(e.from())
              .append("\",\"to\":\"").append(e.to())
              .append("\",\"split\":\"").append(e.split())
              .append("\",\"join\":\"").append(e.join()).append("\"}");
            if (it.hasNext()) sb.append(",\n");
        }
        sb.append("]}");
        try { Files.writeString(FLOW_FILE, sb.toString()); }
        catch (IOException ex) { LittleMaidMoreAction.LOGGER.warn("[TaskFlowGraph] save failed", ex); }
    }

    private static String extract(String obj, String key) {
        int i = obj.indexOf("\"" + key + "\":");
        if (i < 0) return null;
        int start = obj.indexOf('"', i + key.length() + 3);
        int end = obj.indexOf('"', start + 1);
        if (start < 0 || end < 0) return null;
        return obj.substring(start + 1, end);
    }

    // ── types ──

    public record TaskEdge(String from, String to, SplitType split, JoinType join) {
        public String label() { return split.name() + "/" + join.name(); }
    }

    public enum SplitType { SINGLE, PARALLEL, CONDITIONAL, FALLBACK, REPEAT }
    public enum JoinType { ALL, ANY }

    private TaskFlowGraph() {}
}

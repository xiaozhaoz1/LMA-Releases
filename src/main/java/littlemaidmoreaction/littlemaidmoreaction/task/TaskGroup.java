package littlemaidmoreaction.littlemaidmoreaction.task;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务分组 (v35.3)。
 *
 * <p>JSON 持久化到 config/littlemaidmoreaction/task_groups.json。
 */
public final class TaskGroup {

    private static final Path GROUP_FILE = LittleMaidMoreAction.CONFIG_DIR.resolve("task_groups.json");
    private static final List<GroupDef> GROUPS = new ArrayList<>();

    static { loadDefaults(); load(); }

    public record GroupDef(String id, String label, List<String> tasks) {}

    // ── CRUD ──

    public static List<GroupDef> all() { return List.copyOf(GROUPS); }

    public static GroupDef get(String id) {
        return GROUPS.stream().filter(g -> g.id().equals(id)).findFirst().orElse(null);
    }

    /** 找出包含某任务类型的所有分组 */
    public static List<GroupDef> groupsFor(String taskType) {
        return GROUPS.stream()
            .filter(g -> g.tasks().contains(taskType))
            .toList();
    }

    // ── JSON ──

    private static void loadDefaults() {
        if (GROUPS.isEmpty()) {
            GROUPS.add(new GroupDef("crafting", "合成", List.of("craft_chain", "furnace")));
            GROUPS.add(new GroupDef("interact", "交互", List.of("jukebox", "bell_ring")));
        }
    }

    private static void load() {
        if (!Files.exists(GROUP_FILE)) { save(); return; }
        try {
            String json = Files.readString(GROUP_FILE);
            GROUPS.clear();
            int arrStart = json.indexOf('[');
            int arrEnd = json.lastIndexOf(']');
            if (arrStart < 0 || arrEnd <= arrStart) return;
            String inner = json.substring(arrStart + 1, arrEnd);
            for (String obj : inner.split("\\},\\s*\\{")) {
                obj = obj.replace("{", "").replace("}", "").trim();
                String id = extract(obj, "id");
                String label = extract(obj, "label");
                List<String> tasks = extractList(obj, "tasks");
                if (id != null && label != null) {
                    GROUPS.add(new GroupDef(id, label, tasks != null ? tasks : List.of()));
                }
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn("[TaskGroup] load failed", e);
        }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder("{\"groups\":[");
        var it = GROUPS.iterator();
        while (it.hasNext()) {
            var g = it.next();
            sb.append("{\"id\":\"").append(g.id()).append("\",\"label\":\"").append(g.label())
              .append("\",\"tasks\":[");
            var ti = g.tasks().iterator();
            while (ti.hasNext()) { sb.append('"').append(ti.next()).append('"'); if (ti.hasNext()) sb.append(','); }
            sb.append("]}");
            if (it.hasNext()) sb.append(",\n");
        }
        sb.append("]}");
        try { Files.writeString(GROUP_FILE, sb.toString()); }
        catch (IOException e) { LittleMaidMoreAction.LOGGER.warn("[TaskGroup] save failed", e); }
    }

    private static String extract(String obj, String key) {
        int i = obj.indexOf("\"" + key + "\":");
        if (i < 0) return null;
        if (obj.charAt(i + key.length() + 2) == '[') return null; // array, handled by extractList
        int start = obj.indexOf('"', i + key.length() + 3);
        int end = obj.indexOf('"', start + 1);
        if (start < 0 || end < 0) return null;
        return obj.substring(start + 1, end);
    }

    private static List<String> extractList(String obj, String key) {
        int i = obj.indexOf("\"" + key + "\":");
        if (i < 0) return null;
        int arrStart = obj.indexOf('[', i);
        int arrEnd = obj.indexOf(']', arrStart);
        if (arrStart < 0 || arrEnd < 0) return null;
        String inner = obj.substring(arrStart + 1, arrEnd);
        List<String> list = new ArrayList<>();
        for (String s : inner.split(",")) {
            String t = s.trim().replace("\"", "");
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    private TaskGroup() {}
}

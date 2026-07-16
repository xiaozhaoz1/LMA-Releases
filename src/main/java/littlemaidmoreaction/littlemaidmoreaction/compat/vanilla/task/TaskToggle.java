package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务双开关 (v35.4): enabled + showInBar。
 *
 * <p>JSON: config/littlemaidmoreaction/task_toggles.json
 * <pre>{"disabled":[], "hidden":[]}</pre>
 */
public final class TaskToggle {

    private static final Path TOGGLE_FILE = LittleMaidMoreAction.CONFIG_DIR.resolve("task_toggles.json");
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
    private static final Set<String> HIDDEN = ConcurrentHashMap.newKeySet();

    static { load(); }

    // ── enabled ──
    public static boolean isEnabled(String taskType) { return !DISABLED.contains(taskType); }
    public static void setEnabled(String taskType, boolean v) {
        if (v) DISABLED.remove(taskType); else DISABLED.add(taskType); save();
    }
    public static boolean isEnabledFor(EntityMaid maid, String taskType) {
        return isEnabled(taskType) && !maid.getPersistentData().getBoolean(TaskKeys.TASK_ENABLED_PREFIX + taskType);
    }

    // ── showInBar (v35.4) ──
    public static boolean isVisible(String taskType) { return !HIDDEN.contains(taskType); }
    public static void setVisible(String taskType, boolean v) {
        if (v) HIDDEN.remove(taskType); else HIDDEN.add(taskType); save();
    }

    public static Set<String> disabledTypes() { return Collections.unmodifiableSet(DISABLED); }
    public static Set<String> hiddenTypes() { return Collections.unmodifiableSet(HIDDEN); }

    // ── JSON ──
    private static void load() {
        if (!Files.exists(TOGGLE_FILE)) return;
        try {
            String json = Files.readString(TOGGLE_FILE);
            loadArray(json, "disabled", DISABLED);
            loadArray(json, "hidden", HIDDEN);
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.warn("[TaskToggle] load failed", e);
        }
    }
    private static void loadArray(String json, String key, Set<String> target) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return;
        int s = json.indexOf('[', i), e = json.indexOf(']', s);
        if (s < 0 || e < 0) return;
        for (String t : json.substring(s + 1, e).split(",")) {
            String v = t.trim().replace("\"", "");
            if (!v.isEmpty()) target.add(v);
        }
    }
    private static void save() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"disabled\":["); writeArr(sb, DISABLED); sb.append("],");
        sb.append("\"hidden\":["); writeArr(sb, HIDDEN); sb.append("]");
        sb.append("}");
        try { Files.writeString(TOGGLE_FILE, sb.toString()); }
        catch (IOException e) { LittleMaidMoreAction.LOGGER.warn("[TaskToggle] save failed", e); }
    }
    private static void writeArr(StringBuilder sb, Set<String> set) {
        var it = set.iterator();
        while (it.hasNext()) { sb.append('"').append(it.next()).append('"'); if (it.hasNext()) sb.append(','); }
    }

    private TaskToggle() {}
}

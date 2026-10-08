package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;

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
    /** 手写 JSON → Gson (原 indexOf+split 无转义, 含 ,/" 即解析错乱) */
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final java.lang.reflect.Type GROUP_LIST_TYPE =
            new com.google.gson.reflect.TypeToken<List<GroupDef>>() {}.getType();

    static { loadDefaults(); load(); }

    /** Gson 写回 (原 StringBuilder 手拼) */
    private static void save() {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.add("groups", GSON.toJsonTree(GROUPS));
        try { Files.writeString(GROUP_FILE, GSON.toJson(root)); }
        catch (IOException e) { LittleMaidMoreAction.LOGGER.warn("[TaskGroup] save failed", e); }
    }

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
            List<GroupDef> list = List.of();
            com.google.gson.JsonObject root = GSON.fromJson(Files.readString(GROUP_FILE), com.google.gson.JsonObject.class);
            if (root != null && root.has("groups")) {
                list = GSON.fromJson(root.get("groups"), GROUP_LIST_TYPE);
            }
            // 审计 B2: 损坏/空配置保留默认分组 (原 GROUPS.clear() 后解析失败 → 默认组全丢)
            if (list != null && !list.isEmpty()) {
                GROUPS.clear();
                GROUPS.addAll(list);
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn("[TaskGroup] load failed", e);
        }
    }




    private TaskGroup() {}
}

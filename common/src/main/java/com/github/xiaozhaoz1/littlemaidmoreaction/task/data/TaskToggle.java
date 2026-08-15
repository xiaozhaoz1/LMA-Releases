package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;

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
    /** 手写 JSON → Gson (原 indexOf+split 无转义, 含 ,/" 即解析错乱) */
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    /** Gson 写回 (原 StringBuilder 手拼) */
    private static void save() {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        com.google.gson.JsonArray d = new com.google.gson.JsonArray();
        DISABLED.forEach(d::add);
        com.google.gson.JsonArray h = new com.google.gson.JsonArray();
        HIDDEN.forEach(h::add);
        root.add("disabled", d);
        root.add("hidden", h);
        try { Files.writeString(TOGGLE_FILE, GSON.toJson(root)); }
        catch (IOException e) { LittleMaidMoreAction.LOGGER.warn("[TaskToggle] save failed", e); }
    }

    static { load(); }

    // ── enabled ──
    public static boolean isEnabled(String taskType) { return !DISABLED.contains(taskType); }
    public static void setEnabled(String taskType, boolean v) {
        if (v) DISABLED.remove(taskType); else DISABLED.add(taskType); save();
    }
    public static boolean isEnabledFor(EntityMaid maid, String taskType) {
        // per-maid 禁用键无写入方 (死功能) — 简化为全局开关
        return isEnabled(taskType);
    }

    // ── showInBar ──
    public static boolean isVisible(String taskType) { return !HIDDEN.contains(taskType); }
    public static void setVisible(String taskType, boolean v) {
        if (v) HIDDEN.remove(taskType); else HIDDEN.add(taskType); save();
    }

    private static void load() {
        if (!Files.exists(TOGGLE_FILE)) return;
        try {
            com.google.gson.JsonObject root = GSON.fromJson(Files.readString(TOGGLE_FILE), com.google.gson.JsonObject.class);
            if (root != null) {
                DISABLED.clear(); HIDDEN.clear();
                com.google.gson.JsonArray d = root.getAsJsonArray("disabled");
                if (d != null) for (com.google.gson.JsonElement e : d) {
                    // 逐条容错 (审计 B2): 单个坏条目跳过, 不整表作废
                    try { DISABLED.add(e.getAsString()); }
                    catch (Exception ex) { LittleMaidMoreAction.LOGGER.warn("[TaskToggle] skip bad disabled entry", ex); }
                }
                com.google.gson.JsonArray h = root.getAsJsonArray("hidden");
                if (h != null) for (com.google.gson.JsonElement e : h) {
                    try { HIDDEN.add(e.getAsString()); }
                    catch (Exception ex) { LittleMaidMoreAction.LOGGER.warn("[TaskToggle] skip bad hidden entry", ex); }
                }
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn("[TaskToggle] load failed", e);
        }
    }

    private TaskToggle() {}
}

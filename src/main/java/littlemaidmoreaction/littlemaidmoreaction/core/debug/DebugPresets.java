package littlemaidmoreaction.littlemaidmoreaction.core.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.model.MatchMode;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * DEBUG 模式验证预设 — 2 条规则覆盖全部 136 条件 + 104 动作。
 *
 * <p>当 {@link MoreActionConfig#DEBUG_MODE} 开启时自动生成，关闭时自动删除。
 *
 * <p><b>规则 800</b>: {@code debug_conditions} 条件遍历
 * {@link littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry}
 * 评测全部条件并输出日志。<br>
 * <b>规则 801</b>: {@code debug_all_actions} 动作遍历
 * {@link littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry}
 * 执行全部动作（跳过高危）并输出日志。
 */
public final class DebugPresets {

    static final int DEBUG_ID_START = 800;
    private static final int COOLDOWN = 1200;
    private static final int PRIORITY = -1;

    private static final Gson GSON;
    static {
        Gson g;
        try { g = new GsonBuilder().setPrettyPrinting().create(); }
        catch (Throwable ignored) { g = null; }
        GSON = g;
    }

    public static void ensureSynced(Path rulesDir) {
        boolean debugOn = MoreActionConfig.DEBUG_MODE.get();
        boolean exists = debugPresetsExist(rulesDir);

        if (debugOn && !exists) {
            generate(rulesDir);
        } else if (!debugOn && exists) {
            deleteAll(rulesDir);
        } else if (debugOn && exists) {
            LittleMaidMoreAction.LOGGER.info("[DebugPresets] already synced (DEBUG on, rules exist)");
        } else {
            LittleMaidMoreAction.LOGGER.debug("[DebugPresets] skipped (DEBUG off, no rules)");
        }
    }

    // ─── 生成 2 条规则 ───────────────────────────────────────

    private static void generate(Path rulesDir) {
        if (GSON == null) {
            LittleMaidMoreAction.LOGGER.warn("[DebugPresets] Gson not available, skip");
            return;
        }

        List<RuleDef> rules = new ArrayList<>();

        // Rule 800: 条件全量测试 (默认禁用 — 需手动开启)
        rules.add(new RuleDef(800, "DEBUG-全部条件", false, "maid_tick", 1.0, COOLDOWN, PRIORITY,
            MatchMode.ALL,
            List.of(new ConditionDef("debug_conditions")),
            List.of(ActionStep.of("send_message",
                "message", "[LMA DEBUG] conditions checked — see log",
                "type", "chat")),
            List.of()));

        // Rule 801: 动作全量测试 (默认禁用 — 需手动开启)
        rules.add(new RuleDef(801, "DEBUG-全部动作", false, "maid_tick", 1.0, COOLDOWN, PRIORITY,
            MatchMode.ALL,
            List.of(new ConditionDef("is_tamed", ":=:", "true")),
            List.of(
                ActionStep.of("send_message",
                    "message", "[LMA DEBUG] actions test START",
                    "type", "chat"),
                ActionStep.of("debug_all_actions"),
                ActionStep.of("send_message",
                    "message", "[LMA DEBUG] actions test END — see log",
                    "type", "chat")
            ),
            List.of()));

        int written = 0;
        for (RuleDef r : rules) {
            try {
                Files.writeString(rulesDir.resolve(r.id() + ".rule.json"), GSON.toJson(r));
                written++;
            } catch (IOException e) {
                LittleMaidMoreAction.LOGGER.warn("[DebugPresets] write {}: {}", r.id(), e.toString());
            }
        }
        LittleMaidMoreAction.LOGGER.info("[DebugPresets] generated {} debug rules", written);
    }

    // ─── 删除 ────────────────────────────────────────────────

    static void deleteAll(Path rulesDir) {
        int deleted = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rulesDir, "*.rule.json")) {
            for (Path file : ds) {
                if (isDebugFile(file)) {
                    try { Files.delete(file); deleted++; } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        if (deleted > 0) {
            try { LittleMaidMoreAction.LOGGER.info("[DebugPresets] deleted {} debug rules", deleted); }
            catch (Throwable ignored) {}
        }
    }

    static boolean debugPresetsExist(Path rulesDir) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rulesDir, "*.rule.json")) {
            for (Path file : ds) { if (isDebugFile(file)) return true; }
        } catch (IOException ignored) {}
        return false;
    }

    private static boolean isDebugFile(Path file) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        if (dot <= 0) return false;
        try { return Integer.parseInt(name.substring(0, dot)) >= DEBUG_ID_START; }
        catch (NumberFormatException ignored) { return false; }
    }

    private DebugPresets() {}
}

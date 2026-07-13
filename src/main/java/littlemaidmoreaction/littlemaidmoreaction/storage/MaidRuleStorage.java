package littlemaidmoreaction.littlemaidmoreaction.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * v9.0 女仆独立规则存储 — maid_rules/<uuid>/id.rule.json。
 *
 * <p>每个女仆一个文件夹，每条规则一个文件。方便复制整个文件夹给其他女仆。</p>
 */
public final class MaidRuleStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(ActionStep.class, new littlemaidmoreaction.littlemaidmoreaction.core.serialization.ActionStepAdapter())
            .registerTypeAdapter(ConditionDef.class, new littlemaidmoreaction.littlemaidmoreaction.core.serialization.ConditionDefAdapter())
            .create();
    private static final Path DIR =
            LittleMaidMoreAction.CONFIG_DIR.resolve("maid_rules");

    /** 内存缓存: UUID → 规则列表 */
    private static final ConcurrentHashMap<UUID, List<RuleDef>> STORE = new ConcurrentHashMap<>();

    // ─── Load ────────────────────────────────────────────────

    /** 从 maid_rules/<uuid>/ 目录加载该女仆的全部规则。 */
    public static List<RuleDef> load(UUID uuid) {
        Path maidDir = DIR.resolve(uuid.toString());
        if (!Files.isDirectory(maidDir)) {
            STORE.put(uuid, List.of());
            return List.of();
        }

        List<RuleDef> loaded = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(maidDir, "*.rule.json")) {
            for (Path file : ds) {
                try {
                    RuleDef r = GSON.fromJson(Files.readString(file), RuleDef.class);
                    if (r != null) loaded.add(r);
                } catch (Exception e) {
                    LittleMaidMoreAction.LOGGER.warn(
                        "[MaidStorage] failed to parse: {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[MaidStorage] scan failed: {}", uuid, e);
            STORE.put(uuid, List.of());
            return List.of();
        }

        loaded.sort(java.util.Comparator.comparingInt(RuleDef::id));
        List<RuleDef> result = List.copyOf(loaded);
        STORE.put(uuid, result);
        LittleMaidMoreAction.LOGGER.info("[MaidStorage] {} loaded {} rules", uuid, result.size());
        return result;
    }

    // ─── Save / Delete (单条) ────────────────────────────────

    /** 保存单条规则到 maid_rules/<uuid>/<id>.rule.json。 */
    public static void saveRule(UUID uuid, RuleDef rule) {
        try {
            Path maidDir = DIR.resolve(uuid.toString());
            Files.createDirectories(maidDir);
            Files.writeString(maidDir.resolve(rule.id() + ".rule.json"), GSON.toJson(rule));

            List<RuleDef> list = STORE.get(uuid);
            List<RuleDef> updated = (list != null) ? new ArrayList<>(list) : new ArrayList<>();
            updated.removeIf(r -> r.id() == rule.id());
            updated.add(rule);
            STORE.put(uuid, List.copyOf(updated));
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[MaidStorage] saveRule: {}/{}", uuid, rule.id(), e);
        }
    }

    /** 删除 maid_rules/<uuid>/<id>.rule.json。 */
    public static void deleteRule(UUID uuid, int id) {
        try {
            Files.deleteIfExists(DIR.resolve(uuid.toString()).resolve(id + ".rule.json"));
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.warn("[MaidStorage] deleteRule: {}/{}", uuid, id, e);
        }
        List<RuleDef> list = STORE.get(uuid);
        if (list != null) {
            List<RuleDef> updated = new ArrayList<>(list);
            updated.removeIf(r -> r.id() == id);
            if (updated.isEmpty()) {
                STORE.remove(uuid);
                try { Files.deleteIfExists(DIR.resolve(uuid.toString())); } catch (IOException ignored) {}
            } else {
                STORE.put(uuid, List.copyOf(updated));
            }
        }
    }

    // ─── 批量操作（屏幕兼容 API）──────────────────────────────

    /** 获取女仆的全部规则（内存缓存 + 懒加载）。 */
    public static List<RuleDef> getRules(UUID uuid) {
        List<RuleDef> cached = STORE.get(uuid);
        if (cached != null) return cached;
        return load(uuid);
    }

    /** 整体保存女仆的全部规则 + 清理已移除的文件。 */
    public static synchronized void saveRules(UUID uuid, List<RuleDef> list) {
        try {
            Path maidDir = DIR.resolve(uuid.toString());
            Files.createDirectories(maidDir);

            Set<Integer> newIds = list.stream().map(RuleDef::id).collect(Collectors.toSet());
            // 删除已移除的文件
            List<RuleDef> oldList = STORE.getOrDefault(uuid, List.of());
            for (RuleDef old : oldList) {
                if (!newIds.contains(old.id())) {
                    try {
                        Files.deleteIfExists(maidDir.resolve(old.id() + ".rule.json"));
                    } catch (IOException e) {
                        LittleMaidMoreAction.LOGGER.warn("[MaidStorage] del removed: {}/{}", uuid, old.id(), e);
                    }
                }
            }
            // 写入全部
            for (RuleDef r : list) {
                Files.writeString(maidDir.resolve(r.id() + ".rule.json"), GSON.toJson(r));
            }
            // 清理空目录
            if (list.isEmpty()) {
                try { Files.deleteIfExists(maidDir); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[MaidStorage] saveRules failed: {}", uuid, e);
            return;
        }

        if (list.isEmpty()) {
            STORE.remove(uuid);
        } else {
            STORE.put(uuid, List.copyOf(list));
        }
    }

    private MaidRuleStorage() {}
}

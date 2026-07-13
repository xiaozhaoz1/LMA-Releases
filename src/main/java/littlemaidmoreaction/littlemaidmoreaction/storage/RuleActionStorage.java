package littlemaidmoreaction.littlemaidmoreaction.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.cache.RuleIndex;
import littlemaidmoreaction.littlemaidmoreaction.core.model.MatchMode;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * v9.0 文件系统规则存储 — 扁平目录，compat 字段分类。
 *
 * <pre>
 * config/littlemaidmoreaction/rules/
 *   0.rule.json ... 8.rule.json    ← 内置预设
 *   100.rule.json  101.rule.json   ← compat: ["ysm"]
 * </pre>
 */
public final class RuleActionStorage {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ActionStep.class,
                    new littlemaidmoreaction.littlemaidmoreaction.core.serialization.ActionStepAdapter())
            .registerTypeAdapter(ConditionDef.class,
                    new littlemaidmoreaction.littlemaidmoreaction.core.serialization.ConditionDefAdapter())
            .create();

    private static final Path RULES_DIR =
            LittleMaidMoreAction.CONFIG_DIR.resolve("rules");

    private static final List<RuleDef> rules = new CopyOnWriteArrayList<>();
    private static volatile List<RuleDef> sortedCache = List.of();

    // ─── Load ────────────────────────────────────────────────

    /** 扫描 rules/*.rule.json。每次启动检查缺失预设 (v12 P4: 不再仅首次)。 */
    public static void load() {
        try {
            Files.createDirectories(RULES_DIR);
            // ★ v12 P4: 总是调用 seedDefaults (writeIfMissing 幂等)。
            // 旧行为 if(dirIsEmpty()) 导致升级后新增预设永不被写入。
            seedDefaults();
            littlemaidmoreaction.littlemaidmoreaction.core.debug.DebugPresets.ensureSynced(RULES_DIR);
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[Storage] failed to init", e);
        }

        rules.clear();
        loadDir(RULES_DIR);
        rules.sort(Comparator.comparingInt(RuleDef::id)); // 数字序: 0-8内置, 200+compat, 800+task
        RuleIndex.rebuild();
        LittleMaidMoreAction.LOGGER.info("[Storage] loaded {} rules", rules.size());
    }

    /** 确保 compat 模块的默认规则存在（幂等 — 文件已存在则跳过）。
     *  <p>★ 若写入了新文件，自动重建索引，确保 compat 规则在首次触发前已生效。 */
    public static void ensureCompatDefaults(List<RuleDef> defaults) {
        boolean changed = false;
        for (RuleDef r : defaults) {
            Path file = RULES_DIR.resolve(r.id() + ".rule.json");
            if (!Files.exists(file)) {
                try {
                    Files.writeString(file, GSON.toJson(r));
                    LittleMaidMoreAction.LOGGER.info("[Storage] seeded compat rule: {}", r.id());
                    changed = true;
                } catch (IOException e) {
                    LittleMaidMoreAction.LOGGER.error("[Storage] seed compat failed: {}", r.id(), e);
                }
            }
        }
        if (changed) reload();
    }

    private static void loadDir(Path dir) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.rule.json")) {
            for (Path file : ds) {
                try {
                    RuleDef r = GSON.fromJson(Files.readString(file), RuleDef.class);
                    if (r != null) rules.add(r);
                } catch (Exception e) {
                    LittleMaidMoreAction.LOGGER.warn("[Storage] parse fail: {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[Storage] scan fail: {}", dir, e);
        }
    }

    // ─── Save / Delete ───────────────────────────────────────

    public static void saveRule(RuleDef rule) {
        try {
            writeRuleFile(rule);
            rules.removeIf(r -> r.id() == rule.id());
            rules.add(rule);
            sortedCache = List.of();
            RuleIndex.rebuild();
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[Storage] saveRule: {}", rule.id(), e);
        }
    }

    public static void deleteRule(int id) {
        try {
            Files.deleteIfExists(RULES_DIR.resolve(id + ".rule.json"));
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.warn("[Storage] deleteRule: {}", id, e);
        }
        rules.removeIf(r -> r.id() == id);
        sortedCache = List.of();
        RuleIndex.rebuild();
    }

    // ─── 批量操作 ────────────────────────────────────────────

    public static List<RuleDef> getRules() { return List.copyOf(rules); }

    public static List<RuleDef> getSortedRules() {
        if (!sortedCache.isEmpty()) return sortedCache;
        synchronized (RuleActionStorage.class) {
            if (!sortedCache.isEmpty()) return sortedCache;
            List<RuleDef> list = new ArrayList<>(rules);
            list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
            sortedCache = List.copyOf(list);
            return sortedCache;
        }
    }

    public static synchronized void replaceRules(List<RuleDef> list) {
        Set<Integer> newIds = list.stream().map(RuleDef::id).collect(Collectors.toSet());
        for (RuleDef old : rules) {
            if (!newIds.contains(old.id())) {
                try { Files.deleteIfExists(RULES_DIR.resolve(old.id() + ".rule.json")); }
                catch (IOException e) { LittleMaidMoreAction.LOGGER.warn("[Storage] del: {}", old.id(), e); }
            }
        }
        for (RuleDef r : list) {
            try { writeRuleFile(r); }
            catch (IOException e) { LittleMaidMoreAction.LOGGER.error("[Storage] write: {}", r.id(), e); }
        }
        rules.clear();
        rules.addAll(list);
        sortedCache = List.of();
        RuleIndex.rebuild();
    }

    public static void restorePresets() {
        for (RuleDef r : rules) {
            try { Files.deleteIfExists(RULES_DIR.resolve(r.id() + ".rule.json")); }
            catch (IOException e) { LittleMaidMoreAction.LOGGER.warn("[Storage] restore del: {}", r.id(), e); }
        }
        rules.clear();
        try { seedDefaultsForce(); } catch (IOException e) { LittleMaidMoreAction.LOGGER.error("[Storage] restore seed", e); }
        // DEBUG 预设同步（在 load 之前，确保生成后能被扫描到）
        littlemaidmoreaction.littlemaidmoreaction.core.debug.DebugPresets.ensureSynced(RULES_DIR);
        load();
        LittleMaidMoreAction.LOGGER.info("[Storage] presets restored ({} rules)", rules.size());
    }

    public static void reload() { load(); }

    /** 同步 DEBUG 预设 — 由 LMAConfigScreen DEBUG toggle 触发。 */
    public static void syncDebugPresets() {
        littlemaidmoreaction.littlemaidmoreaction.core.debug.DebugPresets.ensureSynced(RULES_DIR);
        reload();
    }

    // ─── 内部 ────────────────────────────────────────────────

    private static void writeRuleFile(RuleDef rule) throws IOException {
        Files.writeString(RULES_DIR.resolve(rule.id() + ".rule.json"), GSON.toJson(rule));
    }

    private static boolean dirIsEmpty() throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(RULES_DIR, "*.rule.json")) {
            return !ds.iterator().hasNext();
        }
    }

    private static void seedDefaults() throws IOException {
        List<RuleDef> defaults = createDefaultRules();
        int seeded = 0;
        for (RuleDef r : defaults) { if (writeIfMissing(r)) seeded++; }
        // compat 模块默认规则（always seed — compat 未加载时降级运行）
        for (RuleDef r : littlemaidmoreaction.littlemaidmoreaction.compat.ysm.YsmPresets.createDefaults()) { if (writeIfMissing(r)) seeded++; }
        for (RuleDef r : littlemaidmoreaction.littlemaidmoreaction.impl.altar.AltarPresets.createDefaults()) { if (writeIfMissing(r)) seeded++; }
        // ★ v18: AiDemoPresets removed — task system no longer uses rule engine
        LittleMaidMoreAction.LOGGER.info("[Storage] seeded {} new default rules", seeded);
    }

    /** 强制写入全部预设（restorePresets 专用） */
    private static void seedDefaultsForce() throws IOException {
        for (RuleDef r : createDefaultRules()) writeRuleFile(r);
        for (RuleDef r : littlemaidmoreaction.littlemaidmoreaction.compat.ysm.YsmPresets.createDefaults()) writeRuleFile(r);
        for (RuleDef r : littlemaidmoreaction.littlemaidmoreaction.impl.altar.AltarPresets.createDefaults()) writeRuleFile(r);
        // ★ v18: AiDemoPresets removed
    }

    /** 文件不存在时才写入，返回 true 表示实际写入了 */
    private static boolean writeIfMissing(RuleDef rule) throws IOException {
        Path file = RULES_DIR.resolve(rule.id() + ".rule.json");
        if (Files.exists(file)) return false;
        Files.writeString(file, GSON.toJson(rule));
        return true;
    }

    private static List<RuleDef> createDefaultRules() {
        List<RuleDef> list = new ArrayList<>();
        int r = 0;
        list.add(RuleDef.full(r++, "预设-处决", "maid_hurt_target_pre", 1.0, 200, 100, MatchMode.ALL,
            List.of(new ConditionDef("damage_type", ":=:", "melee"), new ConditionDef("would_lethal")),
            List.of(ActionStep.of("cancel_event"), ActionStep.of("teleport", "target", "target", "mode", "in_front", "distance", "2.0"),
                ActionStep.of("play_anim", "mode", "INSTANT", "anim", "execution", "auto_wait", "true"),
                ActionStep.of("deal_damage", "damage_type", "execution_kill"))));
        list.add(RuleDef.full(r++, "预设-闪避", "maid_attack", 0.1, 60, 50, MatchMode.ALL,
            List.of(new ConditionDef("damage_type", ":=:", "ranged")),
            List.of(ActionStep.of("cancel_event"), ActionStep.of("teleport", "target", "self", "mode", "offset", "offset_x", "0.5"),
                ActionStep.of("play_anim", "mode", "INSTANT", "anim", "animation.flash1", "auto_wait", "true"),
                ActionStep.of("random", "chance", "0.3", "skip", "2"),
                ActionStep.of("play_anim", "mode", "INSTANT", "anim", "animation.Mock1", "auto_wait", "true"))));
        list.add(RuleDef.full(r++, "预设-弹反", "maid_attack", 0.15, 100, 75, MatchMode.ALL,
            List.of(new ConditionDef("damage_type", ":=:", "ranged"), new ConditionDef("maid_has_shield")),
            List.of(ActionStep.of("cancel_event"), ActionStep.of("play_anim", "mode", "INSTANT", "anim", "parry", "auto_wait", "true"),
                ActionStep.of("apply_effect", "effect_id", "minecraft:strength", "duration", "200", "amplifier", "1", "target", "self"))));
        list.add(RuleDef.full(r++, "预设-提取经验", "maid_interact", 1.0, 0, 0, MatchMode.ALL,
            List.of(new ConditionDef("target_holding_item", ":=:", "minecraft:glass_bottle")),
            List.of(ActionStep.of("cancel_event"), ActionStep.of("extract_maid_xp", "ratio", "1", "max_bottles", "64"))));
        list.add(RuleDef.full(r++, "预设-挨打还手", "maid_attack", 1.0, 100, 50, MatchMode.ALL,
            List.of(new ConditionDef("maid_has_weapon"), new ConditionDef("is_combat_task", ":=:", "false")),
            List.of(ActionStep.of("save_switch_task", "task", "attack"), ActionStep.of("wait", "ticks", "200"), ActionStep.of("restore_maid_task"))));
        list.add(RuleDef.full(r++, "预设-守护主人", "maid_tick", 1.0, 20, 10, MatchMode.ALL,
            List.of(new ConditionDef("is_combat_task", ":=:", "true"), new ConditionDef("is_owner_target")),
            List.of(ActionStep.of("force_target", "mode", "owner_attacker"))));
        list.add(RuleDef.full(r++, "预设-协助攻击", "maid_tick", 1.0, 20, 10, MatchMode.ALL,
            List.of(new ConditionDef("is_combat_task", ":=:", "true"), new ConditionDef("owner_has_attack_target")),
            List.of(ActionStep.of("force_target", "mode", "owner_target"))));
        list.add(RuleDef.full(r++, "预设-肘击", "maid_hurt_target_pre", 0.5, 200, 100, MatchMode.ALL,
            List.of(new ConditionDef("damage_type", ":=:", "melee")),
            List.of(ActionStep.of("cancel_event"), ActionStep.of("dash", "toward_target", "true"),
                ActionStep.of("play_sound", "sound_id", "littlemaidmoreaction:man,littlemaidmoreaction:manbaout,littlemaidmoreaction:whatcanisay", "volume", "1.0", "pitch", "1.0"),
                ActionStep.of("play_anim", "mode", "INSTANT", "anim", "animation.man", "auto_wait", "true"),
                ActionStep.of("deal_damage", "damage_type", "mob_attack", "amount", "10.0"))));
        list.add(RuleDef.full(r++, "预设-单个规则编辑", "maid_interact", 1.0, 0, 100, MatchMode.ALL,
            List.of(new ConditionDef("is_tamed", ":=:", "true"), new ConditionDef("owner_holding_item", ":=:", "minecraft:stick")),
            List.of(ActionStep.of("open_maid_editor"), ActionStep.of("cancel_event"))));
        list.add(RuleDef.full(r++, "预设-经验修武器", "maid_tick", 1.0, 200, 5, MatchMode.ALL,
            List.of(),
            List.of(ActionStep.of("repair_item"))));
        list.add(RuleDef.full(r++, "预设-自动匹配种子", "maid_harvest_crop", 1.0, 2, 100, MatchMode.ALL,
            List.of(),
            List.of(ActionStep.of("auto_match_crop", "scope", "maid", "enabled", "true"))));
        return list;
    }

    // ─── 内置预设定义 ────────────────────────────────────────

    // ... createDefaultRules() unchanged (9 rules, IDs 0-8) ...

    private RuleActionStorage() {}
}

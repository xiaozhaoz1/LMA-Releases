package littlemaidmoreaction.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 规则写入服务 — 基于 JSON 模板创建女仆独立规则文件。
 */
public final class RuleWriter {
    private static final System.Logger LOG = System.getLogger("LMA-V16-RuleWriter");
    private RuleWriter() {}

    private static final String TEMPLATE_DIR = "/assets/littlemaidmoreaction/rules/presets/";
    private static final Path MAID_RULES_DIR = LittleMaidMoreAction.CONFIG_DIR.resolve("maid_rules");

    public static void writeFromTemplate(EntityMaid maid, String templateName,
                                          String target, int targetCount, String taskId) {
        LOG.log(System.Logger.Level.INFO, "[V16] [RuleWriter] template: {0}, target={1}, count={2}, taskId={3}",
                templateName, target, targetCount, taskId);

        LOG.log(System.Logger.Level.INFO, "[V16] [RuleWriter] reading template from JAR: /assets/.../rules/presets/{0}.json", templateName);
        String json = readTemplate(templateName);
        if (json == null) {
            LOG.log(System.Logger.Level.ERROR, "[RuleWriter] template not found: {0}", templateName);
            return;
        }
        LOG.log(System.Logger.Level.INFO, "[V16] [RuleWriter] template loaded: {0} chars", json.length());

        json = json.replace("{{task_id}}", taskId)
                   .replace("{{target}}", target)
                   .replace("{{target_count}}", String.valueOf(targetCount));
        LOG.log(System.Logger.Level.INFO, "[V16] [RuleWriter] placeholders replaced");

        int newId = allocateRuleId(maid.getStringUUID());
        json = json.replace("\"id\": -1", "\"id\": " + newId);
        LOG.log(System.Logger.Level.INFO, "[V16] [RuleWriter] allocated rule ID: {0}", newId);

        json = json.replace("\"enabled\": false", "\"enabled\": true");

        Path maidDir = MAID_RULES_DIR.resolve(maid.getStringUUID());
        Path ruleFile = maidDir.resolve(newId + ".rule.json");
        try {
            Files.createDirectories(ruleFile.getParent());
            Files.writeString(ruleFile, json, StandardCharsets.UTF_8);
            LOG.log(System.Logger.Level.INFO, "[V16] [RuleWriter] written to: {0}", ruleFile.toAbsolutePath());
            // ★ Bug #70 fix: 强制从磁盘重新加载 → 更新 MaidRuleStorage 缓存
            // RuleWriter 直接写磁盘绕过了 STORE 缓存，导致 MaidRuleIndex.rebuild()
            // 从旧缓存取数据，永远看不到新规则 → maid_tick 不匹配
            java.util.UUID uid = java.util.UUID.fromString(maid.getStringUUID());
            littlemaidmoreaction.littlemaidmoreaction.storage.MaidRuleStorage.load(uid);
            // 重建索引 — RuleEngine 需要能匹配到新写入的规则
            littlemaidmoreaction.littlemaidmoreaction.core.cache.MaidRuleIndex.rebuild(uid);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "[RuleWriter] write failed", e);
        }
    }

    private static String readTemplate(String name) {
        String path = TEMPLATE_DIR + name + ".json";
        try (InputStream is = RuleWriter.class.getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static int allocateRuleId(String maidUuid) {
        Path maidDir = MAID_RULES_DIR.resolve(maidUuid);
        if (!Files.exists(maidDir)) return 1;
        try (var files = Files.list(maidDir)) {
            return files.map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith(".rule.json"))
                .map(name -> name.replace(".rule.json", ""))
                .mapToInt(name -> {
                    try { return Integer.parseInt(name); }
                    catch (NumberFormatException e) { return 0; }
                })
                .max()
                .orElse(0) + 1;
        } catch (IOException e) {
            return 1;
        }
    }
}

package littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.event.RuleEvent;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;

import java.util.*;

/**
 * LMA 统一搜索工具 (v10)。
 *
 * <p>LLM 可按不同维度搜索 LMA 数据：
 * <ul>
 *   <li>规则 — 按事件、启用状态、名称、compat 筛选</li>
 *   <li>条件 — 按分类、名称筛选，显示 key + 参数</li>
 *   <li>动作 — 按分类、名称筛选，显示 typeId + 参数</li>
 *   <li>事件 — 列出全部 (LLM 在调用 create_rule 前确认)</li>
 * </ul>
 *
 * <p>使用时机：主人问"有哪些规则"/"拔刀剑的规则有几个"/"有什么条件可以用"时调用。
 * 不必每次对话都查——先看 lma_status 自动上下文中已有规则摘要。
 */
public final class LmaSearchTool implements ITool<LmaSearchTool.Params> {

    public record Params(
        String searchType,
        String event,
        String enabled,
        String category,
        String name,
        String compat
    ) {
        // ★ 懒加载 — 避免 AI 回调线程 static init 时 NoClassDefFoundError
        private static volatile Codec<Params> CODEC;
        static Codec<Params> codec() {
            if (CODEC == null) {
                synchronized (Params.class) {
                    if (CODEC == null) {
                        CODEC = RecordCodecBuilder.create(i -> i.group(
                            Codec.STRING.fieldOf("search_type").forGetter(Params::searchType),
                            Codec.STRING.optionalFieldOf("event", "").forGetter(Params::event),
                            Codec.STRING.optionalFieldOf("enabled", "all").forGetter(Params::enabled),
                            Codec.STRING.optionalFieldOf("category", "").forGetter(Params::category),
                            Codec.STRING.optionalFieldOf("name", "").forGetter(Params::name),
                            Codec.STRING.optionalFieldOf("compat", "").forGetter(Params::compat)
                        ).apply(i, Params::new));
                    }
                }
            }
            return CODEC;
        }
    }

    @Override public String id() { return "lma_search"; }

    @Override
    public String summary(EntityMaid maid) {
        return """
            Search LMA rules, conditions, actions, or events by filters.

            search_type values:
            - "rules": filter by event, enabled (true/false/all), name, compat
            - "conditions": filter by category (maid/target/world), name
            - "actions": filter by category (combat/maid/maid_ext/control/visual/movement/world/effect/item/script), name
            - "events": list all trigger events (no filters needed)

            Use when the user asks specific questions about rules/conditions/actions.
            For nearby functional blocks use query_game_context("nearby_blocks").
            For current LMA status see the auto-injected lma_status context.
            """.trim();
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        StringParameter searchType = StringParameter.create()
            .setDescription("What to search: rules, conditions, actions, events");
        searchType.addEnumValues("rules", "conditions", "actions", "events");
        root.addProperties("search_type", searchType);

        root.addProperties("event", StringParameter.create()
            .setDescription("Filter rules by event ID (e.g. maid_attack). Only for search_type=rules"),
            false);

        root.addProperties("enabled", StringParameter.create()
            .setDescription("Filter rules: 'true'=active, 'false'=disabled, 'all'=both (default: all)")
            .addEnumValues("true", "false", "all"),
            false);

        root.addProperties("category", StringParameter.create()
            .setDescription("Filter conditions by category (maid/target/world) or actions by category"),
            false);

        root.addProperties("name", StringParameter.create()
            .setDescription("Search by name/key (substring match)"),
            false);

        root.addProperties("compat", StringParameter.create()
            .setDescription("Filter rules by compat module (e.g. ysm, slashblade, altar, tpm)"),
            false);

        return root;
    }

    @Override public Codec<Params> codec() { return Params.codec(); }

    @Override
    public LLMCallback onCall(String toolCallId, Params p, LLMCallback cb) {
        return switch (p.searchType) {
            case "rules" -> searchRules(p, cb, toolCallId);
            case "conditions" -> searchConditions(p, cb, toolCallId);
            case "actions" -> searchActions(p, cb, toolCallId);
            case "events" -> searchEvents(cb, toolCallId);
            default -> cb.addToolResult(
                "Invalid search_type '" + p.searchType +
                "'. Must be: rules, conditions, actions, events.", toolCallId);
        };
    }

    @Override public String invocationSummary(Params p) {
        return "lma_search { " + p.searchType + " }";
    }

    // ── 搜索实现 ──

    private LLMCallback searchRules(Params p, LLMCallback cb, String toolCallId) {
        List<RuleDef> rules = new ArrayList<>(RuleActionStorage.getRules());

        // 事件过滤
        if (!p.event.isEmpty()) {
            RuleEvent evt = RuleEvent.fromEventId(p.event);
            if (evt == null) {
                List<String> valid = Arrays.stream(RuleEvent.values())
                    .map(RuleEvent::getEventId).toList();
                return cb.addToolResult(
                    ITool.invalidParam("event", valid, "Unknown event: " + p.event), toolCallId);
            }
            rules.removeIf(r -> !r.eventId().equals(p.event));
        }

        // 启用状态过滤
        if ("true".equals(p.enabled)) {
            rules.removeIf(r -> !r.enabled());
        } else if ("false".equals(p.enabled)) {
            rules.removeIf(RuleDef::enabled);
        }

        // 名称过滤
        if (!p.name.isEmpty()) {
            String lc = p.name.toLowerCase();
            rules.removeIf(r -> !r.name().toLowerCase().contains(lc));
        }

        // compat 过滤
        if (!p.compat.isEmpty()) {
            String lc = p.compat.toLowerCase();
            rules.removeIf(r -> r.compat().stream().noneMatch(c -> c.toLowerCase().contains(lc)));
        }

        return formatRulesResult(rules, p, cb, toolCallId);
    }

    private LLMCallback formatRulesResult(List<RuleDef> rules, Params p,
                                           LLMCallback cb, String toolCallId) {
        if (rules.isEmpty()) {
            return cb.addToolResult(buildEmptyMsg("rules", p), toolCallId);
        }

        // 先活跃后禁用
        rules.sort(Comparator.comparing(RuleDef::enabled).reversed()
            .thenComparingInt(RuleDef::id));

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(rules.size()).append(" rules:\n");
        for (RuleDef r : rules) {
            sb.append(String.format("  #%d %s [%s] %s%s priority=%d cooldown=%d",
                r.id(),
                r.enabled() ? "ON" : "OFF",
                r.eventId(),
                r.name(),
                r.compat().isEmpty() ? "" : " compat=" + String.join(",", r.compat()),
                r.priority(),
                r.cooldown()));
            sb.append(String.format(" (%d conditions, %d actions)\n",
                r.conditions().size(), r.actions().size()));
        }

        int total = RuleActionStorage.getRules().size();
        sb.append(String.format("\nTotal: %d rules (%d shown). Use lma_start_task to create tasks, lma_toggle_rule to switch.",
            total, rules.size()));

        return cb.addToolResult(sb.toString(), toolCallId);
    }

    private LLMCallback searchConditions(Params p, LLMCallback cb, String toolCallId) {
        Collection<ICondition> all = ConditionRegistry.getAll();
        List<ICondition> filtered = new ArrayList<>(all);

        if (!p.category.isEmpty()) {
            filtered.removeIf(c -> !c.category().name().equalsIgnoreCase(p.category));
        }
        if (!p.name.isEmpty()) {
            String lc = p.name.toLowerCase();
            filtered.removeIf(c ->
                !c.key().toLowerCase().contains(lc) &&
                !c.displayName().toLowerCase().contains(lc));
        }

        if (filtered.isEmpty()) {
            return cb.addToolResult(buildEmptyMsg("conditions", p), toolCallId);
        }

        filtered.sort(Comparator.comparing(c -> c.category().name()));

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(filtered.size()).append(" conditions:\n");
        String lastCat = "";
        for (ICondition c : filtered) {
            if (!c.category().name().equals(lastCat)) {
                lastCat = c.category().name();
                sb.append("  [").append(lastCat).append("]\n");
            }
            sb.append(String.format("    %-30s (%s) : %s\n",
                c.key(), c.valueType().name(), c.displayName()));
            if (!c.params().isEmpty()) {
                sb.append("      params: ");
                sb.append(String.join(", ", c.params().stream()
                    .map(tp -> tp.name() + "(" + tp.getClass().getSimpleName() + ")")
                    .toList()));
                sb.append("\n");
            }
        }

        return cb.addToolResult(sb.toString(), toolCallId);
    }

    private LLMCallback searchActions(Params p, LLMCallback cb, String toolCallId) {
        Collection<IAction> all = ActionRegistry.getAll();
        List<IAction> filtered = new ArrayList<>(all);

        if (!p.category.isEmpty()) {
            filtered.removeIf(a -> !a.category().name().equalsIgnoreCase(p.category));
        }
        if (!p.name.isEmpty()) {
            String lc = p.name.toLowerCase();
            filtered.removeIf(a ->
                !a.id().toLowerCase().contains(lc) &&
                !a.displayName().toLowerCase().contains(lc));
        }

        if (filtered.isEmpty()) {
            return cb.addToolResult(buildEmptyMsg("actions", p), toolCallId);
        }

        filtered.sort(Comparator.comparing(a -> a.category().name()));

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(filtered.size()).append(" actions:\n");
        String lastCat = "";
        for (IAction a : filtered) {
            if (!a.category().name().equals(lastCat)) {
                lastCat = a.category().name();
                sb.append("  [").append(lastCat).append("]\n");
            }
            sb.append(String.format("    %-30s : %s\n", a.id(), a.displayName()));
            if (!a.params().isEmpty()) {
                sb.append("      params: ");
                sb.append(String.join(", ", a.params().stream()
                    .map(tp -> tp.name() + "(" + tp.getClass().getSimpleName() + ")")
                    .toList()));
                sb.append("\n");
            }
        }

        return cb.addToolResult(sb.toString(), toolCallId);
    }

    private LLMCallback searchEvents(LLMCallback cb, String toolCallId) {
        var events = RuleEvent.values();
        StringBuilder sb = new StringBuilder();
        sb.append("Available events (").append(events.length).append("):\n");
        for (RuleEvent e : events) {
            sb.append(String.format("  %-35s %s %s\n",
                e.getEventId(), e.getDisplayName(),
                e.isCancellable() ? "(cancellable)" : ""));
        }
        return cb.addToolResult(sb.toString(), toolCallId);
    }

    // ── 工具 ──

    private String buildEmptyMsg(String type, Params p) {
        StringBuilder sb = new StringBuilder("No ").append(type).append(" found");
        List<String> filters = new ArrayList<>();
        if (!p.event.isEmpty()) filters.add("event=" + p.event);
        if (!"all".equals(p.enabled)) filters.add("enabled=" + p.enabled);
        if (!p.category.isEmpty()) filters.add("category=" + p.category);
        if (!p.name.isEmpty()) filters.add("name=" + p.name);
        if (!p.compat.isEmpty()) filters.add("compat=" + p.compat);
        if (!filters.isEmpty()) {
            sb.append(" with filters: ").append(String.join(", ", filters));
        }
        return sb.toString();
    }
}

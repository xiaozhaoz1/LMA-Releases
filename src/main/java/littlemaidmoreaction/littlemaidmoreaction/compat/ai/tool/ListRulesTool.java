package littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;

import java.util.List;

/**
 * AI 工具: 列出当前所有规则。LLM 可根据此信息决定修改或删除规则。
 */
public final class ListRulesTool implements ITool<String> {

    private static final Codec<String> CODEC = Codec.STRING.optionalFieldOf("filter", "").codec();

    @Override public String id() { return "lma_list_rules"; }

    @Override
    public String summary(EntityMaid maid) {
        return "List all current LMA rules. Returns id, name, event, enabled status, " +
               "condition count, and action count for each rule.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties("filter", StringParameter.create()
            .setDescription("Optional filter: event name substring or rule name substring"), false);
        return root;
    }

    @Override public Codec<String> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, String filter, LLMCallback cb) {
        List<RuleDef> rules = RuleActionStorage.getRules();
        if (filter != null && !filter.isBlank()) {
            String f = filter.toLowerCase();
            rules = rules.stream().filter(r ->
                r.name().toLowerCase().contains(f) || r.eventId().toLowerCase().contains(f)
            ).toList();
        }
        if (rules.isEmpty()) {
            return cb.addToolResult("No rules found" +
                (filter != null && !filter.isBlank() ? " matching '" + filter + "'" : "") + ".", toolCallId);
        }
        StringBuilder sb = new StringBuilder("Rules (" + rules.size() + "):\n");
        for (RuleDef r : rules) {
            sb.append(String.format("#%d [%s] '%s' event=%s cond=%d act=%d cooldown=%d pri=%d\n",
                r.id(), r.enabled() ? "ON" : "OFF", r.name(), r.eventId(),
                r.conditions().size(), r.actions().size(), r.cooldown(), r.priority()));
        }
        return cb.addToolResult(sb.toString().trim(), toolCallId);
    }

    @Override public String invocationSummary(String f) { return "lma_list_rules"; }
}

package littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;

import java.util.ArrayList;
import java.util.Optional;

/**
 * AI 工具: 删除指定规则。需要 rule_id — 先用 lma_list_rules 获取 ID。
 */
public final class DeleteRuleTool implements ITool<Integer> {

    private static final Codec<Integer> CODEC = Codec.INT.fieldOf("rule_id").codec();

    @Override public String id() { return "lma_delete_rule"; }

    @Override
    public String summary(EntityMaid maid) {
        return "Delete a rule by its ID. Get IDs from lma_list_rules first.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties("rule_id", IntegerParameter.create()
            .setDescription("Numeric rule ID to delete"));
        return root;
    }

    @Override public Codec<Integer> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Integer ruleId, LLMCallback cb) {
        Optional<RuleDef> target = RuleActionStorage.getRules().stream()
            .filter(r -> r.id() == ruleId).findFirst();

        if (target.isEmpty()) {
            return cb.addToolResult("Rule #" + ruleId + " not found. Use lma_list_rules to see IDs.", toolCallId);
        }

        RuleDef deleted = target.get();
        cb.runOnServerThread(() -> {
            var list = new ArrayList<>(RuleActionStorage.getRules());
            list.removeIf(r -> r.id() == ruleId);
            RuleActionStorage.replaceRules(list);
        });

        return cb.addToolResult("Deleted rule #" + ruleId + " '" + deleted.name() + "'", toolCallId);
    }

    @Override public String invocationSummary(Integer id) { return "lma_delete_rule { #" + id + " }"; }
}

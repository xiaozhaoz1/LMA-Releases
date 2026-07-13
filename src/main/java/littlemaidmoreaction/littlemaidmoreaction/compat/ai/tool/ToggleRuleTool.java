package littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;

import java.util.ArrayList;
import java.util.Optional;

/**
 * AI 工具: 启用/禁用规则。
 */
public final class ToggleRuleTool implements ITool<ToggleRuleTool.Params> {

    public record Params(int ruleId, boolean enabled) {
        private static volatile Codec<Params> CODEC;
    static Codec<Params> paramsCodec() {
      if (CODEC == null) CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("rule_id").forGetter(Params::ruleId),
            Codec.BOOL.fieldOf("enabled").forGetter(Params::enabled)
        ).apply(i, Params::new));
      return CODEC;
    }
    }

    @Override public String id() { return "lma_toggle_rule"; }

    @Override
    public String summary(EntityMaid maid) {
        return "Enable or disable a rule by ID. Useful for temporarily turning off rules.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties("rule_id", IntegerParameter.create()
            .setDescription("Numeric rule ID"));
        root.addProperties("enabled", BoolParameter.create()
            .setDescription("true = enable, false = disable"));
        return root;
    }

    @Override public Codec<Params> codec() { return Params.paramsCodec(); }

    @Override
    public LLMCallback onCall(String toolCallId, Params p, LLMCallback cb) {
        Optional<RuleDef> target = RuleActionStorage.getRules().stream()
            .filter(r -> r.id() == p.ruleId).findFirst();

        if (target.isEmpty()) {
            return cb.addToolResult("Rule #" + p.ruleId + " not found.", toolCallId);
        }

        cb.runOnServerThread(() -> {
            var list = new ArrayList<>(RuleActionStorage.getRules());
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id() == p.ruleId) {
                    RuleDef old = list.get(i);
                    list.set(i, new RuleDef(old.id(), old.name(), p.enabled, old.eventId(),
                        old.chance(), old.cooldown(), old.priority(), old.matchMode(),
                        old.conditions(), old.actions(), old.compat()));
                    break;
                }
            }
            RuleActionStorage.replaceRules(list);
        });

        return cb.addToolResult("Rule #" + p.ruleId + " now " + (p.enabled ? "ON" : "OFF"), toolCallId);
    }

    @Override public String invocationSummary(Params p) {
        return "lma_toggle_rule { #" + p.ruleId + " → " + p.enabled + " }";
    }
}

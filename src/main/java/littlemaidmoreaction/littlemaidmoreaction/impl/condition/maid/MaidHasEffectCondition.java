package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
/** 检查女仆是否有指定药水效果。参数 effect_id 为效果注册名(如 minecraft:regeneration)。 */
@RuleCondition
public final class MaidHasEffectCondition implements ICondition {
    @Override public String key() { return "maid_has_effect"; }
    @Override public String displayName() { return "女仆效果"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override
    public List<TypedParam<?>> params() {
        return List.of(new TypedParam.StringParam("effect_id", "效果ID", "minecraft:regeneration"));
    }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        String effectId = rawParams.getOrDefault("effect_id", "minecraft:regeneration");
        for (var eff : ctx.maid().getActiveEffects()) {
            ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(eff.getEffect());
            if (key != null && key.toString().equals(effectId)) return key.toString();
        }
        return "none";
    }
}

package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** 是否具有特定 SpecialEffect — hasSpecialEffect(ResourceLocation)。参数: effect_id。 */
@RuleCondition
public final class SlashbladeSpecialEffectCondition implements ICondition {
    @Override public String key() { return "slashblade_special_effect"; }
    @Override public String displayName() { return "拔刀剑特殊效果"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public List<TypedParam<?>> params() { return List.of(new TypedParam.StringParam("effect_id", "效果ID", "")); }
    @Override public String evaluate(RuleContext c, Map<String, String> r) {
        String id = r.getOrDefault("effect_id", "");
        if (id.isEmpty() || !(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "false";
        return c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> String.valueOf(s.hasSpecialEffect(ResourceLocation.tryParse(id)))).orElse("false");
    }
}

package littlemaidmoreaction.littlemaidmoreaction.impl.condition.target;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
/**
 * 目标实体类型 — 返回路径部分（不含命名空间）以便与用户输入的简写匹配。
 * 例如 {@code minecraft:zombie} → {@code zombie}，用户可用 {@code :=: zombie}。
 */
@RuleCondition
public final class TargetTypeCondition implements ICondition {
    @Override public String key() { return "target_type"; }
    @Override public String displayName() { return "目标类型"; }
    @Override public ConditionCategory category() { return ConditionCategory.TARGET; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        if (ctx.target() == null) return "none";
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(ctx.target().getType());
        return key != null ? key.getPath() : "none";
    }
}


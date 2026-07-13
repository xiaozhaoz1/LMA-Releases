package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
@RuleCondition
public final class WorldBiomeCondition implements ICondition {
    @Override public String key() { return "world_biome"; }
    @Override public String displayName() { return "生物群系"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { var h=ctx.maid().level().getBiome(ctx.maid().blockPosition()); ResourceLocation k=ForgeRegistries.BIOMES.getKey(h.value()); return k!=null?k.getPath():"unknown"; }
}

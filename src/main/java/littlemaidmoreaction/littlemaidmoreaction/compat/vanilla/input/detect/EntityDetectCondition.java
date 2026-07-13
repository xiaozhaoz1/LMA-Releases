package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.detect;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

@RuleCondition
public final class EntityDetectCondition extends AbstractDetectCondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("entity_id", "实体ID", "minecraft:zombie"),
        new TypedParam.IntParam("range", "搜索范围", 10),
        new TypedParam.IntParam("vertical", "垂直范围", 4)
    );

    private EntityType<?> targetType = null;

    @Override public String key() { return "entity_nearby"; }
    @Override public String displayName() { return "附近有实体"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override protected boolean searchBlocks() { return false; }
    @Override protected boolean searchEntities() { return true; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        String entityId = rawParams.getOrDefault("entity_id", "minecraft:zombie");
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        targetType = rl != null ? ForgeRegistries.ENTITY_TYPES.getValue(rl) : null;
        if (targetType == null) return "false";
        return super.evaluate(ctx, rawParams);
    }

    @Override
    protected boolean matchEntity(Entity entity, EntityMaid maid) {
        return targetType != null && entity.getType() == targetType && entity.isAlive();
    }
}

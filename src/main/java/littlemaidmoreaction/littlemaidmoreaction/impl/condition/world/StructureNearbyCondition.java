package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/**
 * 检测附近结构 (v11 P3)。
 *
 * <p>使用 MC 原版 Structure 系统，检测女仆周围是否有指定结构。
 * 常见结构: minecraft:village, minecraft:stronghold, minecraft:mineshaft,
 * minecraft:desert_pyramid, minecraft:jungle_temple, minecraft:swamp_hut,
 * minecraft:pillager_outpost, minecraft:igloo, minecraft:ocean_ruin,
 * minecraft:shipwreck, minecraft:buried_treasure, minecraft:ruined_portal,
 * minecraft:bastion_remnant, minecraft:nether_fortress, minecraft:end_city
 *
 * <p>条件值类型 BOOL — 返回 true/false。
 */
@RuleCondition
public final class StructureNearbyCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("structure", "结构ID", "minecraft:village"),
        new TypedParam.IntParam("range", "搜索范围(块)", 200)
    );

    @Override public String key() { return "structure_nearby"; }
    @Override public String displayName() { return "附近结构"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (!(maid.level() instanceof ServerLevel sl)) return "false";

        String structureId = rawParams.getOrDefault("structure", "minecraft:village");
        int range = parseInt(rawParams.get("range"), 200);

        ResourceLocation key = ResourceLocation.tryParse(structureId);
        if (key == null) return "false";

        var registry = sl.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, key);
        var holderOpt = registry.getHolder(structureKey);
        if (holderOpt.isEmpty()) return "false";

        HolderSet<Structure> holderSet = HolderSet.direct(holderOpt.get());

        BlockPos center = maid.blockPosition();
        var result = sl.getChunkSource().getGenerator()
            .findNearestMapStructure(sl, holderSet, center, range, false);

        return result != null ? "true" : "false";
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}

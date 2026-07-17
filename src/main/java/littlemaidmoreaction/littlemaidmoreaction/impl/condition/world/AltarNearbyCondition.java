package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.detect.AbstractDetectCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 附近有可用祭坛 (v11 — 从 compat 迁移到 impl)。
 *
 * <p>检测 TileEntityAltar 且 isCanPlaceItem()=true (多方块结构已激活)。
 * TLM 是 LMA 的必需依赖，非可选兼容模块。</p>
 */
@RuleCondition
public final class AltarNearbyCondition extends AbstractDetectCondition {

    @Override public String key() { return "altar_nearby"; }
    @Override public String displayName() { return "附近有祭坛"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }

    @Override
    protected boolean matchAt(Level level, BlockPos pos, EntityMaid maid) {
        var te = level.getBlockEntity(pos);
        return te instanceof TileEntityAltar altar && altar.isCanPlaceItem();
    }
}

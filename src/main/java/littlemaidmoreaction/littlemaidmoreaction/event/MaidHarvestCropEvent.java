package littlemaidmoreaction.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.Event;

/**
 * 女仆收获作物事件 — 在 {@code AutoCropHandler.harvest()} 中触发。
 *
 * <p>非取消事件（cancellable=false），仅在自动匹配启用时触发。
 * 规则可订阅此事件来响应女仆的每次收获行为。</p>
 */
public final class MaidHarvestCropEvent extends Event {

    private final EntityMaid maid;
    private final BlockPos cropPos;
    private final Block cropBlock;

    public MaidHarvestCropEvent(EntityMaid maid, BlockPos cropPos, Block cropBlock) {
        this.maid = maid;
        this.cropPos = cropPos;
        this.cropBlock = cropBlock;
    }

    public EntityMaid getMaid() { return maid; }
    public BlockPos getCropPos() { return cropPos; }
    public Block getCropBlock() { return cropBlock; }
}

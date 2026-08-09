package com.github.xiaozhaoz1.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.Event;
//?} else {
import net.neoforged.bus.api.Event;
//?}

/**
 * 女仆收割作物事件 (v72 Phase 4 恢复) — 通知型, 不可取消。
 *
 * <p>由 {@link com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.AutoCropHandler#harvest}
 * 无条件 post (恢复 v68 裁撤前的旧逻辑); 事件桥 MaidHarvestSignalBridge 消费。
 */
public class MaidHarvestCropEvent extends Event {

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

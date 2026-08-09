package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.SlotLayout;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.block.FurnaceOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** v30: 枚举状态机 + SlotLayout */
public final class FurnaceExecute {
    private FurnaceExecute() {}

    enum Phase {
        COLLECT_RESULT, ADD_INPUT, ADD_FUEL;
        private static final Phase[] VALUES = values();
        static Phase fromOrdinal(int ord) {
            if (ord < 0 || ord >= VALUES.length) return COLLECT_RESULT;
            return VALUES[ord];
        }
    }

    public static boolean execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                   String inputItemId, SlotLayout slots) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/Furnace] no furnace at {}", pos.toShortString());
            return false;
        }

        var data = maid.getPersistentData();
        Phase phase = Phase.fromOrdinal(data.getInt("lma_furnace_phase"));
        boolean meaningful = false;

        switch (phase) {
            case COLLECT_RESULT -> {
                meaningful = FurnaceOutput.collectResult(furnace, maid, slots);
                setPhase(data, Phase.ADD_INPUT);
            }
            case ADD_INPUT -> {
                meaningful = FurnaceOutput.addInput(furnace, maid, inputItemId, slots);
                setPhase(data, Phase.ADD_FUEL);
            }
            case ADD_FUEL -> {
                meaningful = FurnaceOutput.addFuel(furnace, maid, inputItemId, slots);
                setPhase(data, Phase.COLLECT_RESULT);
            }
        }

        LittleMaidMoreAction.LOGGER.info("[LMA/Furnace] phase={} meaningful={} input={}", phase, meaningful, inputItemId);
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager.heartbeat(maid, world.getGameTime());
        return meaningful;
    }

    private static void setPhase(CompoundTag data, Phase phase) {
        data.putInt("lma_furnace_phase", phase.ordinal());
    }
}

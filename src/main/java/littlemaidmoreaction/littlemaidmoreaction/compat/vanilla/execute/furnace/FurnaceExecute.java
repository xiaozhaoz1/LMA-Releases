package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.furnace;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.FurnaceSlotMapping;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.block.FurnaceOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** v29.1: 熔炉编排 — 枚举状态机, 支持自定义栏位映射 */
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

    /** @return true if meaningful work was performed */
    public static boolean execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                   String inputItemId, FurnaceSlotMapping slots) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return false;

        var data = maid.getPersistentData();
        Phase phase = Phase.fromOrdinal(data.getInt("lma_furnace_phase"));
        boolean meaningful = false;

        switch (phase) {
            case COLLECT_RESULT -> {
                meaningful = FurnaceOutput.collectResult(furnace, maid, slots);
                if (!meaningful) setPhase(data, Phase.ADD_INPUT);
            }
            case ADD_INPUT -> {
                if (furnace.getItem(slots.input()).isEmpty())
                    meaningful = FurnaceOutput.addInput(furnace, maid, inputItemId, slots);
                setPhase(data, Phase.ADD_FUEL);
            }
            case ADD_FUEL -> {
                if (furnace.getItem(slots.fuel()).isEmpty())
                    meaningful = FurnaceOutput.addFuel(furnace, maid, inputItemId, slots);
                setPhase(data, Phase.COLLECT_RESULT);
            }
        }

        data.putLong("lma_flow_tick", world.getGameTime());
        return meaningful;
    }

    private static void setPhase(CompoundTag data, Phase phase) {
        data.putInt("lma_furnace_phase", phase.ordinal());
    }
}

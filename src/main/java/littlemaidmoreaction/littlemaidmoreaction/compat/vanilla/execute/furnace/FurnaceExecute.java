package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.furnace;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.block.FurnaceOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** v23: 熔炉编排 — 状态机驱动, 委托 FurnaceOutput 执行 */
public final class FurnaceExecute {
    private FurnaceExecute() {}

    public static void execute(ServerLevel world, EntityMaid maid, BlockPos pos, String inputItemId) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;

        // State 1: 取产物
        if (FurnaceOutput.collectResult(furnace, maid)) return;

        // State 2: 加材料
        FurnaceOutput.addInput(furnace, maid, inputItemId);

        // State 3: 加燃料(排除原料)
        FurnaceOutput.addFuel(furnace, maid, inputItemId);

        // State 4: WAIT — 更新 tick 防 TaskEngine 超时
        maid.getPersistentData().putLong("lma_flow_tick", world.getGameTime());
    }
}

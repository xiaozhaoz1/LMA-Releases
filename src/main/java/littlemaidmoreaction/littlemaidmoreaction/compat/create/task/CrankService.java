package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 手摇曲柄 IO — 搜索 + 激活。
 *
 * <p>原子化 input→compute→output 模式:
 * <br>Input:  findCrank — 螺旋搜索 HandCrankBlock
 * <br>Output: crank    — 调用 HandCrankBlockEntity.turn()
 */
public final class CrankService {
    private static final int SEARCH_RANGE = 3;

    private CrankService() {}

    /** 在周围 3 格范围内搜索最近的曲柄 */
    public static BlockPos findCrank(Level level, BlockPos center) {
        for (int dr = 0; dr <= SEARCH_RANGE; dr++) {
            for (int dx = -dr; dx <= dr; dx++) {
                for (int dz = -dr; dz <= dr; dz++) {
                    if (Math.abs(dx) != dr && Math.abs(dz) != dr) continue;
                    BlockPos pos = center.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = pos.offset(0, dy, 0);
                        BlockState state = level.getBlockState(p);
                        if (state.getBlock() instanceof HandCrankBlock) {
                            return p.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 激活曲柄 — 调用 turn() 模拟玩家右键，服务器端 */
    public static boolean crank(Level level, BlockPos pos) {
        if (level.isClientSide) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HandCrankBlockEntity crankBE) {
            crankBE.turn(false);
            return true;
        }
        return false;
    }
}

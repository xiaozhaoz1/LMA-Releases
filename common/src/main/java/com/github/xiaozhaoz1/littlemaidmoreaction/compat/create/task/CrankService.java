package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task;

import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 手摇曲柄 IO — 搜索 + 激活 (v73 双平台)。
 *
 * <p>原子化 input→compute→output 模式:
 * <br>Input:  findCrank — 螺旋搜索 HandCrankBlock
 * <br>Output: crank    — 调用 HandCrankBlockEntity.turn() (javap 实证双平台类方法)
 *
 * <p>v73: ponder 接口 jar 已补 compileOnly (从 create jarjar 提取) — 双平台类型引用可用。
 */
public final class CrankService {
    private static final int SEARCH_RANGE = 3;

    private CrankService() {}

    /**
     * 螺旋序收集 ≤max 个曲柄 (近→远) — 参数化 findCrank 逻辑。
     * 用于 running_belt 顺带摇曲柄 (周围 2 格内, 最多 2 个)。
     */
    public static java.util.List<BlockPos> findCranks(Level level, BlockPos center, int range, int max) {
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        for (int dr = 0; dr <= range && found.size() < max; dr++) {
            for (int dx = -dr; dx <= dr && found.size() < max; dx++) {
                for (int dz = -dr; dz <= dr && found.size() < max; dz++) {
                    if (Math.abs(dx) != dr && Math.abs(dz) != dr) continue;
                    BlockPos pos = center.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = pos.offset(0, dy, 0);
                        BlockState state = level.getBlockState(p);
                        if (state.getBlock() instanceof HandCrankBlock) {
                            found.add(p.immutable());
                            if (found.size() >= max) break;
                        }
                    }
                }
            }
        }
        return found;
    }

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

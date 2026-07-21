package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlock;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * 女仆动力 IO — 搜索动能方块 + 提供/停止动力。
 *
 * <p>女仆模拟创造马达，向单个最近动能方块提供转速。
 * <br>通过直接操作 KineticBlockEntity 的拓扑网络实现。
 */
public final class PowerService {
    /** 默认基础 RPM，可通过规则参数覆盖，不硬编码 */
    public static final float DEFAULT_RPM = 96f;
    private static final int SEARCH_RANGE = 3;

    private static final Set<Class<? extends Block>> TARGET_BLOCKS = Set.of(
        MillstoneBlock.class,
        CogWheelBlock.class,
        MechanicalMixerBlock.class
    );

    private PowerService() {}

    /** 在周围 3 格搜索最近的目标动能方块 */
    public static BlockPos findTarget(Level level, BlockPos center) {
        for (int dr = 0; dr <= SEARCH_RANGE; dr++) {
            for (int dx = -dr; dx <= dr; dx++) {
                for (int dz = -dr; dz <= dr; dz++) {
                    if (Math.abs(dx) != dr && Math.abs(dz) != dr) continue;
                    BlockPos pos = center.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = pos.offset(0, dy, 0);
                        BlockState state = level.getBlockState(p);
                        if (isTargetBlock(state.getBlock())) {
                            BlockEntity be = level.getBlockEntity(p);
                            if (be instanceof KineticBlockEntity) {
                                return p.immutable();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 检查是否为目标方块类型 */
    public static boolean isTargetBlock(Block block) {
        for (Class<? extends Block> clazz : TARGET_BLOCKS) {
            if (clazz.isInstance(block)) return true;
        }
        return false;
    }

    /**
     * 向目标方块提供动力。
     *
     * <p>核心逻辑: detach → setSpeed → setNetwork → attach,
     * 模拟 GeneratingKineticBlockEntity.updateGeneratedRotation() 的行为。
     */
    public static boolean providePower(Level level, BlockPos pos, float rpm) {
        if (level.isClientSide || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof KineticBlockEntity kbe)) return false;

        float current = kbe.getSpeed();
        if (Math.abs(current - rpm) < 0.01f && current != 0) return true;

        kbe.detachKinetics();
        kbe.setSpeed(rpm);
        kbe.setNetwork(pos.asLong());
        kbe.attachKinetics();
        return true;
    }

    /** 停止供能 — 断网归零，用于任务清理 */
    public static void stopPower(Level level, BlockPos pos) {
        if (level.isClientSide || pos == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof KineticBlockEntity kbe)) return;
        if (kbe.getSpeed() == 0) return;
        kbe.detachKinetics();
        kbe.setSpeed(0);
        kbe.setNetwork(null);
    }
}

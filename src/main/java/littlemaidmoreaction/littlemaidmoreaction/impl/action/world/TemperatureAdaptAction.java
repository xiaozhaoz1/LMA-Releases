package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.world.WorldStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * 温度感知 (v35.4) — 炎热找水源, 寒冷找篝火。
 */
@RuleAction
public final class TemperatureAdaptAction implements IAction {

    private static final BiPredicate<BlockPos, BlockState> WATER = (p, s) -> s.is(Blocks.WATER);
    private static final BiPredicate<BlockPos, BlockState> HEAT = (p, s) ->
        s.is(Blocks.FIRE) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE) ||
        s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK);

    /** 在目标方块周围找安全站立点 (solid, 不伤人) */
    private static BlockPos findSafeAdjacent(ServerLevel level, BlockPos target) {
        // 先检查目标本身是否安全(篝火/灵魂篝火本身就是安全的)
        BlockState ts = level.getBlockState(target);
        if (ts.is(Blocks.CAMPFIRE) || ts.is(Blocks.SOUL_CAMPFIRE)) {
            // 篝火: 找旁边地面站立
            for (BlockPos p : BlockPos.betweenClosed(target.offset(-1, 0, -1), target.offset(1, 0, 1))) {
                if (p.equals(target)) continue;
                BlockState s = level.getBlockState(p);
                BlockState below = level.getBlockState(p.below());
                if (s.isAir() && below.isSolid()) return p.immutable();
            }
        }
        // 水源: 找旁边地面
        if (ts.is(Blocks.WATER)) {
            for (BlockPos p : BlockPos.betweenClosed(target.offset(-1, 0, -1), target.offset(1, 0, 1))) {
                if (p.equals(target)) continue;
                BlockState s = level.getBlockState(p);
                BlockState below = level.getBlockState(p.below());
                if (s.isAir() && below.isSolid()) return p.immutable();
            }
        }
        // 危险方块(火/熔岩/岩浆块): 找2格外安全点
        for (int r = 1; r <= 3; r++) {
            for (BlockPos p : BlockPos.betweenClosed(target.offset(-r, 0, -r), target.offset(r, 0, r))) {
                BlockState s = level.getBlockState(p);
                BlockState below = level.getBlockState(p.below());
                if (s.isAir() && below.isSolid() && !p.equals(target)) return p.immutable();
            }
        }
        return null;
    }

    @Override public String id() { return "temperature_adapt"; }
    @Override public String displayName() { return "温度感知"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        EntityMaid m = ctx.maid();
        if (!(m.level() instanceof ServerLevel level)) return;

        // 1. 读温度
        float temp = level.getBiome(m.blockPosition()).value().getBaseTemperature();

        // 2. 算行为
        BiPredicate<BlockPos, BlockState> target;
        if (temp > 1.5f) target = WATER;
        else if (temp < 0.2f) target = HEAT;
        else return;

        // 3. 搜目标方块
        var matches = BlockSearch.findBlocksInRange(level, m.blockPosition(),
            (int) m.getRestrictRadius(), target);
        if (matches.isEmpty()) return;

        // 4. 找目标旁边的安全站立点
        BlockPos safePos = findSafeAdjacent(level, matches.get(0).pos());
        if (safePos == null) return;

        // 5. 输出导航
        if (safePos.distSqr(m.blockPosition()) > 4) {
            BehaviorUtils.setWalkAndLookTargetMemories(m, safePos, 1.0f, 2);
        }
    }
}

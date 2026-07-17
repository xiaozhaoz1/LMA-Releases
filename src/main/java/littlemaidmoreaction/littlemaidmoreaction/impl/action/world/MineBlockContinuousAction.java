package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer.FakePlayerManager;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer.LmaPlayerSimulator;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/**
 * 持续挖掘方块 — 用 FakePlayer 模拟玩家持续左键挖掘。
 *
 * <p>与 {@link BreakBlockAction} 不同, 此动作:
 * <ul>
 *   <li>通过 {@link FakePlayerManager} 注册持续任务</li>
 *   <li>每 ServerTick 累积挖掘进度 (参考 Create Deployer PUNCH 模式)</li>
 *   <li>方块被破坏后自动停止并收集掉落到女仆背包</li>
 * </ul>
 */
@RuleAction
public final class MineBlockContinuousAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("offset_x", "X偏移", 0),
        new TypedParam.IntParam("offset_y", "Y偏移", -1),
        new TypedParam.IntParam("offset_z", "Z偏移", 0)
    );

    @Override public String id() { return "mine_block_continuous"; }
    @Override public String displayName() { return "持续挖掘方块"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel sw)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        var maid = ctx.maid();
        BlockPos pos = maid.blockPosition().offset(
            p.getInt("offset_x"), p.getInt("offset_y"), p.getInt("offset_z"));

        if (sw.getBlockState(pos).isAir()) return;

        if (FakePlayerManager.hasTask(maid)) {
            FakePlayerManager.stop(maid);
        }

        // 注册持续挖掘 — FakePlayerManager.onServerTick 每 tick 驱动
        FakePlayerManager.start(maid, pos, Direction.UP,
            LmaPlayerSimulator.Mode.LEFT_CLICK_CONTINUOUS);
    }
}

package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer.LmaFakePlayer;
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
 * 破坏方块 — 用 FakePlayer 模拟玩家左键一次性破坏。
 *
 * <p>v38: 改用 {@link LmaFakePlayer} + {@link LmaPlayerSimulator}，
 * 替代旧的 WorldOutput.breakBlockAt() 直接调用。
 * 现在通过 Player 上下文正确触发 Forge 事件、掉落收集到女仆背包、工具耐久扣除。</p>
 */
@RuleAction
public final class BreakBlockAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("offset_x", "X偏移", 0),
        new TypedParam.IntParam("offset_y", "Y偏移", -1),
        new TypedParam.IntParam("offset_z", "Z偏移", 0),
        new TypedParam.BoolParam("drop_items", "掉落物品", true)
    );

    @Override public String id() { return "break_block"; }
    @Override public String displayName() { return "破坏方块"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel sl)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        BlockPos pos = ctx.maid().blockPosition().offset(
            p.getInt("offset_x"), p.getInt("offset_y"), p.getInt("offset_z"));

        var fp = new LmaFakePlayer(sl, ctx.maid(), pos);
        try {
            LmaPlayerSimulator.simulate(fp, sl, pos, Direction.UP, LmaPlayerSimulator.Mode.LEFT_CLICK_ONCE);
            if (p.getBool("drop_items")) LmaPlayerSimulator.syncHandToMaid(fp);
        } finally {
            LmaPlayerSimulator.cleanup(fp, sl);
        }
    }
}

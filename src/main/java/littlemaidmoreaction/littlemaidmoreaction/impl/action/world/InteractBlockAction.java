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
 * 交互方块 — 用 FakePlayer 模拟玩家右键。
 *
 * <p>v38: 改用 {@link LmaFakePlayer} + {@link LmaPlayerSimulator}，
 * 替代旧的 WorldOutput.interactBlock(null) 模式。
 * 支持打开 GUI、放置方块、使用物品等需要 Player 上下文的操作。</p>
 */
@RuleAction
public final class InteractBlockAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("offset_x", "X偏移", 0),
        new TypedParam.IntParam("offset_y", "Y偏移", 0),
        new TypedParam.IntParam("offset_z", "Z偏移", 1),
        new TypedParam.StringParam("face", "面", "up")
    );

    @Override public String id() { return "interact_block"; }
    @Override public String displayName() { return "交互方块"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel sw)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        BlockPos pos = ctx.maid().blockPosition().offset(p.getInt("offset_x"), p.getInt("offset_y"), p.getInt("offset_z"));
        Direction face = parseFace(p.getString("face"));

        var fp = new LmaFakePlayer(sw, ctx.maid(), pos);
        try {
            LmaPlayerSimulator.simulate(fp, sw, pos, face, LmaPlayerSimulator.Mode.RIGHT_CLICK_ONCE);
            LmaPlayerSimulator.syncHandToMaid(fp);
        } finally {
            LmaPlayerSimulator.cleanup(fp, sw);
        }
    }

    private static Direction parseFace(String face) {
        return switch (face != null ? face.toLowerCase() : "up") {
            case "down" -> Direction.DOWN; case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH; case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> Direction.UP;
        };
    }
}

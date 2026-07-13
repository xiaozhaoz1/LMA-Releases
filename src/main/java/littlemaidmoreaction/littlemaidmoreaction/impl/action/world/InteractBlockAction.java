package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Map;

/** 交互方块 — 委托给 {@link WorldOutput#interactBlock}. */
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
        var p = ParamExtractor.from(raw, PARAMS);
        BlockPos pos = ctx.maid().blockPosition().offset(p.getInt("offset_x"), p.getInt("offset_y"), p.getInt("offset_z"));
        Direction face = parseFace(p.getString("face"));
        WorldOutput.interactBlock(ctx.maid().level(), pos, face);
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

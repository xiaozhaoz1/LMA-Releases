package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

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
        WorldOutput.breakBlockAt(sl, pos, p.getBool("drop_items"));
    }
}

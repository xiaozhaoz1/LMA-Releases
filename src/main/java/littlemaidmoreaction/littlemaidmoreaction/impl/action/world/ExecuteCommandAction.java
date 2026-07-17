package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

/** 执行指令 — 委托给 {@link WorldOutput#executeCommand}. */
@RuleAction
public final class ExecuteCommandAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("command","命令",""), new TypedParam.SelectParam("as","执行者","maid",List.of("maid","owner","server")),
        new TypedParam.SelectParam("at","位置","maid",List.of("maid","target")));
    @Override public String id() { return "execute_command"; }
    @Override public String displayName() { return "执行指令"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel sl)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        String cmd = p.getString("command"); if (cmd.isEmpty()) return;
        WorldOutput.executeCommand(sl, ctx.maid(), ctx.target(), cmd, p.getString("as"), p.getString("at"));
    }
}

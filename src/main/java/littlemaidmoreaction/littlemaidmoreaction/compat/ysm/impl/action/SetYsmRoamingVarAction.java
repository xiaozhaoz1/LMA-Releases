package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.ysm.output.YsmWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 设置YSM漫游变量 — 委托给 {@link YsmWriter#setRoamingVar}. */
@RuleAction
public final class SetYsmRoamingVarAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("var_name", "变量名", ""),
        new TypedParam.DoubleParam("value", "数值", 1.0)
    );
    @Override public String id() { return "set_ysm_roaming_var"; }
    @Override public String displayName() { return "设置YSM漫游变量"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        String n = raw.getOrDefault("var_name", "");
        if (!n.isEmpty()) YsmWriter.setRoamingVar(ctx.maid(), n,
            Float.parseFloat(raw.getOrDefault("value", "1.0")));
    }
}

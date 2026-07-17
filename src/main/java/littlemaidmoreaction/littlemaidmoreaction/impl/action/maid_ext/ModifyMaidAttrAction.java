package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid_ext;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 修改女仆属性 — 委托给 {@link MaidStateWriter#modifyAttribute}. */
@RuleAction
public final class ModifyMaidAttrAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("attribute","属性","attack_damage")
    );
    @Override public String id() { return "modify_maid_attr"; }
    @Override public String displayName() { return "修改女仆属性"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        String key = raw.getOrDefault("attribute","attack_damage");
        String mode = raw.getOrDefault("mode","add");
        double amount = Double.parseDouble(raw.getOrDefault("amount", raw.getOrDefault("value","1.0")));
        MaidStateWriter.modifyAttribute(ctx.maid(), key, mode, amount);
    }
}

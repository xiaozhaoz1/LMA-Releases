package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.ysm.output.YsmWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 设置YSM模型 — 委托给 {@link YsmWriter#setModel}. */
@RuleAction
public final class SetYsmModelAction implements IAction {
    @Override public String id() { return "set_ysm_model"; }
    @Override public String displayName() { return "设置YSM模型"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return List.of(
        new TypedParam.SelectParam("mode","模式切换","ysm女仆模型",List.of("ysm女仆模型","输入")),
        new TypedParam.StringParam("model_id","模型ID",""), new TypedParam.StringParam("texture","纹理",""),
        new TypedParam.StringParam("model_name","显示名称",""));
    }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        YsmWriter.setModel(ctx.maid(), raw.getOrDefault("mode","输入"),
            raw.getOrDefault("model_id",""), raw.getOrDefault("texture",""), raw.getOrDefault("model_name",""));
    }
}

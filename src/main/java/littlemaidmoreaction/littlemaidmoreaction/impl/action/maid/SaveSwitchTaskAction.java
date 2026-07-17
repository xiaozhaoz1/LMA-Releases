package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 暂存并切任务 — 委托给 {@link MaidStateWriter#saveAndSwitchTask}. */
@RuleAction
public final class SaveSwitchTaskAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.SelectParam("task", "目标任务", "attack",
                    List.of("attack", "idle", "milk", "shear", "torch", "feed", "follow", "stand", "wander", "pickup"))
    );
    @Override public String id() { return "save_switch_task"; }
    @Override public String displayName() { return "暂存并切任务"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MaidStateWriter.saveAndSwitchTask(ctx.maid(), p.getString("task"));
    }
}

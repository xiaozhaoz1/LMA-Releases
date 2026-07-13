package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.tpm.output.TpmWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import java.util.List;
import java.util.Map;

/** TPM强制格挡 — 委托给 {@link TpmWriter#forceGuard}. */
@RuleAction
public final class TpmForceGuardAction implements IAction {
    @Override public String id() { return "tpm_force_guard"; }
    @Override public String displayName() { return "TPM强制格挡"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        TpmWriter.forceGuard(ctx.maid(), ctx.maid().getMaxHealth() * 0.2f);
    }
}

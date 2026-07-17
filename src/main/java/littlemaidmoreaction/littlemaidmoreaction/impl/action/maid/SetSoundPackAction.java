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

/** 切换女仆语音包。传入语音包 ID 字符串（如 {@code maikaze:maid}）。 */
@RuleAction
public final class SetSoundPackAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.StringParam("pack_id", "语音包ID", "maikaze:maid"));
    @Override public String id() { return "set_sound_pack"; }
    @Override public String displayName() { return "切换语音包"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MaidStateWriter.setSoundPack(ctx.maid(), p.getString("pack_id"));
    }
}

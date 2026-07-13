package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid_ext;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.EquipmentSlot;
import java.util.List;
import java.util.Map;

@RuleAction
public final class DropHandItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.SelectParam("hand", "手部", "main", List.of("main", "off")));
    @Override public String id() { return "drop_hand_item"; }
    @Override public String displayName() { return "丢弃手持物"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var slot = "off".equals(p.getString("hand")) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        MaidStateWriter.dropHandItem(ctx.maid(), slot);
    }
}


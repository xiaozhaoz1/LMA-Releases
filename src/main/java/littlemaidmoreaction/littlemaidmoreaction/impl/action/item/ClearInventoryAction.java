package littlemaidmoreaction.littlemaidmoreaction.impl.action.item;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
@RuleAction
public final class ClearInventoryAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.SelectParam("target","目标","self",List.of("self","target")));
    @Override public String id(){return"clear_inventory";}
    @Override public String displayName(){return"清空背包";}
    @Override public ActionCategory category(){return ActionCategory.ITEM;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){
        var p=ParamExtractor.from(raw,PARAMS);
        LivingEntity t=p.resolveTarget(ctx.maid(),ctx.target());
        if(t==null)return;
        MaidStateWriter.clearInventory(t);
    }
}

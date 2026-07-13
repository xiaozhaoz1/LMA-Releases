package littlemaidmoreaction.littlemaidmoreaction.impl.action.item;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.EquipmentSlot;
import java.util.*;
@RuleAction
public final class RepairItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.IntParam("amount","修复量",100),new TypedParam.SelectParam("hand","手部","mainhand",List.of("mainhand","offhand")));
    @Override public String id(){return"repair_item";}
    @Override public String displayName(){return"修复物品";}
    @Override public ActionCategory category(){return ActionCategory.ITEM;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){
        var p=ParamExtractor.from(raw,PARAMS);
        var slot="offhand".equals(p.getString("hand"))?EquipmentSlot.OFFHAND:EquipmentSlot.MAINHAND;
        MaidStateWriter.repairItem(ctx.maid(),slot,p.getInt("amount"));
    }
}

package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** 流血 — 委托给 {@link CombatOutput#bleed} PersistentData 记录。 */
@RuleAction
public final class BleedAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.DoubleParam("damage_per_tick","每tick伤害",1.0),new TypedParam.IntParam("duration","持续tick",60),new TypedParam.SelectParam("target","目标","target",List.of("self","target","owner")));
    @Override public String id(){return"bleed";}
    @Override public String displayName(){return"流血";}
    @Override public ActionCategory category(){return ActionCategory.COMBAT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public boolean isAsync(){return true;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){
        var p=ParamExtractor.from(raw,PARAMS);
        LivingEntity t=p.resolveTarget(ctx.maid(),ctx.target());
        if(t==null||!t.isAlive())return;
        CombatOutput.bleed(t,(float)p.getDouble("damage_per_tick"),p.getInt("duration"));
    }
}

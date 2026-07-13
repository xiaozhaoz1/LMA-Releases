package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import static littlemaidmoreaction.littlemaidmoreaction.engine.EngineUtils.*;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

@RuleAction
public final class TeleportAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.SelectParam("target","目标","self",List.of("self","target","owner")), new TypedParam.SelectParam("mode","模式","in_front",List.of("in_front","dodge_side","to_target","offset")), new TypedParam.DoubleParam("distance","距离",1.0), new TypedParam.DoubleParam("offset_x","X偏移",0.0), new TypedParam.DoubleParam("offset_y","Y偏移",0.0), new TypedParam.DoubleParam("offset_z","Z偏移",0.0));
    @Override public String id() { return "teleport"; } @Override public String displayName() { return "传送"; } @Override public ActionCategory category() { return ActionCategory.MOVEMENT; } @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> params) {
        String who = params.getOrDefault("target","self"), mode = params.getOrDefault("mode","offset");
        LivingEntity toMove = switch(who){case"self"->ctx.maid();case"owner"->{var u=ctx.maid().getOwnerUUID();yield u!=null?(LivingEntity)ctx.maid().level().getPlayerByUUID(u):null;}default->ctx.target();}; if(toMove==null)return;
        double dist = parseDouble(params.getOrDefault("distance","1.0"),1.0);
        double x,y,z; switch(mode){case"in_front"->{Vec3 l=ctx.maid().getLookAngle();x=ctx.maid().getX()+l.x*dist;y=ctx.maid().getY();z=ctx.maid().getZ()+l.z*dist;if(!ctx.maid().level().getBlockState(BlockPos.containing(x,y,z)).isAir()){x=ctx.maid().getX();z=ctx.maid().getZ();}}
        case"dodge_side"->{double d=parseDouble(params.getOrDefault("offset_x","0.5"),0.5);Vec3 s=ctx.maid().getLookAngle().yRot((float)(Math.PI/2)).scale(d);x=ctx.maid().getX()+s.x;y=ctx.maid().getY();z=ctx.maid().getZ()+s.z;}
        case"to_target"->{if(ctx.target()==null)return;x=ctx.target().getX();y=ctx.target().getY();z=ctx.target().getZ();}
        default->{x=toMove.getX()+parseDouble(params.getOrDefault("offset_x","0"),0);y=toMove.getY()+parseDouble(params.getOrDefault("offset_y","0"),0);z=toMove.getZ()+parseDouble(params.getOrDefault("offset_z","0"),0);}}
        toMove.teleportTo(x,y,z);
    }
}


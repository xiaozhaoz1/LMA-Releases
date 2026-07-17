package littlemaidmoreaction.littlemaidmoreaction.impl.action.visual;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.visual.VisualOutput;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

@RuleAction
public final class PlaySoundAtAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("sound_id","音效ID","minecraft:entity.lightning_bolt.thunder"),
        new TypedParam.DoubleParam("volume","音量",1.0),
        new TypedParam.DoubleParam("pitch","音调",1.0),
        new TypedParam.StringParam("x","X","0"),
        new TypedParam.StringParam("y","Y","0"),
        new TypedParam.StringParam("z","Z","0"));
    @Override public String id() { return "play_sound_at"; }
    @Override public String displayName() { return "定点音效"; }
    @Override public ActionCategory category() { return ActionCategory.VISUAL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        int x = (int) p.getDouble("x"); int y = (int) p.getDouble("y"); int z = (int) p.getDouble("z");
        VisualOutput.playSoundAt(ctx.maid().level(), new BlockPos(x, y, z), p.getString("sound_id"), (float) p.getDouble("volume"), (float) p.getDouble("pitch"));
    }
}


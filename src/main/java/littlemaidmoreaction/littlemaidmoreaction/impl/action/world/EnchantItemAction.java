package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

/** 附魔台 — 搜索委托保留，GUI委托给 {@link WorldOutput#openGui}. */
@RuleAction
public final class EnchantItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("range", "搜索范围", 16)
    );

    @Override public String id() { return "enchant_item"; }
    @Override public String displayName() { return "附魔物品"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        if (ctx.maid().level().isClientSide()) return;
        var p = ParamExtractor.from(raw, PARAMS);
        BlockPos pos = findBlock(ctx.maid(), p.getInt("range"), Blocks.ENCHANTING_TABLE);
        if (pos == null) return;
        var owner = ctx.maid().getOwner();
        if (owner instanceof Player player) WorldOutput.openGui(ctx.maid().level(), player, pos);
    }

    static BlockPos findBlock(EntityMaid maid, int range, net.minecraft.world.level.block.Block target) {
        var center = maid.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-range, -4, -range), center.offset(range, 4, range))) {
            if (maid.level().getBlockState(pos).is(target)) return pos.immutable();
        }
        return null;
    }
}

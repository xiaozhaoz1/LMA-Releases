package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.VanillaInputRegistry;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.recipe.RecipeChain;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import littlemaidmoreaction.littlemaidmoreaction.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.ProgressNotifier;
import littlemaidmoreaction.littlemaidmoreaction.core.MaterialChecker;
import littlemaidmoreaction.littlemaidmoreaction.core.model.MaterialReport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CraftChainPipeline implements TaskPipeline {

    @Override
    public String taskType() { return "craft_chain"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s) { return s.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE); }

    @Override
    public List<TaskStep> steps() {
        return List.of(
            new TaskStep("resolve", "解析配方", StepType.COLLECT, List.of()),
            new TaskStep("gather", "收集材料", StepType.COLLECT, List.of("resolve")),
            new TaskStep("craft", "合成物品", StepType.CRAFT, List.of("gather")),
            new TaskStep("deliver", "交付产物", StepType.DELIVER, List.of("craft"))
        );
    }

    /** v44: 纯验证 — 仅检查配方+材料(读操作)，不写日志 */
    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        Map<Item, Integer> available = VanillaInputRegistry.readAllItems(maid);
        if (available.isEmpty()) return PipelineResult.failed("empty inventory");
        var chain = RecipeResolver.resolve(level, target, available);
        if (chain == null || chain.steps().isEmpty()) return PipelineResult.failed("no recipe for " + target);
        MaterialReport<Item> report = MaterialChecker.check(extractRequired(chain), available);
        if (!report.sufficient()) return PipelineResult.failed("insufficient materials");
        return PipelineResult.ok("craft_chain ready");
    }


    private static Map<Item, Integer> merge(Map<Item, Integer> a, Map<Item, Integer> b) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        if (a != null) result.putAll(a);
        if (b != null) b.forEach((k, v) -> result.merge(k, v, Integer::sum));
        return result;
    }

    private static Map<Item, Integer> extractRequired(RecipeChain chain) {
        Map<Item, Integer> required = new LinkedHashMap<>();
        if (chain.cost() != null) required.putAll(chain.cost());
        return required;
    }
}

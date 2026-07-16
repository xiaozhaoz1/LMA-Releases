package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaInputRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.recipe.RecipeChain;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.ProgressNotifier;
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

    @Override
    public List<TaskStep> steps() {
        return List.of(
            new TaskStep("resolve", "解析配方", StepType.COLLECT, List.of()),
            new TaskStep("gather", "收集材料", StepType.COLLECT, List.of("resolve")),
            new TaskStep("craft", "合成物品", StepType.CRAFT, List.of("gather")),
            new TaskStep("deliver", "交付产物", StepType.DELIVER, List.of("craft"))
        );
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return execute(level, maid, ctx);
    }

    @Override
    public PipelineResult execute(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        int targetCount = ctx.targetCount();

        LittleMaidMoreAction.LOGGER.info("[V18] [CraftChain] START: target={}, targetCount={}", target, targetCount);

        Map<Item, Integer> available = VanillaInputRegistry.readAllItems(maid);
        if (available.isEmpty()) return PipelineResult.failed("empty inventory");

        var chain = RecipeResolver.resolve(level, target, available);
        if (chain == null || chain.steps().isEmpty()) return PipelineResult.failed("no recipe for " + target);

        Map<Item, Integer> required = extractRequired(chain);
        MaterialReport<Item> report = MaterialChecker.check(required, available);
        if (!report.sufficient()) return PipelineResult.failed("insufficient materials");

        // ★ v18: Brain Behavior handles execution directly — no RuleWriter needed
        LittleMaidMoreAction.LOGGER.info("[V18] [CraftChain] validate OK: {} steps, taskId={}", chain.steps().size(), ctx.taskId());
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

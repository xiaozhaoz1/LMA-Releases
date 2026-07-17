package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.item.ToolStateReader;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.ToolJudge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 连锁挖矿管道 (v36) — 校验主手镐可用。
 * 挖掘等级判定（镐材质 vs 矿石门槛）在到达矿脉后由 ToolJudge.canPickaxeMine 逐块执行。
 */
public final class ChainOrePipeline implements TaskPipeline {

    @Override
    public String taskType() {
        return "collect_ore";
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        ItemStack tool = maid.getMainHandItem();
        if (!ToolStateReader.isPickaxe(tool)) {
            return PipelineResult.failed("需要主手持镐才能挖矿");
        }
        if (!ToolJudge.isToolUsable(tool, 1)) {
            return PipelineResult.failed("镐耐久不足");
        }
        return PipelineResult.ok("开始连锁挖矿");
    }

    @Override
    @Deprecated
    public PipelineResult execute(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return validate(level, maid, ctx);
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(
                new TaskStep("search", "寻找矿石", StepType.COLLECT, List.of()),
                new TaskStep("mine", "连锁挖掘", StepType.INTERACT, List.of("search"))
        );
    }
}

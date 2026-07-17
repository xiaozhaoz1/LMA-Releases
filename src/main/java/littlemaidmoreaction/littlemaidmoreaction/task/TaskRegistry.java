package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.VanillaTasks;
import littlemaidmoreaction.littlemaidmoreaction.api.SlotLayout;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;

import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 任务注册中心 — 所有任务类型的单一真相源。
 *
 * 合并了旧 TaskOrchestrator (Pipeline 验证) + TaskHandlerRegistry (导航执行)。
 * 新增任务类型只需一行 register()。
 */
public final class TaskRegistry {

    private static final Map<String, TaskHandler> HANDLERS = new LinkedHashMap<>();

    static {
        register("craft_chain", Blocks.CRAFTING_TABLE,
            state -> state.is(Blocks.CRAFTING_TABLE),
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.CraftChainPipeline(),
            (w, m, p, d) -> VanillaTasks.craft(w, m, p, d.getString("lma_task_target"))
                ? TaskResult.SUCCESS : TaskResult.FAILED);

        register("furnace", null,
            state -> true,
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.FurnacePipeline(),
            (w, m, p, d) -> {
                VanillaTasks.furnace(w, m, p, d.getString("lma_task_input"), SlotLayout.FURNACE);
                return TaskResult.SUCCESS;
            });

        register("jukebox", Blocks.JUKEBOX,
            state -> state.is(Blocks.JUKEBOX),
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.JukeboxPipeline(),
            (w, m, p, d) -> {
                VanillaTasks.jukebox(w, m, p, d.getString("lma_task_target"));
                return TaskResult.CONTINUE;
            });

        register("bell_ring", Blocks.BELL,
            state -> state.getBlock() instanceof BellBlock,
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.BellRingPipeline(),
            (w, m, p, d) -> {
                VanillaTasks.bell(w, m, p);
                return TaskResult.SUCCESS;
            });

        // ── v36: 连锁采集（环境感知任务 — searchPredicate 按标签搜索目标） ──
        registerSearch("collect_wood",
            (p, s) -> s.is(net.minecraft.tags.BlockTags.LOGS),
            state -> state.is(net.minecraft.tags.BlockTags.LOGS),
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.ChainWoodPipeline(),
            (w, m, p, d) -> littlemaidmoreaction.littlemaidmoreaction.vanilla.execute
                .ChainHarvestExecute.execute(w, m, p, d,
                    littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.Mode.WOOD));

        registerSearch("collect_ore",
            (p, s) -> s.is(net.minecraftforge.common.Tags.Blocks.ORES),
            state -> state.is(net.minecraftforge.common.Tags.Blocks.ORES),
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.ChainOrePipeline(),
            (w, m, p, d) -> littlemaidmoreaction.littlemaidmoreaction.vanilla.execute
                .ChainHarvestExecute.execute(w, m, p, d,
                    littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.Mode.ORE));

        // ── v38: 机械臂搬运任务 (Create 兼容, compat/create/) ──
        registerSearch("arm_transfer",
            (p, s) -> false,
            state -> true,
            new littlemaidmoreaction.littlemaidmoreaction.compat.create.ArmTransferPipeline(),
            (w, m, p, d) -> littlemaidmoreaction.littlemaidmoreaction.compat.create.ArmTransferPipeline
                .tick(w, m, p, d));
    }

    /** 注册任务类型 (targetBlock 精确匹配搜索) */
    public static void register(String taskType, Block block, Predicate<BlockState> valid,
                                 TaskPipeline pipeline, IExecutor executor) {
        HANDLERS.put(taskType, new TaskHandler(taskType, block, null, valid, pipeline, executor));
    }

    /** v36: 注册任务类型 (searchPredicate 自定义搜索 — 标签/多方块匹配) */
    public static void registerSearch(String taskType, BiPredicate<BlockPos, BlockState> searchPredicate,
                                       Predicate<BlockState> valid,
                                       TaskPipeline pipeline, IExecutor executor) {
        HANDLERS.put(taskType, new TaskHandler(taskType, null, searchPredicate, valid, pipeline, executor));
    }

    /**
     * v18: 验证材料 + 写任务参数到 PersistentData。
     * Brain Behavior 直接读取参数执行导航+交互。
     */
    public static PipelineResult validate(EntityMaid maid, String taskType, String taskId,
                                          String target, int targetCount) {
        LittleMaidMoreAction.LOGGER.info("[V35] [TaskRegistry] validate: task_type={}, target={}, count={}",
            taskType, target, targetCount);

        TaskHandler handler = HANDLERS.get(taskType);
        if (handler == null) {
            return PipelineResult.failed("未知任务类型: " + taskType);
        }

        if (!(maid.level() instanceof ServerLevel level)) {
            return PipelineResult.failed("仅在服务端可用");
        }

        return handler.pipeline().validate(level, maid,
            new PipelineContext(target, targetCount, taskId));
    }

    // ── Queries ──

    public static TaskHandler get(String taskType) { return HANDLERS.get(taskType); }
    public static Set<String> taskTypes() { return HANDLERS.keySet(); }

    // ── Types ──

    public record TaskHandler(
        String taskType,
        Block targetBlock,
        BiPredicate<BlockPos, BlockState> searchPredicate,
        Predicate<BlockState> isValid,
        TaskPipeline pipeline,
        IExecutor executor
    ) {}

}

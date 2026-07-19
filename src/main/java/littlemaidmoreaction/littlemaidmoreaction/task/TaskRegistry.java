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
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.logistics.depot.DepotBlock;
import com.simibubi.create.content.processing.basin.BasinBlock;
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

        // ── v36: 连锁采集 ──
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

        // ── v38-39: Create 女仆专属任务 (仅 Create 加载时注册) ──
        if (net.minecraftforge.fml.ModList.get().isLoaded("create")) {
            // v38: 搬运
            registerSearch("arm_transfer",
                (p, s) -> false,
                state -> true,
                new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.ArmTransferPipeline(),
                littlemaidmoreaction.littlemaidmoreaction.compat.create.task.ArmTransferPipeline.executor());

            // v39: 手摇曲柄
            registerSearch("crank",
                (p, s) -> s.getBlock() instanceof HandCrankBlock,
                state -> state.getBlock() instanceof HandCrankBlock,
                new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.CrankPipeline(),
                littlemaidmoreaction.littlemaidmoreaction.compat.create.task.CrankPipeline.executor());

            // v39: 动力齿轮
            registerSearch("power",
                (p, s) -> littlemaidmoreaction.littlemaidmoreaction.compat.create.task.PowerService.isTargetBlock(s.getBlock()),
                state -> littlemaidmoreaction.littlemaidmoreaction.compat.create.task.PowerService.isTargetBlock(state.getBlock()),
                new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.PowerPipeline(),
                littlemaidmoreaction.littlemaidmoreaction.compat.create.task.PowerPipeline.executor());

            // v39: 女仆冲压
            registerSearch("press",
                (p, s) -> { Block b = s.getBlock(); return b instanceof DepotBlock || b instanceof BasinBlock; },
                state -> true,
                new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.PressPipeline(),
                littlemaidmoreaction.littlemaidmoreaction.compat.create.task.PressPipeline.executor());

            // v39: 女仆搅拌
            registerSearch("mix",
                (p, s) -> s.getBlock() instanceof BasinBlock,
                state -> true,
                new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.MixPipeline(),
                littlemaidmoreaction.littlemaidmoreaction.compat.create.task.MixPipeline.executor());

            // v40: 女仆跑步发电
            registerSearch("running_belt",
                (p, s) -> s.getBlock() instanceof com.simibubi.create.content.kinetics.belt.BeltBlock
                    && s.getValue(com.simibubi.create.content.kinetics.belt.BeltBlock.SLOPE)
                        == com.simibubi.create.content.kinetics.belt.BeltSlope.HORIZONTAL,
                state -> state.getBlock() instanceof com.simibubi.create.content.kinetics.belt.BeltBlock,
                new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.RunningBeltPipeline(),
                littlemaidmoreaction.littlemaidmoreaction.compat.create.task.RunningBeltPipeline.executor());
        }
    }

    public static void register(String taskType, Block block, Predicate<BlockState> valid,
                                 TaskPipeline pipeline, IExecutor executor) {
        HANDLERS.put(taskType, new TaskHandler(taskType, block, null, valid, pipeline, executor));
    }

    public static void registerSearch(String taskType, BiPredicate<BlockPos, BlockState> searchPredicate,
                                       Predicate<BlockState> valid,
                                       TaskPipeline pipeline, IExecutor executor) {
        HANDLERS.put(taskType, new TaskHandler(taskType, null, searchPredicate, valid, pipeline, executor));
    }

    public static PipelineResult validate(EntityMaid maid, String taskType, String taskId,
                                          String target, int targetCount) {
        LittleMaidMoreAction.LOGGER.info("[V35] [TaskRegistry] validate: task_type={}, target={}, count={}",
            taskType, target, targetCount);
        TaskHandler handler = HANDLERS.get(taskType);
        if (handler == null) return PipelineResult.failed("未知任务类型: " + taskType);
        if (!(maid.level() instanceof ServerLevel level)) return PipelineResult.failed("仅在服务端可用");
        return handler.pipeline().validate(level, maid, new PipelineContext(target, targetCount, taskId));
    }

    public static TaskHandler get(String taskType) { return HANDLERS.get(taskType); }
    public static Set<String> taskTypes() { return HANDLERS.keySet(); }

    public record TaskHandler(
        String taskType, Block targetBlock,
        BiPredicate<BlockPos, BlockState> searchPredicate,
        Predicate<BlockState> isValid,
        TaskPipeline pipeline, IExecutor executor
    ) {}
}

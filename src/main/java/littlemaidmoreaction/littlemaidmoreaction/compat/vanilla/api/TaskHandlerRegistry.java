package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.VanillaTasks;
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
import java.util.function.Predicate;

/**
 * 任务处理器注册表 — 替代 LmaFlowCoordinationBehavior 中的 3 个 switch。
 * 新增任务类型只需在此 register() 一行。
 */
public final class TaskHandlerRegistry {
    private static final Map<String, TaskHandler> HANDLERS = new LinkedHashMap<>();

    static {
        register("craft_chain", Blocks.CRAFTING_TABLE,
            state -> state.is(Blocks.CRAFTING_TABLE),
            (w, m, p, d) -> VanillaTasks.craft(w, m, p, d.getString("lma_task_target"))
                ? TaskResult.SUCCESS : TaskResult.FAILED);

        register("furnace", Blocks.FURNACE,
            state -> state.is(Blocks.FURNACE)
                  || state.is(Blocks.BLAST_FURNACE)
                  || state.is(Blocks.SMOKER),
            (w, m, p, d) -> {
                VanillaTasks.furnace(w, m, p, d.getString("lma_task_input"));
                return TaskResult.SUCCESS;
            });

        register("jukebox", Blocks.JUKEBOX,
            state -> state.is(Blocks.JUKEBOX),
            (w, m, p, d) -> {
                VanillaTasks.jukebox(w, m, p, d.getString("lma_task_target"));
                return TaskResult.CONTINUE;
            });

        register("bell_ring", Blocks.BELL,
            state -> state.getBlock() instanceof BellBlock,
            (w, m, p, d) -> {
                VanillaTasks.bell(w, m, p);
                return TaskResult.SUCCESS;
            });

        register("altar_craft", null,
            state -> false, // altar 验证特殊: 需检查 BlockEntity
            (w, m, p, d) -> {
                var action = new littlemaidmoreaction.littlemaidmoreaction.impl.action.world.PlaceAltarItemAction();
                var params = new java.util.HashMap<String, String>();
                String target = d.getString("lma_task_target");
                params.put("item_id", target.isEmpty() ? "minecraft:coal" : target);
                params.put("range", String.valueOf((int) m.getRestrictRadius()));
                action.execute(new littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext(m), params);
                return TaskResult.SUCCESS;
            });
    }

    public static void register(String taskType, Block block, Predicate<BlockState> valid, TaskExecutor exec) {
        HANDLERS.put(taskType, new TaskHandler(taskType, block, valid, exec));
    }

    public static TaskHandler get(String taskType) {
        return HANDLERS.get(taskType);
    }

    public static Set<String> taskTypes() {
        return HANDLERS.keySet();
    }

    // -- nested types --

    public record TaskHandler(
        String taskType,
        Block targetBlock,
        Predicate<BlockState> isValid,
        TaskExecutor executor
    ) {}

    @FunctionalInterface
    public interface TaskExecutor {
        TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos, CompoundTag data);
    }
}

package littlemaidmoreaction.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.task.*;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.pipeline.*;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 任务注册中心 (v53: 注册简化 — 每个任务一行 register(name, new Pipeline(), Pipeline.executor()))
 * 外部 Mod 注册方式见 {@link #register(String, TaskPipeline, IExecutor, boolean)}
 */
public final class TaskRegistry {

    private static final Map<String, TaskHandler> HANDLERS = new LinkedHashMap<>();

    static {
        register("craft_chain",  new CraftChainPipeline(),  CraftChainPipeline.executor());
        register("furnace",      new FurnacePipeline(),      FurnacePipeline.executor());
        register("jukebox",      new JukeboxPipeline(),      JukeboxPipeline.executor());
        register("bell_ring",    new BellRingPipeline(),     BellRingPipeline.executor());
        register("collect_wood", new ChainWoodPipeline(),    ChainWoodPipeline.executor());
        register("collect_ore",  new ChainOrePipeline(),     ChainOrePipeline.executor());
        register("altar_craft",  new AltarCraftPipeline(),   AltarCraftPipeline.executor());
        var armTransferPl = new ArmTransferPipeline();
        register("arm_transfer", armTransferPl, armTransferPl.executor());

        // ── v38-40: Create 女仆专属任务 ──
        if (net.minecraftforge.fml.ModList.get().isLoaded("create")) {
            var crankPl = new CrankPipeline();
            register("crank", crankPl, crankPl.executor());

            var powerPl = new PowerPipeline();
            register("power", powerPl, powerPl.executor());

            var pressPL = new PressPipeline();
            register("press", pressPL, pressPL.executor());

            var mixPL = new MixPipeline();
            register("mix", mixPL, mixPL.executor());

            var beltPl = new RunningBeltPipeline();
            register("running_belt", beltPl, beltPl.executor());

            var assemblyPl = new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyPipeline();
            register("maid_assembly", assemblyPl, assemblyPl.executor(), true);
        }
    }

    /**
     * v52: 注册任务 — showInBar=true 的任务会出现在 TLM 任务栏 GUI。
     * 被动/环境任务应传 false，只内部注册不暴露给玩家。
     */
    public static void register(String taskType, TaskPipeline pipeline, IExecutor executor,
                                 boolean showInBar) {
        HANDLERS.put(taskType, new TaskHandler(taskType, pipeline, executor, showInBar));
    }

    /** v52: 默认 showInBar=true — 大多数任务在 TLM 任务栏可见 */
    public static void register(String taskType, TaskPipeline pipeline, IExecutor executor) {
        register(taskType, pipeline, executor, true);
    }

    /** v63: 注册被动任务 (showInBar=false), 供环境感知管线使用 */
    public static void registerPassive(String taskType, TaskPipeline pipeline) {
        register(taskType, pipeline, passiveExecutor(), false);
    }

    private static IExecutor passiveExecutor() {
        return (w, m, p, d) ->
                littlemaidmoreaction.littlemaidmoreaction.api.TaskResult.CONTINUE;
    }

    public static PipelineResult validate(EntityMaid maid, String taskType, String taskId,
                                          String target, int targetCount) {
        TaskHandler handler = HANDLERS.get(taskType);
        if (handler == null) return PipelineResult.failed("未知任务类型: " + taskType);
        if (!(maid.level() instanceof ServerLevel level)) return PipelineResult.failed("仅在服务端可用");
        return handler.pipeline().validate(level, maid, new PipelineContext(target, targetCount, taskId));
    }

    public static TaskHandler get(String taskType) { return HANDLERS.get(taskType); }
    public static Set<String> taskTypes() { return HANDLERS.keySet(); }

    /** v52: 是否在 TLM 任务栏显示 */
    public static boolean isShowInBar(String taskType) {
        TaskHandler h = HANDLERS.get(taskType);
        return h != null && h.showInBar();
    }

    /** v52: showInBar=true → TLM 任务栏可见; false → 仅内部注册 (被动/环境任务) */
    /** showInBar=false 的被动任务 (v61). */
    public static java.util.stream.Stream<TaskHandler> passiveTasks() {
        return HANDLERS.values().stream().filter(h -> !h.showInBar());
    }

    public record TaskHandler(String taskType, TaskPipeline pipeline, IExecutor executor,
                               boolean showInBar) {}
}

package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.VanillaTasks;
import littlemaidmoreaction.littlemaidmoreaction.api.SlotLayout;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 任务注册中心 (v52: 注册简化 — 只指定 pipeline + executor, 方块过滤由 Pipeline 自己负责)
 */
public final class TaskRegistry {

    private static final Map<String, TaskHandler> HANDLERS = new LinkedHashMap<>();
    public static final Map<String, BiConsumer<ServerLevel, EntityMaid>> CREATE_TICK = new LinkedHashMap<>();

    static {
        register("craft_chain",
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.CraftChainPipeline(),
            new IExecutor() {
                @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                    return VanillaTasks.craft(w, m, p, d.getString("lma_task_target"))
                        ? TaskResult.SUCCESS : TaskResult.FAILED;
                }
                @Override public void onStop(EntityMaid maid) {}
            });

        register("furnace",
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.FurnacePipeline(),
            new IExecutor() {
                @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                    String ingredientKey = resolveSmeltIngredient(w, m, d);
                    if (ingredientKey.isEmpty()) return TaskResult.FAILED;
                    d.putString("lma_task_input", ingredientKey);
                    VanillaTasks.furnace(w, m, p, ingredientKey, SlotLayout.FURNACE);
                    return TaskResult.SUCCESS;
                }
                @Override public void onStop(EntityMaid maid) {}

                private String resolveSmeltIngredient(ServerLevel level, EntityMaid maid, CompoundTag d) {
                    String target = d.getString("lma_task_target");
                    if (target.isEmpty()) return "";
                    Item targetItem = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(net.minecraft.resources.ResourceLocation.tryParse(target));
                    if (targetItem == null) return "";
                    Map<Item, Integer> allItems = littlemaidmoreaction.littlemaidmoreaction.api
                        .VanillaInputRegistry.readAllItems(maid);
                    for (SmeltingRecipe recipe : level.getRecipeManager()
                         .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING)) {
                        if (!recipe.getResultItem(level.registryAccess()).is(targetItem)) continue;
                        for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                            if (allItems.getOrDefault(ing.getItem(), 0) > 0)
                                return net.minecraftforge.registries.ForgeRegistries.ITEMS
                                    .getKey(ing.getItem()).toString();
                        }
                    }
                    // fallback: target itself in inventory
                    if (allItems.getOrDefault(targetItem, 0) > 0)
                        return target;
                    return "";
                }
            });

        register("jukebox",
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.JukeboxPipeline(),
            new IExecutor() {
                @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                    VanillaTasks.jukebox(w, m, p, d.getString("lma_task_target"));
                    return TaskResult.CONTINUE;
                }
                @Override public void onStop(EntityMaid maid) {}
            });

        register("bell_ring",
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.BellRingPipeline(),
            new IExecutor() {
                @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                    VanillaTasks.bell(w, m, p);
                    return TaskResult.SUCCESS;
                }
                @Override public void onStop(EntityMaid maid) {}
            });

        // ── v36: 连锁采集 ──
        register("collect_wood",
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.ChainWoodPipeline(),
            new IExecutor() {
                @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                    return littlemaidmoreaction.littlemaidmoreaction.vanilla.execute
                        .ChainHarvestExecute.execute(w, m, p, d,
                            littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.Mode.WOOD);
                }
                @Override public void onStop(EntityMaid maid) {
                    littlemaidmoreaction.littlemaidmoreaction.vanilla.execute
                        .ChainHarvestExecute.onMaidUnload(maid.getId());
                }
            });

        register("collect_ore",
            new littlemaidmoreaction.littlemaidmoreaction.task.pipeline.ChainOrePipeline(),
            new IExecutor() {
                @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                    return littlemaidmoreaction.littlemaidmoreaction.vanilla.execute
                        .ChainHarvestExecute.execute(w, m, p, d,
                            littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute.Mode.ORE);
                }
                @Override public void onStop(EntityMaid maid) {
                    littlemaidmoreaction.littlemaidmoreaction.vanilla.execute
                        .ChainHarvestExecute.onMaidUnload(maid.getId());
                }
            });

        // ── v44: 祭坛合成 ──
        register("altar_craft",
            new TaskPipeline() {
                @Override public String taskType() { return "altar_craft"; }
                @Override public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
                    return PipelineResult.ok("");
                }
                
            },
            new IExecutor() {
                @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                    return TaskResult.SUCCESS;
                }
                @Override public void onStop(EntityMaid maid) {}
            });

        // ── v38-40: Create 女仆专属任务 ──
        if (net.minecraftforge.fml.ModList.get().isLoaded("create")) {
            var armTransferPl = new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.ArmTransferPipeline();
            register("arm_transfer", armTransferPl, armTransferPl.executor());
            CREATE_TICK.put("arm_transfer", armTransferPl::tick);

            var crankPl = new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.CrankPipeline();
            register("crank", crankPl, crankPl.executor());
            CREATE_TICK.put("crank", crankPl::tick);

            var powerPl = new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.PowerPipeline();
            register("power", powerPl, powerPl.executor());
            CREATE_TICK.put("power", powerPl::tick);

            var pressPL = new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.PressPipeline();
            register("press", pressPL, pressPL.executor());
            CREATE_TICK.put("press", pressPL::tick);

            var mixPL = new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.MixPipeline();
            register("mix", mixPL, mixPL.executor());
            CREATE_TICK.put("mix", mixPL::tick);

            var beltPl = new littlemaidmoreaction.littlemaidmoreaction.compat.create.task.RunningBeltPipeline();
            register("running_belt", beltPl, beltPl.executor());
            CREATE_TICK.put("running_belt", (w, m) -> littlemaidmoreaction.littlemaidmoreaction.compat.create.task.RunningBeltPipeline.tick(w, m));
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

    public static BiConsumer<ServerLevel, EntityMaid> getCreateTick(String taskType) {
        return CREATE_TICK.get(taskType);
    }

    /** v52: showInBar=true → TLM 任务栏可见; false → 仅内部注册 (被动/环境任务) */
    public record TaskHandler(String taskType, TaskPipeline pipeline, IExecutor executor,
                               boolean showInBar) {}
}

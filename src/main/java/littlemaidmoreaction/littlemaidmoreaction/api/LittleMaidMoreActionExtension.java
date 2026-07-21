package littlemaidmoreaction.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.magic.MagicCastingAnimationManager;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.crop.SpecialCropManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import java.util.List;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaMagicCastingProvider;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.AutoCropHandler;
import littlemaidmoreaction.littlemaidmoreaction.resource.DynamicAnimationResources;
import littlemaidmoreaction.littlemaidmoreaction.storage.StartupLoader;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * TLM 扩展入口。
 *
 * 动画注册双通道：
 * - FORGE 总线: 注册 jar 内置动画到 TLM (DefaultGeckoAnimationEvent)
 * - MOD 总线:  生成 config 预设 → 扫描自定义 → 加载时长 → 注册 AnimationState
 *
 * 所有配置统一在 config/littlemaidmoreaction/ 下管理。
 */
@LittleMaidExtension
public final class LittleMaidMoreActionExtension implements ILittleMaid {

    public LittleMaidMoreActionExtension() {
        // ★ v35: 任务类型注册已移至 TaskRegistry (静态初始化)
        TaskRegistry.taskTypes(); // 触发类加载 → 自动注册 5 个 Pipeline
    }

    /** 注册 LMA 魔法咏唱动画 Provider — TLM 每帧自动调用（客户端专用） */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void registerMagicCastingAnimation(MagicCastingAnimationManager manager) {
        manager.register(new LmaMagicCastingProvider());
        LittleMaidMoreAction.LOGGER.info("[LMA] MagicCasting Provider 已注册");
    }

    /** ★ v12.5 注册 LMA 流程任务到 TLM TaskManager — 每种 task_type 独立注册 */
    @Override
    public void addMaidTask(TaskManager manager) {
        LmaTaskTypeRegistry.scanAndRegister(manager);
    }

    /** ★ v9.3 注册耕种种子白名单处理器 — 拦截 farmland 上的 canPlant 调用 */
    @Override
    public void registerSpecialCropHandler(SpecialCropManager manager) {
        manager.addCrop(Blocks.FARMLAND, new AutoCropHandler());
        LittleMaidMoreAction.LOGGER.info("[LMA] 耕种白名单处理器已注册");
    }

    // ── v10: AI 整合扩展点 (TLM >= 1.5.1) ──

    @Override
    public void registerAITool(ToolRegister register) {
        register.register(new littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool.StartTaskTool());
        register.register(new littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool.ListRulesTool());
        register.register(new littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool.DeleteRuleTool());
        register.register(new littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool.ToggleRuleTool());
        register.register(new littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool.LmaSearchTool());
        register.register(new littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool.LmaDocsTool());
        LittleMaidMoreAction.LOGGER.info("[LMA] AI Tools 已注册 (6 tools)");
    }

    @Override
    public void registerAIMaidContext(GameContextRegister register) {
        // v10: 周围方块感知 + LMA 状态摘要
        littlemaidmoreaction.littlemaidmoreaction.compat.ai.context.LmaBlocksContext.registerAll(register);
        littlemaidmoreaction.littlemaidmoreaction.compat.ai.context.LmaStatusContext.registerAll(register);
        littlemaidmoreaction.littlemaidmoreaction.compat.ai.context.LmaDetailContext.registerAll(register);
        LittleMaidMoreAction.LOGGER.info("[LMA] AI Context 已注册 (nearby_blocks + lma_status + lma_details)");
    }

    // ── v12.5: 预留扩展钩子 (待实现) ──

    /** [预留] 注册女仆背包类型 */
    @Override
    public void addMaidBackpack(BackpackManager manager) {
        // TODO: LMA 自定义背包 (如规则配置背包、任务物品包)
        LittleMaidMoreAction.LOGGER.debug("[LMA] addMaidBackpack — reserved for future use");
    }

    /** [预留] 注册箱子类型 — 供无线IO饰品识别 */
    @Override
    public void addChestType(ChestManager manager) {
        // TODO: LMA 自定义箱子识别 (如祭坛物品箱、配方缓存箱)
        LittleMaidMoreAction.LOGGER.debug("[LMA] addChestType — reserved for future use");
    }

    /** [预留] 注册女仆饭类型 */
    @Override
    public void addMaidMeal(MaidMealManager manager) {
        // TODO: LMA 自定义女仆食物 (如规则触发特殊进食行为)
        LittleMaidMoreAction.LOGGER.debug("[LMA] addMaidMeal — reserved for future use");
    }

    /** ★ v12.7 P0: 注册 LMA 自定义 MemoryModuleType — 导航目标不再用 PersistentData */
    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new IExtraMaidBrain() {
            @Override
            public List<MemoryModuleType<?>> getExtraMemoryTypes() {
                return List.of(
                        littlemaidmoreaction.littlemaidmoreaction.adapter.LmaMemoryModuleRegistry.NAV_TARGET.get(),
                        littlemaidmoreaction.littlemaidmoreaction.adapter.LmaMemoryModuleRegistry.NAV_START_TICK.get()
                );
            }
        });
        LittleMaidMoreAction.LOGGER.info("[LMA] ExtraMaidBrain 已注册 (NAV_TARGET, NAV_START_TICK)");
    }

    // ── 任务数据注册 ──

    @Override
    public void registerTaskData(TaskDataRegister register) {
        register.register(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "flow_tasks"),
            littlemaidmoreaction.littlemaidmoreaction.compat.ai.model.FlowTask.CODEC.listOf(),
            littlemaidmoreaction.littlemaidmoreaction.compat.ai.model.FlowTask.CODEC.listOf()
        );
        // v43: 注册 LmaTaskDataKeys (备用, v44切换)
        littlemaidmoreaction.littlemaidmoreaction.task.LmaTaskDataKeys.registerAll(register);
        LittleMaidMoreAction.LOGGER.info("[LMA] TaskData 已注册 (flow_tasks + lma_flow_*)");
    }

    /**
     * FORGE 总线：读取 startup.json 动画清单，注册到 TLM 动画索引。
     * 包含预设 + 自定义动画，单一来源。
     */
    @Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class AnimationEvents {
        @SubscribeEvent
        public static void onDefaultGeckoAnimation(DefaultGeckoAnimationEvent event) {
            MoreActionAPI.registerCustomAnimations(event);
        }
    }

    /** MOD 总线：客户端初始化 */
    @Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModClientEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MoreActionAPI.scanCustomAnimations();
                // ★ loadClientDurations() 已移至 onClientReload — 此时 GeckoLibCache 尚未加载自定义动画，
                // 提前调用会导致 DURATIONS 为空、兜底动画误报"不存在"
                // ★ v7: 动画播放已迁移到 magic_casting 控制器 (LmaMagicCastingProvider)
                LittleMaidMoreAction.LOGGER.info("[LMA] 客户端动画数据加载完成");
            });
        }

        /** 注册内存虚拟资源包，为动画提供虚拟文件映射。 */
        @SubscribeEvent
        public static void onAddPackFinders(AddPackFindersEvent event) {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

            // 动画资源包 — 使用 StartupLoader 预扫描数据，避免独立目录扫描的时序问题
            var animRes = new DynamicAnimationResources(StartupLoader.getAnimationFiles());
            var animPack = Pack.readMetaAndCreate(
                    "lma_dynamic_animations",
                    Component.literal("LMA Custom Animations"),
                    false,
                    id -> animRes,
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN
            );
            event.addRepositorySource(c -> c.accept(animPack));
            LittleMaidMoreAction.LOGGER.info("[LMA] 动画资源包已注册 ({} 个)", animRes.getAnimationFiles().size());
        }
    }

    /** FORGE 总线：监听女仆卸载事件，清理耕种白名单缓存 */
    @Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ServerEvents {
        @SubscribeEvent
        public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
            if (event.getEntity() instanceof EntityMaid maid) {
                AutoCropHandler.onMaidUnload(maid.getUUID());
                // v37: 环境感知缓存清理（key 闭环）
                littlemaidmoreaction.littlemaidmoreaction.api.envsense
                    .EnvSenseScheduler.onMaidUnload(maid.getId());
                // v36.1: 连锁采集跳过集清理
                littlemaidmoreaction.littlemaidmoreaction.vanilla.execute
                    .ChainHarvestExecute.onMaidUnload(maid.getId());
                // v38: FakePlayer 持续挖掘清理
                littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer
                    .FakePlayerManager.onMaidUnload(maid.getId());
            }
        }
    }
}

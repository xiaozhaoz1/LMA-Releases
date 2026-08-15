package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.magic.MagicCastingAnimationManager;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.crop.SpecialCropManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import java.util.List;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaMagicCastingProvider;
import com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.AutoCropHandler;
import com.github.xiaozhaoz1.littlemaidmoreaction.resource.DynamicAnimationResources;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
//? if !1.20.1 {
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;
//?}
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.Blocks;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
//?} else {
import net.neoforged.api.distmarker.Dist;
//?}
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.OnlyIn;
//?}
//? if 1.20.1 {
import net.minecraftforge.event.AddPackFindersEvent;
//?} else {
import net.neoforged.neoforge.event.AddPackFindersEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
//?} else {
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//?} else {
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//?}

/**
 * TLM 扩展入口。
 *
 * 动画注册 (DefaultGeckoAnimationEvent) 在平台入口构造器手动注册
 * (forge: {@code LittleMaidMoreAction} / neoforge: {@code LmaNeoForgeClientEntry}) —
 * TLM javadoc 要求手动注册 ("事件早于 LittleMaidExtension 注解识别"), v79.18 实证注解
 * auto-scan 对该事件失效; 2026-08-11c 删本类原 @EventBusSubscriber 注解路径 (双注册清理)。
 * - 平台入口 (FORGE/GAME 总线): 注册 jar 内置动画到 TLM (DefaultGeckoAnimationEvent)
 * - MOD 总线 (本类 ModClientEvents): 生成 config 预设 → 扫描自定义 → 加载时长
 *
 * 所有配置统一在 config/littlemaidmoreaction/ 下管理。
 */
@LittleMaidExtension
public final class LittleMaidMoreActionExtension implements ILittleMaid {

    public LittleMaidMoreActionExtension() {
        // 任务类型注册已移至 TaskRegistry (静态初始化)
        TaskRegistry.taskTypes(); // 触发类加载 → 自动注册 5 个 Pipeline
    }

    /** 注册 LMA 魔法咏唱动画 Provider — TLM 每帧自动调用（客户端专用） */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void registerMagicCastingAnimation(MagicCastingAnimationManager manager) {
        manager.register(new LmaMagicCastingProvider());
        LittleMaidMoreAction.LOGGER.info("[LMA] MagicCasting Provider 已注册");
    }

    /** 注册 LMA 流程任务到 TLM TaskManager — 每种 task_type 独立注册 */
    @Override
    public void addMaidTask(TaskManager manager) {
        LmaTaskTypeRegistry.scanAndRegister(manager);
        // 便携装配 (Create门控); CompatToggle 开关 (可 GUI 关闭)
        // ★ 门控镜像 CompatRegistry.MODULES 模块表 (GUI/开关单一事实源) — 2026-08-11c
//? if 1.20.1 {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                && net.minecraftforge.fml.ModList.get().isLoaded("create")) {
//?} else {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                && net.neoforged.fml.ModList.get().isLoaded("create")) {
//?}
//? if 1.20.1 {
            manager.add(com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyTask.get());
//?}
            LittleMaidMoreAction.LOGGER.info("[LMA] MaidAssemblyTask 已注册");
        }
    }

    /** 注册耕种种子白名单处理器 — 拦截 farmland 上的 canPlant 调用 */
    @Override
    public void registerSpecialCropHandler(SpecialCropManager manager) {
        manager.addCrop(Blocks.FARMLAND, new AutoCropHandler());
        LittleMaidMoreAction.LOGGER.info("[LMA] 耕种白名单处理器已注册");
    }

    // ── AI 整合扩展点 (TLM >= 1.5.1) ──

    @Override
    public void registerAITool(ToolRegister register) {
        // AI 世界操作工具 (TLM AI 环 — 移动/挖掘/交互/战斗/切任务, 委托 LMA IO+fakeplayer)
        com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.AiToolRegistration.registerAll(register);
    }

    @Override
    public void registerAIMaidContext(GameContextRegister register) {
        // 周围方块感知 + LMA 状态摘要 (LmaStatusContext 暂挂)
        com.github.xiaozhaoz1.littlemaidmoreaction.ai.context.LmaBlocksContext.registerAll(register);
        com.github.xiaozhaoz1.littlemaidmoreaction.ai.context.LmaDetailContext.registerAll(register);
        // 环境感知上下文
        com.github.xiaozhaoz1.littlemaidmoreaction.ai.context.LmaEnvSenseContext.registerAll(register);
        // 任务状态上下文
        com.github.xiaozhaoz1.littlemaidmoreaction.ai.context.MaidTaskContext.registerAll(register);
        LittleMaidMoreAction.LOGGER.info("[LMA] AI Context 已注册 (nearby_blocks + lma_status + lma_details + envsense + lma_task)");
    }

    // ── 哈气表情气泡自定义类型 ──

    @Override
    public void registerChatBubble(com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleRegister register) {
        register.register(com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidEmojiBubbleData.ID,
                new com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidEmojiBubbleData.MaidEmojiChatSerializer());
        LittleMaidMoreAction.LOGGER.info("[LMA] 哈气表情气泡已注册 (haqi_emoji)");
    }

    /** 注册 LMA 女仆饰品 (v79.6x 酒狐奶) — TLM BaubleManager 构造期绑定 */
    @Override
    public void bindMaidBauble(com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager manager) {
        com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkBaubleRegistry.bind(manager);
        LittleMaidMoreAction.LOGGER.info("[LMA] 酒狐奶饰品已注册 (tamed_milk_bucket / wild_dogmilk)");
    }

    // ── 预留扩展钩子 (待实现) ──

    /** [预留] 注册女仆背包类型 */
    @Override
    public void addMaidBackpack(BackpackManager manager) {
        // TODO: LMA 自定义背包 (需 IMaidBackpack: id + 物品 + 客户端模型/纹理)
    }

    /** [预留] 注册箱子类型 — 供无线IO饰品识别 */
    @Override
    public void addChestType(ChestManager manager) {
        // TODO: LMA 自定义箱子识别 (需 IChestType: 自有容器方块; 无线IO饰品语义落地时再议)
    }

    /** [预留] 注册女仆饭类型 */
    @Override
    public void addMaidMeal(MaidMealManager manager) {
        // TODO: LMA 自定义女仆食物 (需 IMaidMeal: 自有食物 + 进食行为)
    }

    /** P0: 注册 LMA 自定义 MemoryModuleType — 导航目标不再用 PersistentData */
    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new IExtraMaidBrain() {
            @Override
            public List<MemoryModuleType<?>> getExtraMemoryTypes() {
                return List.of(
                        com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaMemoryModuleRegistry.NAV_TARGET.get(),
                        com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaMemoryModuleRegistry.NAV_START_TICK.get()
                );
            }
        });
        manager.addExtraMaidBrain(com.github.xiaozhaoz1.littlemaidmoreaction.task.behavior.DefaultBehaviorBrain.INSTANCE);
        LittleMaidMoreAction.LOGGER.info("[LMA] ExtraMaidBrain 已注册 (NAV_TARGET, NAV_START_TICK + default behaviors)");
    }

    /** MOD 总线：客户端初始化 */
//? if 1.20.1 {
    @Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else {
    @EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?}
    public static final class ModClientEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                AnimationResourceRegistrar.scanCustomAnimations();
                // ★ loadClientDurations() 已移至 onClientReload — 此时 GeckoLibCache 尚未加载自定义动画，
                // 提前调用会导致 DURATIONS 为空、兜底动画误报"不存在"
                // 动画播放已迁移到 magic_casting 控制器 (LmaMagicCastingProvider)
                LittleMaidMoreAction.LOGGER.info("[LMA] 客户端动画数据加载完成");
            });
        }

        /** 注册内存虚拟资源包，为动画提供虚拟文件映射。 */
        @SubscribeEvent
        public static void onAddPackFinders(AddPackFindersEvent event) {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

            // 动画资源包 — 使用 StartupLoader 预扫描数据，避免独立目录扫描的时序问题
            var animRes = new DynamicAnimationResources(StartupLoader.getAnimationFiles());
//? if 1.20.1 {
            var animPack = Pack.readMetaAndCreate(
                    "lma_dynamic_animations",
                    Component.literal("LMA Custom Animations"),
                    false,
                    id -> animRes,
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN
            );
//?} else {
            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo info) { return animRes; }
                @Override public net.minecraft.server.packs.PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) { return animRes; }
            };
            PackLocationInfo info = new PackLocationInfo("lma_dynamic_animations", Component.literal("LMA Custom Animations"), PackSource.BUILT_IN, java.util.Optional.empty());
            Pack.Metadata metadata = new Pack.Metadata(Component.literal("LMA Custom Animations"), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), java.util.List.of(), false);
            PackSelectionConfig config = new PackSelectionConfig(false, Pack.Position.TOP, false);
            var animPack = new Pack(info, supplier, metadata, config);
//?}
            event.addRepositorySource(c -> c.accept(animPack));
            LittleMaidMoreAction.LOGGER.info("[LMA] 动画资源包已注册 ({} 个)", animRes.getAnimationFiles().size());
        }
    }
}

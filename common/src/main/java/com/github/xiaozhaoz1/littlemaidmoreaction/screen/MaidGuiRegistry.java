package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * v79.25: TLM 女仆 GUI 扩展注册门面 — 把 TLM {@code AbstractMaidContainerGui} 的按钮注入扩展点
 * (MaidContainerGuiEvent.Init) 封装成注册 API; 任务设置 tab 见 {@link #registerTaskConfig}。
 * <p>侧栏 index 2 = LMA 模组主界面入口 (LMAConfigScreen — 全局设置性质, 参照 TLM 侧栏
 * index 1 全局设置; 用户裁定: 单女仆设置 (女仆选择/属性) 进主界面, 不直接放 TLM 界面)。</p>
 * <p>位置: 侧栏 (leftPos+251, topPos+37+index*25, TLM MaidSideTabs 占用 index 0/1 → LMA 从 2 起);
 * 顶部 (leftPos+194, topPos+5+index*26, 参照 MaidAttributeDisplay)。
 * bus: 1.20 MinecraftForge.EVENT_BUS (FORGE) / 1.21 NeoForge.EVENT_BUS
 * (必须显式 Bus.GAME — MaidAttributeDisplay 1.21 移植版漏写, 默认 MOD bus 静默失效)。</p>
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
//?}
public final class MaidGuiRegistry {

    /** 注册条目: index 位置 + tooltip 两行 + onPress(按钮, 女仆) — onPress 经 maid 上下文实例化 */
    public record Spec(int index, Component title, Component desc, BiConsumer<Button, EntityMaid> onPress) {
    }

    private static final List<Spec> SIDE_BUTTONS = new ArrayList<>();
    private static final List<Spec> TOP_TABS = new ArrayList<>();

    private static final String SIDE_ID_PREFIX = LittleMaidMoreAction.MOD_ID + ":side_";
    private static final String TOP_ID_PREFIX = LittleMaidMoreAction.MOD_ID + ":top_";

    /** LMA 默认注册: 模组主界面 (侧栏 index 2, TLM 0/1 已占) — 外部女仆向 GUI 再 register* 追加 */
    static {
        registerSideButton(2,
                Component.translatable("gui.littlemaidmoreaction.main.button"),
                Component.translatable("gui.littlemaidmoreaction.main.button.desc"),
                (button, maid) -> Minecraft.getInstance().setScreen(
                        new LMAConfigScreen(Minecraft.getInstance().screen)));
    }

    private MaidGuiRegistry() {
    }

    /** 注册侧栏按钮 — index 从 2 起 (TLM MaidSideTabs 占用 0/1, SPACING 25) */
    public static void registerSideButton(int index, Component title, Component desc,
                                          BiConsumer<Button, EntityMaid> onPress) {
        SIDE_BUTTONS.add(new Spec(index, title, desc, onPress));
    }

    /** 注册顶部 tab — index 从 0 起 (SPACING 26) */
    public static void registerTopTab(int index, Component title, Component desc,
                                      BiConsumer<Button, EntityMaid> onPress) {
        TOP_TABS.add(new Spec(index, title, desc, onPress));
    }

    /** 注册任务配置 GUI (TLM 任务设置 tab 内容) — 委托 {@link TaskConfigGuiFactory#register}。
     *  <p>注册优先, 既有 Pipeline 覆写 getConfigGuiProvider 兜底; 注册表按 taskType 查,
     *  不依赖 lma_flow_task 写入时序 (v67.12 语义)。</p> */
    public static void registerTaskConfig(String taskType,
                                          Function<EntityMaid, MenuProvider> provider) {
        TaskConfigGuiFactory.register(taskType, provider);
    }

    /** TLM 女仆容器屏按钮注入 (事件入口, 注册表驱动) */
    @SubscribeEvent
    public static void onMaidGuiInit(MaidContainerGuiEvent.Init event) {
        EntityMaid maid = event.getGui().getMaid();
        if (maid == null) {
            return;
        }
        int leftPos = event.getLeftPos();
        int topPos = event.getTopPos();
        for (Spec spec : SIDE_BUTTONS) {
            event.addButton(SIDE_ID_PREFIX + spec.index(), createSideButton(spec, leftPos, topPos, true,
                    button -> spec.onPress().accept(button, maid)));
        }
        for (Spec spec : TOP_TABS) {
            event.addButton(TOP_ID_PREFIX + spec.index(), createTopButton(spec, leftPos, topPos, true,
                    button -> spec.onPress().accept(button, maid)));
        }
    }

    /** 侧栏按钮工厂: (leftPos+251, topPos+37+index*25) */
    public static MaidListButton createSideButton(Spec spec, int leftPos, int topPos, boolean active,
                                                  Button.OnPress onPress) {
        return new MaidListButton(leftPos + 251, topPos + 37 + spec.index() * 25, MaidListButton.Kind.SIDE,
                active, onPress, List.of(spec.title(), spec.desc()));
    }

    /** 顶部 tab 工厂: (leftPos+194, topPos+5+index*26) */
    public static MaidListButton createTopButton(Spec spec, int leftPos, int topPos, boolean active,
                                                 Button.OnPress onPress) {
        return new MaidListButton(leftPos + 194, topPos + 5 + spec.index() * 26, MaidListButton.Kind.TAB,
                active, onPress, List.of(spec.title(), spec.desc()));
    }
}

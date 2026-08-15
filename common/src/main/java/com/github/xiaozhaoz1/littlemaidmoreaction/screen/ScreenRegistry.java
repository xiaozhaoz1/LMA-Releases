package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.ConfigSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * v79.51: Screen 打开入口统一注册表 — 收敛散落的 5 处打开点:
 * ① LMAConfigScreen 6 硬编码按钮 → 本表枚举 (注册序 = 按钮显示序);
 * ② MaidGuiRegistry 侧栏条目 (TLM 女仆 GUI) → {@link #open("lma_config", parent)};
 * ③ MaidCodexScreenPacket.opener 双平台赋值 → {@link #openCodex} 方法引用
 *    (赋值点仍在双平台入口 — 保 boot 期就绪, 图鉴包先到不丢屏);
 * ④ 双平台 IConfigScreenFactory → {@link #create("lma_config", parent)}。
 *
 * <p>打开入口表 ({@link #registerOpener}) 与 LMAConfigScreen 按钮表
 * ({@link #registerConfigButton}) 分置; 按钮条目含 2 个非打开类按钮
 * (debug 开关/返回父屏), onPress 统一收 {@code BiConsumer&lt;Button, LMAConfigScreen&gt;}
 * (按钮 + 屏实例, 静态注册不捕获实例; 打开类按钮以屏实例为子屏 parent,
 * done 经包私有 {@code parent} 字段取原始父屏)。
 *
 * <p>静态表只存函数引用 (KeyTriggerRegistry/TaskRegistry 同模式) — 无实体引用,
 * 免 MaidUnloadRegistry 登记 (红线 #7 语义不触)。
 */
public final class ScreenRegistry {

    /** 打开入口表: name → 打开工厂 (父屏 → 新屏) — LinkedHashMap 注册序稳定 */
    private static final Map<String, Function<Screen, Screen>> OPENERS = new LinkedHashMap<>();

    /** LMAConfigScreen 按钮表 — 注册序 = 按钮显示序 */
    private static final List<ConfigButton> CONFIG_BUTTONS = new ArrayList<>();

    /** LMAConfigScreen 按钮条目: name + 标题 (lang key) + onPress(按钮, 屏实例)。
     *  onPress 的 Screen 参数 = LMAConfigScreen 实例 (打开类按钮作子屏 parent,
     * done 经包私有 {@code LMAConfigScreen.parent} 取原始父屏)。 */
    public record ConfigButton(String name, Component title, BiConsumer<Button, LMAConfigScreen> onPress) {
    }

    static {
        // ---- 打开入口: 模组主界面 (侧栏 index 2 + 模组列表 IConfigScreenFactory 共用) ----
        registerOpener("lma_config", LMAConfigScreen::new);

        // ---- LMAConfigScreen 按钮 (位置/尺寸由屏内按注册序排版: 常规列 + done 独立位) ----
        // 详细设置 (Cloth Config 软依赖 — 未安装时提示而非崩溃, 崩溃实测 crash-2026-08-02_20.08.56)
        registerConfigButton("cloth_detail",
                Component.translatable("gui.littlemaidmoreaction.config.detail"),
                (btn, parent) -> {
                    if (!isClothLoaded()) {
                        var player = Minecraft.getInstance().player;
                        if (player != null) {
                            player.displayClientMessage(
                                    Component.translatable("gui.littlemaidmoreaction.config.cloth_missing"), false);
                        }
                        return;
                    }
                    Minecraft.getInstance().setScreen(ClothSettingsScreen.create(parent));
                });
        // 任务树入口
        registerConfigButton("task_tree",
                Component.translatable("gui.littlemaidmoreaction.config.task_tree"),
                (btn, parent) -> Minecraft.getInstance().setScreen(new TaskTreeScreen(parent)));
        // 女仆独立选择入口 (独立屏, 非 TLM 容器屏)
        registerConfigButton("maid_list",
                Component.translatable("gui.littlemaidmoreaction.maid_list.button"),
                (btn, parent) -> Minecraft.getInstance().setScreen(new MaidListScreen(parent)));
        // 兼容模块开关 (注册期生效 — 重启后应用)
        registerConfigButton("compat",
                Component.translatable("gui.littlemaidmoreaction.config.compat"),
                (btn, parent) -> Minecraft.getInstance().setScreen(new CompatConfigScreen(parent)));
        // 调试开关 (非打开类按钮 — onPress 内更新按钮文案)
        registerConfigButton("debug",
                Component.translatable("gui.littlemaidmoreaction.config.debug",
                        MoreActionConfig.DEBUG_MODE.get() ? "ON" : "OFF"),
                (btn, parent) -> {
                    boolean v = !MoreActionConfig.DEBUG_MODE.get();
                    MoreActionConfig.DEBUG_MODE.set(v);
                    MoreActionConfig.saveAll();
                    if (!Minecraft.getInstance().hasSingleplayerServer()) {
                        ConfigSyncPacket.send();
                    }
                    btn.setMessage(Component.translatable("gui.littlemaidmoreaction.config.debug",
                            v ? "ON" : "OFF"));
                });
        // 返回原始父屏 (打开本屏的屏 — 非屏实例自身; 独立位 — 屏内按注册序末位定位)
        registerConfigButton("done",
                Component.translatable("gui.littlemaidmoreaction.config.done"),
                (btn, screen) -> Minecraft.getInstance().setScreen(screen.parent));
    }

    private ScreenRegistry() {
    }

    /** 打开入口注册 — 重复 name 直接抛 (防静默覆盖, KeyTriggerRegistry 同风格) */
    public static void registerOpener(String name, Function<Screen, Screen> opener) {
        if (OPENERS.containsKey(name)) {
            throw new IllegalArgumentException("Screen opener 重复注册: " + name);
        }
        OPENERS.put(name, opener);
    }

    /** 打开入口 (有父屏上下文) — 未注册静默 (注册在 static init, 首用前必就绪) */
    public static void open(String name, Screen parent) {
        Function<Screen, Screen> opener = OPENERS.get(name);
        if (opener != null) {
            Minecraft.getInstance().setScreen(opener.apply(parent));
        }
    }

    /** 打开入口工厂 (IConfigScreenFactory 用 — 返回新屏而非 setScreen); 未注册返回 null */
    public static Screen create(String name, Screen parent) {
        Function<Screen, Screen> opener = OPENERS.get(name);
        return opener != null ? opener.apply(parent) : null;
    }

    /** 图鉴屏打开 (MaidCodexScreenPacket.opener 实现收敛于此 — 赋值点保留在双平台入口) */
    public static void openCodex(Map<String, Integer> counts) {
        Minecraft.getInstance().setScreen(new MaidCodexScreen(counts));
    }

    /** LMAConfigScreen 按钮注册 — 追加到显示序尾 */
    public static void registerConfigButton(String name, Component title, BiConsumer<Button, LMAConfigScreen> onPress) {
        CONFIG_BUTTONS.add(new ConfigButton(name, title, onPress));
    }

    /** LMAConfigScreen 按钮枚举 (注册序, 只读副本) */
    public static List<ConfigButton> configButtons() {
        return List.copyOf(CONFIG_BUTTONS);
    }

    /** Cloth Config 软依赖检测 (未安装时详细设置不可用 — 双平台) */
    private static boolean isClothLoaded() {
//? if 1.20.1 {
        return net.minecraftforge.fml.ModList.get().isLoaded("cloth_config");
//?} else {
        return net.neoforged.fml.ModList.get().isLoaded("cloth_config");
//?}
    }
}

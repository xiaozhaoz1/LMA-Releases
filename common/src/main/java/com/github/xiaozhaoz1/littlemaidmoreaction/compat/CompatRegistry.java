package com.github.xiaozhaoz1.littlemaidmoreaction.compat;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.VanillaCompat;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.ModList;
//?} else {
import net.neoforged.fml.ModList;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
//?} else {
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
//?}

/**
 * 兼容模块调度中心 (v73 清理: 规则引擎时代死代码已删)。
 *
 * <p><b>v73 清理项</b>:
 * <ul>
 *   <li>CompatScanner 扫描机制 — 扫描目标包 (compat/vanilla/*, compat/create/impl/*) 随规则引擎
 *       impl 裁撤全部为空, 纯死路径 (CompatScanner.java 已删)</li>
 *   <li>CreateCompat 扫描 (同空包, 已删) — create 任务 (Crank/Power 等) 不受影响,
 *       走 TaskRegistry static 块 ModList 门控独立注册</li>
 *   <li>ysm 条件/动作映射 (COMPAT_CONDITION_KEYS/ACTION_TYPES/detectCompat) — 规则 GUI
 *       已删, 无人消费</li>
 * </ul>
 *
 * <p>保留: 无条件 VanillaCompat.init (女仆编辑器 + 被动任务) + checkModLoad 门控
 * (未来 compat 恢复用, 如 neoforge Create)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
//?} else {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?}
public final class CompatRegistry {

    /** v35.5: 防止 scanAllCompatEarly() + onEnqueue() 双重初始化 */
    private static volatile boolean earlyScanned = false;

    /** v77: 可开关兼容模块表 (显式注册 — 注解扫描路径未启用, TaskConditionRegistration 先例) */
    public record CompatModule(String id, String name, String description, String modId) {}

    private static final java.util.List<CompatModule> MODULES = new java.util.ArrayList<>();

    static {
        registerModule("create", "机械动力 Create",
                "女仆专属任务: 曲柄/动力/压块/搅拌/跑步带/便携装配 + 发电皮带方块", "create");
        registerModule("numen", "言出法随 Numen",
                "AI 操控任务 (ai_control) — 依赖 Numen 提供 AI 对话来源", "numen");
//? if 1.20.1 {
        registerModule("createbigcannons", "Create Big Cannons",
                "速射炮闩装填任务 (cannon_load) — 1.20.1 专属", "createbigcannons");
//?}
    }

    public static void registerModule(String id, String name, String description, String modId) {
        MODULES.add(new CompatModule(id, name, description, modId));
    }

    public static java.util.List<CompatModule> getAllModules() {
        return java.util.List.copyOf(MODULES);
    }

    /**
     * 提前初始化 compat 模块（mod 构造器中调用）。
     * 幂等 — earlyScanned 守卫防止重复执行副作用。
     */
    public static void scanAllCompatEarly() {
        if (earlyScanned) return;
        earlyScanned = true;
        doScan();
    }

    @SubscribeEvent
    public static void onEnqueue(final InterModEnqueueEvent event) {
        event.enqueueWork(() -> {
            // ★ v35.5: 若 scanAllCompatEarly() 已扫描则跳过，避免双重初始化
            if (earlyScanned) return;
            earlyScanned = true;
            doScan();
        });
    }

    /** 执行 compat 初始化 (原版功能无条件; 未来 compat 在此加 checkModLoad) */
    private static void doScan() {
        // v77: 兼容模块开关 — 构造期显式加载 (TaskRegistry static 块门控依赖此时序: 本行早于一切门控点)
        CompatToggle.load();
        VanillaCompat.init();
    }

    /** mod 存在性门控 (未来 compat 恢复用) */
    private static void checkModLoad(String modId, Runnable runnable) {
        if (ModList.get().isLoaded(modId)) {
            runnable.run();
        }
    }

    private CompatRegistry() {}
}

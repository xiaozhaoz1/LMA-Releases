package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.IEventBus;
//?} else {
import net.neoforged.bus.api.IEventBus;
//?}

/**
 * LMA 初始化编排器 (v10 → v72 Phase 5: 规则引擎退役, 纯任务系统)。
 *
 * <p>从 @Mod 构造器提取，将扫描/加载/文档生成集中管理。
 * 所有初始化操作在构造器中同步执行（Forge 要求注册在 mod 构造器完成前）。</p>
 */
public final class LmaRegistrar {

    /** 核心初始化 — 扫描注册 + 任务条件/动作注册 + JSON 任务加载装配 */
    public static void init() {
        // 扫描注册 (注解扫描路径未启用 — 任务条件/动作走显式注册)

        // ★ Bug #68 fix: 提前扫描 compat 模块，确保注册时序正确
        com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatRegistry.scanAllCompatEarly();

        // 启动加载器：创建目录 → 复制 jar 预设 → 扫描 config 目录
        StartupLoader.load();
        // 节日表 (v79.47): jar 预设复制 → config 读取 → FestivalTable 注入 (日历检测器消费)
        com.github.xiaozhaoz1.littlemaidmoreaction.storage.FestivalLoader.load();
        // v79.51 (KeyTrigger): 通用按键触发注册表 — 首个消费者 block_interact (引擎级任务分发)
        com.github.xiaozhaoz1.littlemaidmoreaction.network.KeyTriggerRegistry.init();
    }

    /** 服务端初始化 (DocGenerator 随条件栈删除 — 空实现保留签名) */
    public static void initServer() {
    }

    /** 注册音效 DeferredRegister 到 MOD 总线 */
    public static void registerSounds(IEventBus modBus) {
        LmaSounds.SOUNDS.register(modBus);
    }

    /** P0: 注册 MemoryModuleType DeferredRegister */
    public static void registerMemoryModules(IEventBus modBus) {
        com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaMemoryModuleRegistry.register(modBus);
    }

    /** 注册方块 DeferredRegister */
    public static void registerBlocks(IEventBus modBus) {
        LmaBlocks.register(modBus);
    }

    /** 注册方块实体 DeferredRegister */
    public static void registerBlockEntityTypes(IEventBus modBus) {
        LmaBlockEntityTypes.register(modBus);
    }

    /** 注册物品 DeferredRegister (LMA 首个物品注册点 — 女仆饰品) */
    public static void registerItems(IEventBus modBus) {
        LmaItems.register(modBus);
    }

    private LmaRegistrar() {}
}

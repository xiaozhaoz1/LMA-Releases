package littlemaidmoreaction.littlemaidmoreaction.init;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.BuiltinRegistrar;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ClassScanner;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ForgeClassScanner;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;
import littlemaidmoreaction.littlemaidmoreaction.storage.StartupLoader;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * LMA 初始化编排器 (v10)。
 *
 * <p>从 @Mod 构造器提取，将扫描/加载/文档生成集中管理。
 * 所有初始化操作在构造器中同步执行（Forge 要求注册在 mod 构造器完成前）。</p>
 */
public final class LmaRegistrar {

    /** 核心初始化 — 扫描注册 + 规则加载 + 启动加载 */
    public static void init() {
        // 三层注册回退 — ① Forge原生 → ② ClassGraph → ③ 显式硬编码
        ForgeClassScanner.scanAll();
        if (ConditionRegistry.size() == 0 || ActionRegistry.size() == 0) {
            ClassScanner.scanAll();
        }
        if (ConditionRegistry.size() == 0) {
            BuiltinRegistrar.registerAllConditions();
        }
        if (ActionRegistry.size() == 0) {
            BuiltinRegistrar.registerAllActions();
        }

        // ★ Bug #68 fix: 提前扫描 compat 模块，确保规则加载时所有动作/条件已注册
        littlemaidmoreaction.littlemaidmoreaction.compat.CompatRegistry.scanAllCompatEarly();

        // 加载规则引擎存储（首次启动生成预设）
        RuleActionStorage.load();
        // 启动加载器：创建目录 → 复制 jar 预设 → 扫描 config 目录
        StartupLoader.load();
    }

    /** 服务端初始化 — 任务执行器 + 文档 + 版本门控 */
    public static void initServer() {
        littlemaidmoreaction.littlemaidmoreaction.core.engine.ServerTaskExecutor.init();
        littlemaidmoreaction.littlemaidmoreaction.core.doc.DocGenerator.generateAll(
                LittleMaidMoreAction.CONFIG_DIR.resolve("introduce"));
        littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.TlmVersionedEvents.register();
    }

    /** 注册音效 DeferredRegister 到 MOD 总线 */
    public static void registerSounds(IEventBus modBus) {
        LmaSounds.SOUNDS.register(modBus);
    }

    /** ★ v12.7 P0: 注册 MemoryModuleType DeferredRegister */
    public static void registerMemoryModules(IEventBus modBus) {
        littlemaidmoreaction.littlemaidmoreaction.core.memory.LmaMemoryModuleRegistry.register(modBus);
    }

    private LmaRegistrar() {}
}

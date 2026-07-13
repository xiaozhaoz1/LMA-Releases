package littlemaidmoreaction.littlemaidmoreaction.core.registry;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.ScriptPlugin;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.script.ScriptPluginRegistry;

/**
 * 类路径扫描器 — 使用 ClassGraph 在模组初始化时自动发现注解标记的扩展。
 *
 * <p>替代旧架构中的 static{} 手动注册模式。
 * 扫描范围限定在本模组包内（littlemaidmoreaction），避免扫描整个类路径。</p>
 *
 * <p>Minecraft Forge 环境下禁用了 JPMS 模块扫描，
 * 因为 Forge 的自定义类加载器不支持 Module API（ModuleReader.list()）。</p>
 */
public final class ClassScanner {

    private static final String BASE_PACKAGE = "littlemaidmoreaction";

    /**
     * 一次性执行全部扫描。
     *
     * <p>单次 scan() 调用收集所有三种注解类型，避免重复扫描。
     * .ignoreModuleScanning() 解决 Forge 环境下
     * "Could not call moduleReader.list()" 的 ClassGraphException。</p>
     */
    public static void scanAll() {
        long startMs = System.currentTimeMillis();

        try (ScanResult scan = new ClassGraph()
                .enableAnnotationInfo()
                .enableClassInfo()
                .overrideClassLoaders(ClassScanner.class.getClassLoader())  // 绕过 Forge 的 Module API
                .acceptPackages(BASE_PACKAGE)
                .scan()) {

            // 注册所有 @RuleCondition
            for (ClassInfo ci : scan.getClassesWithAnnotation(RuleCondition.class)) {
                registerCondition(ci);
            }

            // 注册所有 @RuleAction
            for (ClassInfo ci : scan.getClassesWithAnnotation(RuleAction.class)) {
                registerAction(ci);
            }

            // 记录所有 @ScriptPlugin
            ScriptPluginRegistry.clear();
            for (ClassInfo ci : scan.getClassesWithAnnotation(ScriptPlugin.class)) {
                ScriptPluginRegistry.markDiscovered(ci.getName());
                if (MoreActionConfig.DEBUG_MODE.get()) {
                    LittleMaidMoreAction.LOGGER.debug(
                        "[ClassScanner] 发现脚本插件: {}", ci.getSimpleName());
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        LittleMaidMoreAction.LOGGER.info(
            "[ClassScanner] 扫描完成: {} 条件 + {} 动作 + {} 脚本插件 ({}ms)",
            ConditionRegistry.size(), ActionRegistry.size(),
            ScriptPluginRegistry.getDiscovered().size(), elapsed);
    }

    /** 实例化并注册单个条件。 */
    private static void registerCondition(ClassInfo ci) {
        try {
            Class<?> clazz = Class.forName(ci.getName());
            if (ICondition.class.isAssignableFrom(clazz)) {
                ICondition cond = (ICondition) clazz.getDeclaredConstructor().newInstance();
                ConditionRegistry.register(cond);
                if (MoreActionConfig.DEBUG_MODE.get()) {
                    LittleMaidMoreAction.LOGGER.debug(
                        "[ClassScanner] 注册条件: {} → {}", cond.key(), ci.getSimpleName());
                }
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.error(
                "[ClassScanner] 条件实例化失败: {}", ci.getName(), e);
        }
    }

    /** 实例化并注册单个动作。 */
    private static void registerAction(ClassInfo ci) {
        try {
            Class<?> clazz = Class.forName(ci.getName());
            if (IAction.class.isAssignableFrom(clazz)) {
                IAction action = (IAction) clazz.getDeclaredConstructor().newInstance();
                ActionRegistry.register(action);
                if (MoreActionConfig.DEBUG_MODE.get()) {
                    LittleMaidMoreAction.LOGGER.debug(
                        "[ClassScanner] 注册动作: {} → {}", action.id(), ci.getSimpleName());
                }
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.error(
                "[ClassScanner] 动作实例化失败: {}", ci.getName(), e);
        }
    }

    private ClassScanner() {}
}

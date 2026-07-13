package littlemaidmoreaction.littlemaidmoreaction.core.spi.script;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 脚本插件注册表 — ClassScanner 在此记录 {@code @ScriptPlugin} 标注的类名。
 *
 * <p>{@link littlemaidmoreaction.littlemaidmoreaction.core.annotation.ScriptPlugin ScriptPlugin}
 * 注解的编译期扫描结果，通过 {@link #markDiscovered(String)} 登记到此注册表。
 * {@code ScriptEngineManager} 在运行时通过反射懒加载。无需启动时实例化所有引擎。</p>
 *
 * <p>线程安全要求：{@link LinkedHashSet} 非线程安全，但扫描阶段在单线程中进行；
 * 运行时仅读取，无需同步。</p>
 */
public final class ScriptPluginRegistry {
    private static final Set<String> DISCOVERED = new LinkedHashSet<>();

    /**
     * 标记一个 {@code @ScriptPlugin} 类已被扫描发现。
     *
     * @param className 被注解标记的类的全限定名
     */
    public static void markDiscovered(String className) {
        DISCOVERED.add(className);
    }

    /**
     * 获取所有已发现的脚本插件类名。
     *
     * @return 不可变的已发现类名集合
     */
    public static Set<String> getDiscovered() {
        return Set.copyOf(DISCOVERED);
    }

    /** 清空已发现列表（通常用于测试重置或热重载）。 */
    public static void clear() {
        DISCOVERED.clear();
    }

    private ScriptPluginRegistry() {
    }
}

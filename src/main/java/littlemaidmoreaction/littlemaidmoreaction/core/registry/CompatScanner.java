package littlemaidmoreaction.littlemaidmoreaction.core.registry;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import net.minecraftforge.common.MinecraftForge;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.Collections;

/**
 * 通用 Compat 扫描器 — 从 compat 模块的限定子包中扫描并注册条件/动作/事件。
 *
 * <p>替代每个 Compat 类手写 200 行 JDK NIO 扫描逻辑。
 * 新 compat 模块只需调用 {@link #scan(Class, String, String, String)} 即可。</p>
 *
 * <h3>三扩展目录</h3>
 * <ul>
 *   <li><b>condition/</b> — 实现 {@link ICondition} + 带 {@link RuleCondition} → {@link ConditionRegistry}</li>
 *   <li><b>action/</b>    — 实现 {@link IAction}    + 带 {@link RuleAction}    → {@link ActionRegistry}</li>
 *   <li><b>event/</b>     — 任意类 → {@link MinecraftForge#EVENT_BUS}</li>
 * </ul>
 *
 * <p>任意子包路径传 {@code null} 则跳过该类型扫描。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * CompatScanner.ScanResult r = CompatScanner.scan(MyCompat.class,
 *     "compat/mymod/impl/condition/",
 *     "compat/mymod/impl/action/",
 *     "compat/mymod/impl/event/");
 * LOGGER.info("+{}c +{}a +{}e", r.conditions(), r.actions(), r.events());
 * }</pre>
 */
public final class CompatScanner {

    private CompatScanner() {}

    /** 扫描结果。 */
    public record ScanResult(int conditions, int actions, int events) {
        public int total() { return conditions + actions + events; }
    }

    /**
     * 扫描并注册 compat 模块的三类扩展。
     *
     * @param compatClass  compat 入口类（用于定位 CodeSource + ClassLoader）
     * @param conditionPkg 条件子包路径（如 {@code "compat/mymod/impl/condition/"}），null 跳过
     * @param actionPkg    动作子包路径，null 跳过
     * @param eventPkg     事件子包路径，null 跳过
     * @return 各类注册数量
     */
    public static ScanResult scan(Class<?> compatClass,
                                   String conditionPkg,
                                   String actionPkg,
                                   String eventPkg) {
        CodeSource cs = compatClass.getProtectionDomain().getCodeSource();
        if (cs == null) {
            LittleMaidMoreAction.LOGGER.error(
                "[CompatScanner] Cannot get CodeSource from {}", compatClass.getSimpleName());
            return new ScanResult(0, 0, 0);
        }

        URL location = cs.getLocation();
        LittleMaidMoreAction.LOGGER.info("[CompatScanner] scanning from: {}", location);

        int conds = 0, acts = 0, evts = 0;
        try {
            URI uri = location.toURI();
            boolean isJar = location.getPath().endsWith(".jar");

            Path root = isJar ? openJarRoot(uri) : Path.of(uri);
            try {
                if (conditionPkg != null) {
                    conds = scanType(root, conditionPkg, compatClass,
                            ICondition.class, RuleCondition.class, true);
                }
                if (actionPkg != null) {
                    acts = scanType(root, actionPkg, compatClass,
                            IAction.class, RuleAction.class, false);
                }
                if (eventPkg != null) {
                    evts = scanEvents(root, eventPkg, compatClass);
                }
            } finally {
                if (isJar && root.getFileSystem() != null) {
                    try { root.getFileSystem().close(); } catch (IOException ignored) {}
                }
            }
        } catch (IOException | URISyntaxException e) {
            LittleMaidMoreAction.LOGGER.error("[CompatScanner] scan failed", e);
        }

        return new ScanResult(conds, acts, evts);
    }

    // ─── JAR 根路径 ──────────────────────────────────────────────

    private static Path openJarRoot(URI jarUri) throws IOException {
        try {
            return FileSystems.getFileSystem(jarUri).getPath("/");
        } catch (Exception e) {
            return FileSystems.newFileSystem(jarUri, Collections.emptyMap()).getPath("/");
        }
    }

    // ─── 条件/动作扫描 ───────────────────────────────────────────

    /**
     * 扫描指定子包中的条件或动作类。
     *
     * @param root       扫描根（JAR根 或 目录）
     * @param subPkg     子包路径（如 "compat/ysm/impl/condition/"）
     * @param compatClass compat 入口类（提供 ClassLoader）
     * @param spiType    接口类型 ({@code ICondition.class} 或 {@code IAction.class})
     * @param annoType   注解类型 ({@code RuleCondition.class} 或 {@code RuleAction.class})
     * @param isCondition true=条件, false=动作（决定注册目标）
     */
    @SuppressWarnings("unchecked")
    private static int scanType(Path root, String subPkg, Class<?> compatClass,
                                 Class<?> spiType, Class<?> annoType,
                                 boolean isCondition) throws IOException {
        String typeName = isCondition ? "condition" : "action";
        Path pkgPath = root.resolve(subPkg);
        if (!Files.exists(pkgPath)) {
            LittleMaidMoreAction.LOGGER.debug(
                "[CompatScanner] {} pkg not found: {}", typeName, subPkg);
            return 0;
        }

        int[] count = {0};
        Files.walkFileTree(pkgPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String pathStr = file.toString().replace('\\', '/');
                if (!pathStr.endsWith(".class")) return FileVisitResult.CONTINUE;

                String fqcn = pathToFqcn(pathStr);
                if (fqcn == null || fqcn.contains("$")) return FileVisitResult.CONTINUE;

                try {
                    Class<?> clazz = Class.forName(fqcn, false,
                            compatClass.getClassLoader());
                    if (!spiType.isAssignableFrom(clazz)) return FileVisitResult.CONTINUE;
                    if (!clazz.isAnnotationPresent((Class<? extends java.lang.annotation.Annotation>) annoType))
                        return FileVisitResult.CONTINUE;

                    Object instance = clazz.getDeclaredConstructor().newInstance();

                    if (isCondition) {
                        ICondition cond = (ICondition) instance;
                        if (!ConditionRegistry.has(cond.key())) {
                            ConditionRegistry.register(cond);
                            count[0]++;
                            debug("register condition: {} → {}", cond.key(), clazz.getSimpleName());
                        }
                    } else {
                        IAction action = (IAction) instance;
                        if (!ActionRegistry.has(action.id())) {
                            ActionRegistry.register(action);
                            count[0]++;
                            debug("register action: {} → {}", action.id(), clazz.getSimpleName());
                        }
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError |
                         ExceptionInInitializerError e) {
                    // 类不可加载（依赖未就绪），静默跳过
                } catch (Exception e) {
                    LittleMaidMoreAction.LOGGER.warn(
                        "[CompatScanner] {} instantiation failed: {}", typeName, fqcn, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return count[0];
    }

    // ─── 事件扫描 ────────────────────────────────────────────────

    /**
     * 扫描指定子包中的事件处理类，实例化并注册到 Forge EVENT_BUS。
     *
     * <p>事件类约定：无参构造，在构造函数或 {@code @SubscribeEvent} 方法中
     * 调用 {@code RuleEngine.handleEvent()}。CompatScanner 只负责加载+注册。</p>
     */
    private static int scanEvents(Path root, String subPkg, Class<?> compatClass)
            throws IOException {
        Path pkgPath = root.resolve(subPkg);
        if (!Files.exists(pkgPath)) {
            LittleMaidMoreAction.LOGGER.debug(
                "[CompatScanner] event pkg not found: {}", subPkg);
            return 0;
        }

        int[] count = {0};
        Files.walkFileTree(pkgPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String pathStr = file.toString().replace('\\', '/');
                if (!pathStr.endsWith(".class")) return FileVisitResult.CONTINUE;

                String fqcn = pathToFqcn(pathStr);
                if (fqcn == null || fqcn.contains("$")) return FileVisitResult.CONTINUE;

                try {
                    Class<?> clazz = Class.forName(fqcn, false,
                            compatClass.getClassLoader());
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    MinecraftForge.EVENT_BUS.register(instance);
                    count[0]++;
                    debug("register event: {}", clazz.getSimpleName());
                } catch (ClassNotFoundException | NoClassDefFoundError |
                         ExceptionInInitializerError e) {
                    // 静默跳过
                } catch (Exception e) {
                    LittleMaidMoreAction.LOGGER.warn(
                        "[CompatScanner] event instantiation failed: {}", fqcn, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return count[0];
    }

    // ─── 工具 ────────────────────────────────────────────────────

    /** 文件路径 → 全限定类名。 */
    private static String pathToFqcn(String pathStr) {
        int pkgStart = pathStr.indexOf("littlemaidmoreaction/littlemaidmoreaction");
        if (pkgStart < 0) return null;
        return pathStr.substring(pkgStart)
                .replace('/', '.')
                .replace(".class", "");
    }

    private static void debug(String fmt, Object... args) {
        if (MoreActionConfig.DEBUG_MODE.get()) {
            LittleMaidMoreAction.LOGGER.debug("[CompatScanner] " + fmt, args);
        }
    }
}

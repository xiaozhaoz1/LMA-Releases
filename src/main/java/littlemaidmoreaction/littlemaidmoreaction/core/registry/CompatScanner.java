package littlemaidmoreaction.littlemaidmoreaction.core.registry;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
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
import java.util.function.Consumer;

/**
 * 通用 Compat 扫描器 — v35.1: 零 MC 依赖, 事件注册回调通过 Consumer 注入。
 */
public final class CompatScanner {

    private CompatScanner() {}

    public record ScanResult(int conditions, int actions, int events) {
        public int total() { return conditions + actions + events; }
    }

    /** v35.1: 新增 eventRegistrar 参数 — 替代直接 MinecraftForge.EVENT_BUS.register() */
    public static ScanResult scan(Class<?> compatClass,
                                   String conditionPkg,
                                   String actionPkg,
                                   String eventPkg,
                                   Consumer<Object> eventRegistrar) {
        CodeSource cs = compatClass.getProtectionDomain().getCodeSource();
        if (cs == null) {
            LittleMaidMoreAction.LOGGER.error("[CompatScanner] Cannot get CodeSource from {}", compatClass.getSimpleName());
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
                if (conditionPkg != null)
                    conds = scanType(root, conditionPkg, compatClass, ICondition.class, RuleCondition.class, true);
                if (actionPkg != null)
                    acts = scanType(root, actionPkg, compatClass, IAction.class, RuleAction.class, false);
                if (eventPkg != null)
                    evts = scanEvents(root, eventPkg, compatClass, eventRegistrar);
            } finally {
                if (isJar && root.getFileSystem() != null)
                    try { root.getFileSystem().close(); } catch (IOException ignored) {}
            }
        } catch (IOException | URISyntaxException e) {
            LittleMaidMoreAction.LOGGER.error("[CompatScanner] scan failed", e);
        }
        return new ScanResult(conds, acts, evts);
    }

    private static Path openJarRoot(URI jarUri) throws IOException {
        try { return FileSystems.getFileSystem(jarUri).getPath("/"); }
        catch (Exception e) { return FileSystems.newFileSystem(jarUri, Collections.emptyMap()).getPath("/"); }
    }

    @SuppressWarnings("unchecked")
    private static int scanType(Path root, String subPkg, Class<?> compatClass,
                                 Class<?> spiType, Class<?> annoType, boolean isCondition) throws IOException {
        String typeName = isCondition ? "condition" : "action";
        Path pkgPath = root.resolve(subPkg);
        if (!Files.exists(pkgPath)) { debug("{} pkg not found: {}", typeName, subPkg); return 0; }
        int[] count = {0};
        Files.walkFileTree(pkgPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String pathStr = file.toString().replace('\\', '/');
                if (!pathStr.endsWith(".class")) return FileVisitResult.CONTINUE;
                String fqcn = pathToFqcn(pathStr);
                if (fqcn == null || fqcn.contains("$")) return FileVisitResult.CONTINUE;
                try {
                    Class<?> clazz = Class.forName(fqcn, false, compatClass.getClassLoader());
                    if (!spiType.isAssignableFrom(clazz)) return FileVisitResult.CONTINUE;
                    if (!clazz.isAnnotationPresent((Class<? extends java.lang.annotation.Annotation>) annoType))
                        return FileVisitResult.CONTINUE;
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    if (isCondition) {
                        ICondition cond = (ICondition) instance;
                        if (!ConditionRegistry.has(cond.key())) { ConditionRegistry.register(cond); count[0]++; }
                    } else {
                        IAction action = (IAction) instance;
                        if (!ActionRegistry.has(action.id())) { ActionRegistry.register(action); count[0]++; }
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError | ExceptionInInitializerError e) { /* skip */ }
                catch (Exception e) { LittleMaidMoreAction.LOGGER.warn("[CompatScanner] {} instantiation failed: {}", typeName, fqcn, e); }
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private static int scanEvents(Path root, String subPkg, Class<?> compatClass,
                                   Consumer<Object> eventRegistrar) throws IOException {
        Path pkgPath = root.resolve(subPkg);
        if (!Files.exists(pkgPath)) { debug("event pkg not found: {}", subPkg); return 0; }
        int[] count = {0};
        Files.walkFileTree(pkgPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String pathStr = file.toString().replace('\\', '/');
                if (!pathStr.endsWith(".class")) return FileVisitResult.CONTINUE;
                String fqcn = pathToFqcn(pathStr);
                if (fqcn == null || fqcn.contains("$")) return FileVisitResult.CONTINUE;
                try {
                    Class<?> clazz = Class.forName(fqcn, false, compatClass.getClassLoader());
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    if (eventRegistrar != null) eventRegistrar.accept(instance);
                    count[0]++; debug("register event: {}", clazz.getSimpleName());
                } catch (ClassNotFoundException | NoClassDefFoundError | ExceptionInInitializerError e) { /* skip */ }
                catch (Exception e) { LittleMaidMoreAction.LOGGER.warn("[CompatScanner] event instantiation failed: {}", fqcn, e); }
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private static String pathToFqcn(String pathStr) {
        int pkgStart = pathStr.indexOf("littlemaidmoreaction/littlemaidmoreaction");
        if (pkgStart < 0) return null;
        return pathStr.substring(pkgStart).replace('/', '.').replace(".class", "");
    }

    private static void debug(String fmt, Object... args) {
        if (MoreActionConfig.DEBUG_MODE.get()) LittleMaidMoreAction.LOGGER.debug("[CompatScanner] " + fmt, args);
    }
}

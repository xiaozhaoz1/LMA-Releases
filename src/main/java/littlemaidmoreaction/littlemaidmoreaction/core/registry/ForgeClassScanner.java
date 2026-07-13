package littlemaidmoreaction.littlemaidmoreaction.core.registry;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.io.IOException;
import java.lang.annotation.Annotation;
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
import java.util.jar.JarFile;

/**
 * Forge 原生类扫描器 — 直接扫描 mod 的 JAR/目录发现注解扩展。
 *
 * <p>替代 ClassGraph 成为主扫描路径。通过 {@code ProtectionDomain} 定位 mod 文件：
 * <ul>
 *   <li>开发环境：{@code build/classes/java/main/} 或 {@code bin/main/}</li>
 *   <li>生产环境：{@code mods/littlemaidmoreaction-X.Y.Z.jar}</li>
 * </ul>
 *
 * <p>扫描 {@code impl/condition/} 和 {@code impl/action/} 子包，
 * 找到 {@link RuleCondition @RuleCondition} 和 {@link RuleAction @RuleAction}
 * 注解类，实例化并注册到对应 Registry。</p>
 *
 * <p>零外部依赖 — 仅使用 JDK NIO 和 Forge 类加载器。</p>
 */
public final class ForgeClassScanner {

    private static final String IMPL_CONDITION_PKG = "littlemaidmoreaction.littlemaidmoreaction.impl.condition.";
    private static final String IMPL_ACTION_PKG    = "littlemaidmoreaction.littlemaidmoreaction.impl.action.";

    /** 路径分隔符对应的子包路径 */
    private static final String CONDITION_SUBPATH =
            "littlemaidmoreaction/littlemaidmoreaction/impl/condition/";
    private static final String ACTION_SUBPATH =
            "littlemaidmoreaction/littlemaidmoreaction/impl/action/";

    public static void scanAll() {
        long startMs = System.currentTimeMillis();
        int condCount = ConditionRegistry.size();
        int actCount = ActionRegistry.size();

        CodeSource cs = ForgeClassScanner.class.getProtectionDomain().getCodeSource();
        if (cs == null) {
            LittleMaidMoreAction.LOGGER.error("[ForgeClassScanner] Cannot get CodeSource");
            return;
        }

        URL location = cs.getLocation();
        LittleMaidMoreAction.LOGGER.info("[ForgeClassScanner] mod location: {}", location);

        try {
            URI uri = location.toURI();
            boolean isJar = location.getPath().endsWith(".jar");

            if (isJar) {
                scanJar(uri);
            } else {
                scanDirectory(Path.of(uri));
            }

            long elapsed = System.currentTimeMillis() - startMs;
            int newConds = ConditionRegistry.size() - condCount;
            int newActs = ActionRegistry.size() - actCount;
            LittleMaidMoreAction.LOGGER.info(
                "[ForgeClassScanner] scan done: +{} conditions +{} actions ({}ms)",
                newConds, newActs, elapsed);
        } catch (IOException | URISyntaxException e) {
            LittleMaidMoreAction.LOGGER.error("[ForgeClassScanner] scan failed", e);
        }
    }

    // ─── JAR 扫描 ─────────────────────────────────────────────

    private static void scanJar(URI jarUri) throws IOException {
        // FileSystem already-open check — avoid "FileSystem already exists" error
        try (FileSystem fs = getOrCreateFileSystem(jarUri)) {
            scanPath(fs.getPath("/"), true);
        }
    }

    private static FileSystem getOrCreateFileSystem(URI jarUri) throws IOException {
        try {
            return FileSystems.getFileSystem(jarUri);
        } catch (Exception e) {
            return FileSystems.newFileSystem(jarUri, Collections.emptyMap());
        }
    }

    // ─── 目录扫描 ─────────────────────────────────────────────

    private static void scanDirectory(Path root) throws IOException {
        scanPath(root, false);
    }

    // ─── 通用扫描逻辑 ─────────────────────────────────────────

    private static void scanPath(Path root, boolean isJar) throws IOException {
        if (!Files.exists(root)) return;

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String pathStr = file.toString().replace('\\', '/');
                processClassFile(pathStr);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ─── 类处理 ───────────────────────────────────────────────

    private static void processClassFile(String pathStr) {
        if (!pathStr.endsWith(".class")) return;

        // 确定包
        String fqcn;
        boolean isCond;
        if (pathStr.contains(CONDITION_SUBPATH)) {
            isCond = true;
        } else if (pathStr.contains(ACTION_SUBPATH)) {
            isCond = false;
        } else {
            return; // 不是条件也不是动作，跳过
        }

        // 路径 → FQCN
        // littlemaidmoreaction/littlemaidmoreaction/impl/condition/maid/Name.class
        // → littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid.Name
        int pkgStart = pathStr.indexOf("littlemaidmoreaction/littlemaidmoreaction");
        if (pkgStart < 0) return;
        fqcn = pathStr.substring(pkgStart)
                .replace('/', '.')
                .replace(".class", "");

        // 跳过内部类（$）
        if (fqcn.contains("$")) return;

        try {
            Class<?> clazz = Class.forName(fqcn, false,
                    ForgeClassScanner.class.getClassLoader());

            if (isCond && ICondition.class.isAssignableFrom(clazz)) {
                registerCondition(clazz);
            } else if (!isCond && IAction.class.isAssignableFrom(clazz)) {
                registerAction(clazz);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError | ExceptionInInitializerError e) {
            // class not loadable (Minecraft dependencies may not be ready), silently skip
        } catch (Exception e) {
            if (MoreActionConfig.DEBUG_MODE.get()) {
                LittleMaidMoreAction.LOGGER.debug(
                    "[ForgeClassScanner] failed to process class: {}", fqcn, e);
            }
        }
    }

    // ─── condition registration ────────────────────────────────

    private static void registerCondition(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(RuleCondition.class)) return;

        try {
            ICondition cond = (ICondition) clazz.getDeclaredConstructor().newInstance();
            if (!ConditionRegistry.has(cond.key())) {
                ConditionRegistry.register(cond);
                if (MoreActionConfig.DEBUG_MODE.get()) {
                    LittleMaidMoreAction.LOGGER.debug(
                        "[ForgeClassScanner] register condition: {} → {}", cond.key(), clazz.getSimpleName());
                }
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn(
                "[ForgeClassScanner] condition instantiation failed: {}", clazz.getSimpleName(), e);
        }
    }

    // ─── action registration ───────────────────────────────────

    private static void registerAction(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(RuleAction.class)) return;

        try {
            IAction action = (IAction) clazz.getDeclaredConstructor().newInstance();
            if (!ActionRegistry.has(action.id())) {
                ActionRegistry.register(action);
                if (MoreActionConfig.DEBUG_MODE.get()) {
                    LittleMaidMoreAction.LOGGER.debug(
                        "[ForgeClassScanner] register action: {} → {}", action.id(), clazz.getSimpleName());
                }
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn(
                "[ForgeClassScanner] action instantiation failed: {}", clazz.getSimpleName(), e);
        }
    }

    private ForgeClassScanner() {}
}

package com.github.xiaozhaoz1.littlemaidmoreaction.storage;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
//? if 1.20.1 {
import net.minecraftforge.fml.loading.FMLPaths;
//?} else {
import net.neoforged.fml.loading.FMLPaths;
//?}

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;

/**
 * 启动加载器 — 扫描 config 目录，注册动画/音效。
 *
 * <p>v5: 扫描式复制 + SoundEvent 注册与扫描分离。
 * <ul>
 *   <li>动画 — {@code config/littlemaidmoreaction/animations/*.animation.json}</li>
 *   <li>音效 — {@code config/littlemaidmoreaction/sounds/*.ogg}</li>
 * </ul>
 * 首次启动扫描 JAR 预设文件列表，检查 config 目录中是否存在，不存在则复制。
 * SoundEvent 注册仅在 {@link #load()} 中执行一次（Forge registry 未冻结），
 * {@link #reload()} 仅重新扫描目录，不重复注册。
 * </p>
 */
public final class StartupLoader {

    static final Path CONFIG_DIR = LittleMaidMoreAction.CONFIG_DIR;
    static final Path ANIM_DIR = CONFIG_DIR.resolve("animations");

    /** JAR 内置预设动画文件列表 — 见 {@link com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationPresetNames#SHIPPED}。
     *  <p>v79.70 精简 (用户裁定): 只保留**在用的两个** — 其余 6 个既无代码消费者、又随 TLM/GeckoLib
     *  校验收紧而**崩溃** (dodge.animation.json 的 `animation.flash1` 关键帧非升序 ⇒
     *  `Invalid keyframe data - multiple starting keyframes?`) ⇒ 见 {@code AnimationPresetNames#OBSOLETE}。 */
    public static final java.util.List<String> ANIM_PRESETS =
            com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationPresetNames.SHIPPED;

    /** (v79.70) 废弃预设清单已在 {@link com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationPresetNames#syncConfigDir}
     *  里**内联为"不在 JAR 就删"** — 见该类 javadoc (用户裁定: config 动画目录是 LMA 专属, 直接对照 JAR 清理) ✓ */

    // ★ SOUNDS DeferredRegister 已移至 init/LmaSounds.java

    private static final Set<String> LOADED = new LinkedHashSet<>();
    /** ★ volatile: modloading-worker-0 写入后 Render thread 立即可见。重新赋值而非 clear+put。 */
    private static volatile List<String> animFiles = List.of();

    // ======================== 公开入口 ========================

    /**
     * 模组构造器中调用。创建目录 → 扫描 JAR 预设复制到 config → 扫描目录。
     * ★ SoundEvent 实际注册由 {@code LittleMaidMoreAction} 构造器中
     *   RegisterEvent handler 负责（直接写入 BuiltInRegistries.SOUND_EVENT）。
     */
    public static void load() {
        try {
            Files.createDirectories(ANIM_DIR);
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/Startup] 创建目录失败", e);
            return;
        }
        syncConfigDir();   // ★ 必须先于 copy/scan: 清掉老存档残留的废弃预设 (畸形文件会崩 GeckoLib)
        copyPresetsFromJar();
        scanAnimations();
        LittleMaidMoreAction.LOGGER.info("[LMA/Startup] 加载完成 — {} 动画", animFiles.size());
    }

    /**
     * 热重载 — 重新扫描 config 目录，检测新增/删除的动画文件。
     * 由 {@code MoreActionAPI.onClientReload} 在资源重载时调用。
     */
    public static void reload() {
        LOADED.clear();
        syncConfigDir();  // 资源重载也是清残留的时机 (玩家可能手动放回)
        copyPresetsFromJar();      // 检查是否有新预设需要复制
        scanAnimations();
        LittleMaidMoreAction.LOGGER.info("[LMA/Startup] 重载完成 — {} 动画", animFiles.size());
    }

    /**
     * **废弃预设迁移** (v79.70) — 把 {@link #OBSOLETE_ANIM_PRESETS} 从动画目录**移动到 `removed/` 子目录**:
     * 不删除 (玩家若自行改过可找回 ✓), 但不再位于扫描目录 ⇒ 不再注册给 TLM/GeckoLib ⇒ **崩溃消失** ✓
     *
     * <p>为什么必须做: 老玩家的 config 目录里已有这 6 个文件, 只从 JAR 里删掉**不会**让它们消失
     * (JAR 里没有 ⇒ 也不会被覆盖), 而 `scanAnimations` 扫的是**目录** ⇒ 畸形文件继续被加载 ✗
     * (外部玩家崩溃案例 = 错题 #356)。
     *
     * @return 本次迁移的文件数 (幂等 — 迁移后目录里不再有, 后续为 0)
     */
    static int syncConfigDir() {
        java.util.List<String> removed = com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationPresetNames.syncConfigDir(ANIM_DIR);
        if (!removed.isEmpty()) {
            LittleMaidMoreAction.LOGGER.warn(
                "[LMA/Startup] 已清理 {} 个不在 JAR 内的动画预设 (废弃/残留文件会崩 GeckoLib — 错题 #356): {}",
                removed.size(), removed);
        }
        return removed.size();
    }

    /** 可测版本 (单元测试传临时目录) — 委托纯逻辑 {@link com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationPresetNames#syncConfigDir} */
    public static java.util.List<String> syncConfigDir(java.nio.file.Path animDir) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationPresetNames.syncConfigDir(animDir);
    }

    // ======================== 预设复制 (扫描式) ========================

    /**
     * 扫描 JAR 预设文件列表，对每个文件检查 config 目录中是否已存在，
     * 不存在则从 JAR 复制。
     */
    private static void copyPresetsFromJar() {
        int copied = 0;
        for (String file : ANIM_PRESETS) {
            if (copyIfMissing(ANIM_DIR, "animations/" + file, file)) copied++;
        }
        if (copied > 0) {
            try {
                if (MoreActionConfig.DEBUG_MODE.get()) {
                    LittleMaidMoreAction.LOGGER.debug("[LMA/Startup] 本次复制 {} 个预设文件", copied);
                }
            } catch (IllegalStateException ignored) {
                // mod 构造期 config 未加载
            }
        }
    }

    /**
     * 检查目标文件是否存在，不存在则从 JAR 资源复制。
     *
     * @param dir        目标目录
     * @param jarRelPath JAR 内相对路径（不含 {@code assets/modid/} 前缀），如 {@code "animations/execution.animation.json"}
     * @param targetName 目标文件名
     * @return true 如果本次执行了复制
     */
    private static boolean copyIfMissing(Path dir, String jarRelPath, String targetName) {
        Path target = dir.resolve(targetName);
        if (Files.exists(target)) return false;

        String fullJarPath = "assets/" + LittleMaidMoreAction.MOD_ID + "/" + jarRelPath;
        try (InputStream in = StartupLoader.class.getClassLoader().getResourceAsStream(fullJarPath)) {
            if (in == null) {
                LittleMaidMoreAction.LOGGER.warn("[LMA/Startup] JAR 中缺少预设资源: {}", fullJarPath);
                return false;
            }
            Files.copy(in, target);
            LittleMaidMoreAction.LOGGER.info("[LMA/Startup] 生成预设: {}", targetName);
            return true;
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/Startup] 复制预设失败: {} — {}", targetName, e.getMessage());
            return false;
        }
    }

    // ======================== 目录扫描 ========================

    /** 扫描 {@code config/animations/*.animation.json}，按文件名排序。★ 新建不可变 list 后 volatile 赋值。 */
    private static void scanAnimations() {
        if (!Files.isDirectory(ANIM_DIR)) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/Startup] 动画目录不存在: {}", ANIM_DIR);
            animFiles = List.of();
            return;
        }
        List<String> list = new ArrayList<>();
        try (Stream<Path> files = Files.list(ANIM_DIR)) {
            files.filter(p -> {
                        String fn = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return fn.endsWith(".animation.json") && Files.isRegularFile(p);
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        list.add(name);
                        LOADED.add("animation:" + name);
                    });
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/Startup] 扫描动画目录失败: {}", e.getMessage(), e);
        }
        animFiles = Collections.unmodifiableList(list);
    }

    // ======================== 查询接口 ========================

    /** ★ volatile 读取 — 已不可变，直接返回无需包装。 */
    public static List<String> getAnimationFiles() {
        return animFiles;
    }

    /** 动画目录 (config/littlemaidmoreaction/animations) — YSM 注入/重载监听按文件遍历读取 */
    public static Path getAnimDir() {
        return ANIM_DIR;
    }

    public static Set<String> getLoadedIds() {
        return Collections.unmodifiableSet(LOADED);
    }

}

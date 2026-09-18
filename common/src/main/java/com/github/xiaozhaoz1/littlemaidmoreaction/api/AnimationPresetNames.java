package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 动画预设清单 + **config 目录与 JAR 对齐** (v79.70 — 错题 #356)。
 *
 * <p><b>为什么单独一个类</b>: 清单要被**单元测试**直接断言 (守护: 清单 ↔ resources 一致), 而
 * {@link com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader} 的静态初始化依赖 FML
 * (`FMLPaths.CONFIGDIR`) ⇒ 纯 JUnit 下 `<clinit>` 抛异常 ✗ (实测 `ExceptionInInitializerError`)
 * ⇒ 纯数据 + 纯逻辑放这里 (零 MC/FML 依赖), 由 StartupLoader 调用 ✓
 *
 * <p><b>背景</b>: 用户裁定动画只保留 **haqi (哈气) + maimeng (卖萌)**; 其余 6 个既无代码消费者,
 * 又随 TLM/GeckoLib 校验收紧而**加载崩溃** (实证: `dodge.animation.json` 的 `animation.flash1`
 * 关键帧键序 `'1'` → `'-0.03'` → `0.3 …` 非升序 ⇒ `Invalid keyframe data - multiple starting keyframes?`) ✗
 *
 * <p><b>为什么还要清理 config 目录</b>: 老玩家 `config/littlemaidmoreaction/animations/` 里已有那 6 个文件;
 * 只从 JAR 里删掉**不会**让它们消失 (目录是玩家的, 且 copyPresetsFromJar 只补缺失文件), 而
 * `scanAnimations` 扫的是**目录** ⇒ 畸形文件继续被注册给 TLM/GeckoLib ⇒ **崩溃照旧** ✗
 */
public final class AnimationPresetNames {

    private AnimationPresetNames() {}

    /** 随包发布的动画预设 (== JAR 内 `assets/littlemaidmoreaction/animations/` 的文件, 由守护测试保证一致) */
    public static final List<String> SHIPPED = List.of(
            "haqi.animation.json",      // 哈气 (ISS 注册 — TLM geckolib 模型播放; YSM 通道走模型包同名动画)
            "maimeng.animation.json"    // 对主人哈气/卖萌 (YsmAnimInjector 注入 YSM 模型包)
    );

    /**
     * **config 动画目录 与 JAR 对齐** (v79.70, 用户裁定): 该目录是 LMA 专属 (只有本模组读它) ⇒ 直接
     * **删除所有不在 {@link #SHIPPED} 里的 `*.animation.json`** (旧版本残留 / 手工放入的废弃预设) ✓
     *
     * <p>只删 `*.animation.json` (不碰其它后缀); 幂等 (第二次返回 0) ✓
     *
     * @return 本次删除的文件名列表 (调用方打日志)
     */
    public static List<String> syncConfigDir(Path animDir) {
        List<String> removed = new ArrayList<>();
        if (animDir == null || !Files.isDirectory(animDir)) return removed;
        try (Stream<Path> files = Files.list(animDir)) {
            for (Path p : files.toList()) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString();
                if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".animation.json")) continue;
                if (SHIPPED.contains(name)) continue;
                try {
                    Files.delete(p);
                    removed.add(name);
                } catch (IOException ignored) {
                    // 单个删除失败不阻断 (下次 load/reload 重试) — 日志由调用方打
                }
            }
        } catch (IOException ignored) {
            // 目录读取失败 ⇒ 视作无变化
        }
        return removed;
    }
}

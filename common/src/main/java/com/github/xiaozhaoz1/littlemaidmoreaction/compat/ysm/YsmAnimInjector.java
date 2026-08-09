package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
//?} else {
import net.neoforged.api.distmarker.Dist;
//?}
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.OnlyIn;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.loading.FMLPaths;
//?} else {
import net.neoforged.fml.loading.FMLPaths;
//?}

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * YSM 动画注入器 (v79.18) — 客户端启动/资源重载时把 LMA 的动画注入 YSM 模型包 (幂等)。
 *
 * <p>YSM 轮盘动画 (playRouletteAnim 请求) 需要<b>三件套</b> (OpenYSM 2.6.6 反编译实证
 * {@code TouhouMaidModelHandler.activateRouletteAnimation → ModelProperties.getExtraAnimation}):
 * ① 动画定义 — {@code animations/extra.animation.json} (轮盘动画文件, wiki: "使用动画轮盘播放对应额外动画")
 * ② 声明表 — {@code ysm.json} 的 {@code properties.extra_animation} 加 {@code "haqi": "haqi"}
 *    (轮盘表 — 查不到名字不播, 缺此项 = 静默失败, 用户实测)
 *
 * <p>v79.20.4: 多源注入 — haqi (对女仆) + maimeng (对主人, 用户裁定) 双动画。
 * <b>v79.25 通用化</b> (用户裁定: "网络应该是通用的支持动画同步不是只支持单一动画同步"):
 * 源不再硬编码 4 文件 — 遍历 {@code config/littlemaidmoreaction/animations/} 全部动画文件
 * (磁盘读取, 与 AnimFileSyncPacket 网络同步落盘同路径) — 新动画文件放 config 零代码接入,
 * YSM 注入 + TLM 合并全自动; 轮盘声明表 key 从全部源文件 JSON 动态解析。
 * 遍历 {@code config/yes_steve_model/builtin/*} 全部模型包 (default + wine_fox/22 酒狐 — 用户女仆
 * = wine_fox/21_saint 圣女酒狐实证)。幂等: 已有 key 跳过; 失败仅日志; YSM 还原后自动重注入。</p>
 */
public final class YsmAnimInjector {

    /** 动画注入目标文件 (模型包 animations/ 目录下) — 仅 extra (轮盘动画文件, playRouletteAnim 实证查询;
     *  tlm.animation.json 是 TLM 行为动画 (gomoku 等), rouletteAnim 不查 — 曾作双保险, 未实证, 已删) */
    private static final String[] ANIM_TARGETS = {"extra.animation.json"};

    /**
     * v79.26.2 卡顿修复: 源文件指纹 (fileName+size+mtime 串) — 注入结果只依赖源文件内容,
     * 源只在 AnimSync 落盘/用户手改时变化。指纹不变 = 幂等注入必已完成 → 零磁盘 IO 跳过。
     * 实证: AnimFileSyncPacket 每包都调本方法, 旧实现每次全量注入 (22 模型包 × ~24 次
     * 文件读 + Gson 解析 ≈ 528 次磁盘 IO 在渲染线程 = 每包卡 5.5 秒, 7 包 40 秒 — 日志实证)。
     */
    private static String lastFingerprint = null;

    private YsmAnimInjector() {}

    /**
     * 遍历 builtin 全部模型包注入 (幂等)。目标缺失 (YSM 未装/路径变) → 仅 warn。
     * <p>容错 (v79.20.4c): mod construct 与 YSM 解压 builtin 包并行 — walk 中途目录可能被
     * 并发删除 (实测 NoSuchFileException: wine_fox/13_matured)。Files.walk 迭代抛
     * {@link java.io.UncheckedIOException} (RuntimeException 子类) — 原 catch(IOException) 抓不到
     * → 逃出构造器 → FML mod loading 失败崩溃。现 catch RuntimeException + 逐包容错;
     * 竞态漏注入由 YsmReloadListener 资源重载兜底。</p>
     */
    @OnlyIn(Dist.CLIENT)
    public static void injectHaqiIfNeeded() {
        Path builtin = FMLPaths.CONFIGDIR.get().resolve("yes_steve_model/builtin");
        if (!Files.isDirectory(builtin)) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/YsmInject] YSM builtin 目录不存在: {}", builtin);
            return;
        }
        // v79.26.2 卡顿修复: 指纹快检 — 源文件未变 (上次注入必已完成) → 零磁盘 IO 跳过。
        // 旧实现每次资源重载全量注入: 22 模型包 × ~24 次文件读 + Gson 解析 ≈ 528 次磁盘 IO
        // 在渲染线程 (AnimSync 每包卡 5.5 秒 × 7 包 = 40 秒 — 加载世界卡很久日志实证)。
        // 指纹在 builtin 就绪检查后计算 — 目录未就绪 (v79.20.4c 竞态) 时不缓存, 下次重试。
        String fp = sourceFingerprint();
        if (fp.equals(lastFingerprint)) {
            return;
        }
        lastFingerprint = fp;
        // 递归找含 animations/ 子目录的模型包 (default + wine_fox/<22 模型> — 两层结构实证)
        try (var stream = Files.walk(builtin, 3)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> Files.isDirectory(p.resolve("animations")))
                    .forEach(p -> {
                        try {
                            injectIntoModelPack(p);
                        } catch (RuntimeException e) {
                            // 目录并发消失等竞态 — 跳过该包, reload listener 兜底
                            LittleMaidMoreAction.LOGGER.warn("[LMA/YsmInject] 注入模型包失败 (可能被 YSM 并发更新): {}", p);
                        }
                    });
        } catch (IOException | RuntimeException e) {
            // 遍历失败 — 重置指纹, 下次 reload 重试 (竞态兜底, 错题 #123 同族)
            lastFingerprint = null;
            LittleMaidMoreAction.LOGGER.error("[LMA/YsmInject] 遍历 YSM builtin 失败", e);
        }
    }

    /**
     * 源文件指纹 — 全部源文件 (fileName+size+mtime) 串。只 stat 不读内容 (8 次 lstat 远快于
     * 528 次内容读)。源文件仅在 AnimSync 落盘/用户手改时变化 — 指纹不变 = 注入结果不变。
     */
    private static String sourceFingerprint() {
        StringBuilder sb = new StringBuilder();
        for (String file : StartupLoader.getAnimationFiles()) {
            Path p = StartupLoader.getAnimDir().resolve(file);
            try {
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                sb.append(file).append(':').append(a.size()).append(':')
                        .append(a.lastModifiedTime().toMillis()).append('|');
            } catch (IOException e) {
                sb.append(file).append(":missing|");
            }
        }
        return sb.toString();
    }

    /** 注入单个模型包: 动画文件 (extra) + ysm.json 轮盘声明表
     *  (v79.25: 遍历 config/animations/ 全部源文件 — 不再硬编码 haqi/maimeng) */
    @OnlyIn(Dist.CLIENT)
    private static void injectIntoModelPack(Path packDir) {
        for (String animFile : ANIM_TARGETS) {
            for (String source : StartupLoader.getAnimationFiles()) {
                injectInto(packDir.resolve("animations").resolve(animFile), source);
            }
        }
        injectYsmJson(packDir.resolve("ysm.json"));
    }

    /**
     * v79.25: 动态解析 config/animations/ 全部源文件的动画 key 并集 — 轮盘声明表。
     * 新动画文件放 config 即自动进轮盘表 (不再手改 EXTRA_KEYS)。
     */
    @OnlyIn(Dist.CLIENT)
    private static Set<String> collectAllAnimKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (String file : StartupLoader.getAnimationFiles()) {
            Path p = StartupLoader.getAnimDir().resolve(file);
            try (InputStream in = Files.newInputStream(p)) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject anims = root.getAsJsonObject("animations");
                if (anims != null) {
                    keys.addAll(anims.keySet());
                }
            } catch (Exception ignored) {
                // 单个文件解析失败不影响其它
            }
        }
        return keys;
    }

    /**
     * ysm.json 轮盘声明表 — properties.extra_animation 加全部动画 key (playRouletteAnim 查此表, OpenYSM 实证)。
     * 缺此项 = 动画文件有定义但轮盘查不到 → 静默不播 (用户实测坑)。幂等 — 已注册的 key 跳过。
     */
    @OnlyIn(Dist.CLIENT)
    private static void injectYsmJson(Path ysmJson) {
        if (!Files.isRegularFile(ysmJson)) return;
        Set<String> keys = collectAllAnimKeys();
        if (keys.isEmpty()) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseReader(Files.newBufferedReader(ysmJson, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject props = root.getAsJsonObject("properties");
            if (props == null) {
                props = new JsonObject();
                root.add("properties", props);
            }
            JsonObject extra = props.getAsJsonObject("extra_animation");
            if (extra == null) {
                extra = new JsonObject();
                props.add("extra_animation", extra);
            }
            int added = 0;
            for (String key : keys) {
                if (extra.has(key)) {
                    continue;
                }
                extra.addProperty(key, key);
                added++;
            }
            if (added == 0) {
                LittleMaidMoreAction.LOGGER.debug("[LMA/YsmInject] ysm.json extra_animation 已全部注册: {}", ysmJson);
                return;
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(ysmJson, json, StandardCharsets.UTF_8);
            // v79.26 卡顿修复: 逐模型逐文件 — DEBUG 级 (启动一次性, 179 行/轮的刷屏无诊断价值)
            LittleMaidMoreAction.LOGGER.debug("[LMA/YsmInject] ysm.json extra_animation 已注册 {} 个: {}", added, ysmJson);
        } catch (IOException | RuntimeException e) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/YsmInject] ysm.json 注入失败: {}", ysmJson, e);
        }
    }

    /** 注入单个动画文件 (幂等) — v79.25: 源从 jar 资源改磁盘 config/animations/ (与网络同步落盘同路径) */
    @OnlyIn(Dist.CLIENT)
    private static void injectInto(Path target, String source) {
        if (!Files.isRegularFile(target)) {
            return;
        }
        JsonObject sourceRoot;
        Path sourcePath = StartupLoader.getAnimDir().resolve(source);
        try (InputStream in = Files.newInputStream(sourcePath)) {
            sourceRoot = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/YsmInject] 读取源动画失败: {}", source, e);
            return;
        }
        JsonObject sourceAnims = sourceRoot.getAsJsonObject("animations");
        if (sourceAnims == null || sourceAnims.isEmpty()) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/YsmInject] 源动画无 animations: {}", source);
            return;
        }

        try {
            JsonObject targetRoot = JsonParser.parseReader(Files.newBufferedReader(target, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject targetAnims = targetRoot.getAsJsonObject("animations");
            if (targetAnims == null) {
                targetAnims = new JsonObject();
                targetRoot.add("animations", targetAnims);
            }
            int added = 0;
            for (var entry : sourceAnims.entrySet()) {
                String name = entry.getKey();
                // 幂等 — 已存在 (用户自定义/LMA 已注入) 跳过, 不覆盖
                if (targetAnims.has(name)) {
                    continue;
                }
                targetAnims.add(name, entry.getValue().deepCopy());
                added++;
            }
            if (added == 0) {
                return;
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(targetRoot);
            Files.writeString(target, json, StandardCharsets.UTF_8);
            LittleMaidMoreAction.LOGGER.debug("[LMA/YsmInject] 已注入 {} 动画到 {} (新增: {})", source, target, added);
        } catch (IOException | RuntimeException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/YsmInject] 注入失败: {}", target, e);
        }
    }
}

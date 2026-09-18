package com.github.xiaozhaoz1.littlemaidmoreaction.storage;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 节日表加载器 — **纯读 jar 预设** (v79.64 用户裁定: 节日是预设数据, 不给用户改, 不要 config 副本)。
 *
 * <p>格式: {@code {"festivals": [{"id","name","month","day","text","lunar","foods"}]}};
 * 来源: classpath 资源 {@code assets/littlemaidmoreaction/festival.json} (jar 内)。解析失败 → 空表 (日历检测静默)。
 *
 * <p><b>为什么不再复制到 config</b> (lessons #341): 原设计把预设复制成
 * {@code config/littlemaidmoreaction/festival.json} 供用户改, 且语义是"缺了才复制, 永不更新" ⇒
 * 老 config 永久冻结 ⇒ 我们后续新增节日/新增字段 (如 {@code foods}) 老用户**永远拿不到** ✗
 * (实测事故: 节日不送礼 = config 里旧条目没有 {@code foods}, 见 lessons #335/#337)。
 * 节日不是给玩家改的数据 ⇒ 直接读 jar 一份, 与版本天然一致 ✓。
 *
 * <p>老 config 文件若残留: **静默忽略, 不读不动** (用户 2026-09-16 裁定) — 不再有它的任何消费方。
 *
 * <p>纯 JVM 约定: 本类零 MC 引用; {@link #parse(InputStream)} 为纯解析入口,
 * {@link #loadFromFile(java.nio.file.Path)} 仅作 JVM 测试/调试的路径注入入口。
 */
public final class FestivalLoader {

    /** jar 预设资源路径 (classpath 资源; 打包后位于 jar 内, 与版本同源) */
    static final String PRESET_RESOURCE = "assets/littlemaidmoreaction/festival.json";

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private FestivalLoader() {}

    /** 加载 jar 预设 → 注入 {@link FestivalTable} (mod 构造期调一次, LmaRegistrar) */
    public static void load() {
        InputStream in = FestivalLoader.class.getClassLoader().getResourceAsStream(PRESET_RESOURCE);
        if (in == null) {
            FestivalTable.setFestivals(List.of());   // 打包异常兜底 (不该发生) — 静默空表
            return;
        }
        int n = 0;
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            List<FestivalTable.Festival> list = parse(reader);
            FestivalTable.setFestivals(list);
            n = list.size();
        } catch (Exception e) {
            FestivalTable.setFestivals(List.of());
        }
        try {
            LittleMaidMoreAction.LOGGER.info("[LMA/Festival] 节日表已从 jar 预设加载 — {} 节日", n);
        } catch (Throwable ignored) {
            // 无日志环境 (单测/早期加载) 静默 — 与项目既有惯例一致 (LittleMaidMoreAction <clinit> 在测试环境不可用)
        }
    }

    /** 纯解析 (JVM 可测): 逐条容错 — 坏条目跳过, 其余节日保留 */
    static List<FestivalTable.Festival> parse(java.io.Reader reader) {
        List<FestivalTable.Festival> list = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has("festivals")) {
                for (var el : root.getAsJsonArray("festivals")) {
                    try {
                        JsonObject o = el.getAsJsonObject();
                        List<String> foods = new ArrayList<>();
                        if (o.has("foods")) {
                            for (var fe : o.getAsJsonArray("foods")) foods.add(fe.getAsString());
                        }
                        list.add(new FestivalTable.Festival(
                                o.get("id").getAsString(),
                                o.get("name").getAsString(),
                                o.get("month").getAsInt(),
                                o.get("day").getAsInt(),
                                o.has("text") ? o.get("text").getAsString() : "",
                                o.has("lunar") && o.get("lunar").getAsBoolean(),
                                foods));
                    } catch (Exception ignored) {
                        // 坏条目跳过, 其余节日保留
                    }
                }
            }
        } catch (Exception ignored) {
            // 解析失败 → 空表
        }
        return list;
    }

    /** jar 预设直接解析 (JVM 测试: 断言"打包里的预设真能被解析且字段齐全") */
    static List<FestivalTable.Festival> loadFromJar() {
        try (InputStream in = FestivalLoader.class.getClassLoader().getResourceAsStream(PRESET_RESOURCE)) {
            if (in == null) return List.of();
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 路径注入版 — 仅 JVM 测试/调试用 (生产不再有任何文件路径读取) */
    static List<FestivalTable.Festival> loadFromFile(java.nio.file.Path file) {
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<FestivalTable.Festival> list = parse(reader);
            FestivalTable.setFestivals(list);
            return list;
        } catch (Exception e) {
            FestivalTable.setFestivals(List.of());
            return List.of();
        }
    }
}

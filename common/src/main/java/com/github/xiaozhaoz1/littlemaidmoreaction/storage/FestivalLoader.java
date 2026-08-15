package com.github.xiaozhaoz1.littlemaidmoreaction.storage;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.FestivalTable;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * v79.47: 节日表加载器 — jar 预设复制 (config/littlemaidmoreaction/festival.json) + 读取注入。
 *
 * <p>格式: {@code {"festivals": [{"id","name","month","day","text","lunar"}]}};
 * 用户可编辑 config 版本 (名称/日期/文案)。解析失败 → 空表 (日历检测器静默)。
 *
 * <p>v79.49: FESTIVAL_FILE 静态字段改 {@link #festivalFile()} 方法内取 — 类加载零 MC 引用,
 * 路径注入版 loadFromFile(Path) 纯 JVM 可测 (原静态字段触发 LittleMaidMoreAction 类加载炸)。
 */
public final class FestivalLoader {

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private FestivalLoader() {}

    /** config festival.json 路径 — 方法内取 (类加载不触发 MC 静态初始化) */
    private static Path festivalFile() {
        return StartupLoader.CONFIG_DIR.resolve("festival.json");
    }

    /** 加载: 创建目录 → jar 预设复制 (缺失时) → 读 config 注入 FestivalTable */
    public static void load() {
        try {
            Files.createDirectories(StartupLoader.CONFIG_DIR);
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/Festival] 创建配置目录失败", e);
            return;
        }
        copyPresetIfMissing();
        loadFromFile(festivalFile());
        LittleMaidMoreAction.LOGGER.info("[LMA/Festival] 节日表加载完成 — {} 节日", FestivalTable.all().size());
    }

    /** JAR 内置 festival.json → config (不存在时; StartupLoader.copyIfMissing 同款) */
    private static void copyPresetIfMissing() {
        Path target = festivalFile();
        if (Files.exists(target)) return;
        String jarPath = "assets/" + LittleMaidMoreAction.MOD_ID + "/festival.json";
        try (InputStream in = StartupLoader.class.getClassLoader().getResourceAsStream(jarPath)) {
            if (in == null) {
                LittleMaidMoreAction.LOGGER.warn("[LMA/Festival] JAR 中缺少预设资源: {}", jarPath);
                return;
            }
            Files.copy(in, target);
            LittleMaidMoreAction.LOGGER.info("[LMA/Festival] 生成预设: festival.json");
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/Festival] 复制预设失败: {}", e.getMessage());
        }
    }

    /** 路径注入版 (v79.49 — 供 JVM 测试临时文件; 纯方法零 MC 引用 — LOGGER 移 load() 侧);
     *  解析失败 → 空表 (静默) */
    static void loadFromFile(Path file) {
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            List<FestivalTable.Festival> list = new ArrayList<>();
            if (root != null && root.has("festivals")) {
                for (var el : root.getAsJsonArray("festivals")) {
                    // 逐条容错 (审计 B2): 用户手改坏一个条目不应让全部节日静默失效
                    try {
                        JsonObject o = el.getAsJsonObject();
                        list.add(new FestivalTable.Festival(
                                o.get("id").getAsString(),
                                o.get("name").getAsString(),
                                o.get("month").getAsInt(),
                                o.get("day").getAsInt(),
                                o.has("text") ? o.get("text").getAsString() : "",
                                o.has("lunar") && o.get("lunar").getAsBoolean()));  // 缺省 false (兼容旧 json)
                    } catch (Exception ignored) {
                        // 坏条目跳过, 其余节日保留
                    }
                }
            }
            FestivalTable.setFestivals(list);
        } catch (Exception e) {
            FestivalTable.setFestivals(List.of());
        }
    }
}

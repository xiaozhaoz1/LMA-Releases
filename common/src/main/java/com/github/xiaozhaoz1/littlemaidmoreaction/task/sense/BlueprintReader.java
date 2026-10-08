package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图读取 (v77.6 移植自 Numen BlueprintReadTool) — LMA 极简蓝图 JSON:
 * 相对坐标块列表 → 尺寸/用料/按层分布, 不动世界一格。
 *
 * <p>文件: {@code config/littlemaidmoreaction/blueprints/<name>.json}
 * <pre>{"name":"小屋","blocks":[{"x":0,"y":0,"z":0,"block":"minecraft:stone"},...]}</pre>
 * 纯文件统计 (零世界依赖 — 相对坐标无世界状态可查)。
 */
public final class BlueprintReader {

    private static final Gson GSON = new Gson();

    private BlueprintReader() {}

    /** 蓝图块 (相对坐标) */
    public record BlueprintBlock(int x, int y, int z, String blockId) {}

    /** 蓝图统计 */
    public record BlueprintInfo(String name, int sizeX, int sizeY, int sizeZ,
                                Map<String, Integer> materials, List<String> layers) {}

    /** 从 JSON 文件读取并统计 (文件缺失/解析失败 → null) */
    public static BlueprintInfo load(Path file) {
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null) return null;
            String name = root.has("name") ? root.get("name").getAsString() : file.getFileName().toString();
            List<BlueprintBlock> blocks = new ArrayList<>();
            JsonArray arr = root.has("blocks") ? root.getAsJsonArray("blocks") : new JsonArray();
            for (var e : arr) {
                JsonObject b = e.getAsJsonObject();
                blocks.add(new BlueprintBlock(
                        b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt(),
                        b.get("block").getAsString()));
            }
            return describe(name, blocks);
        } catch (Exception e) {
            return null;
        }
    }

    /** 统计蓝图块列表 */
    public static BlueprintInfo describe(String name, List<BlueprintBlock> blocks) {
        // v79.49: 空列表特判 — 否则 maxX-minX 整数溢出 (MIN_VALUE-MAX_VALUE=1) 尺寸假正
        if (blocks.isEmpty()) {
            return new BlueprintInfo(name, 0, 0, 0, Map.of(), List.of());
        }
        Map<String, Integer> materials = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlueprintBlock b : blocks) {
            minX = Math.min(minX, b.x()); maxX = Math.max(maxX, b.x());
            minY = Math.min(minY, b.y()); maxY = Math.max(maxY, b.y());
            minZ = Math.min(minZ, b.z()); maxZ = Math.max(maxZ, b.z());
            materials.merge(b.blockId(), 1, Integer::sum);
        }
        List<String> layers = new ArrayList<>();
        for (int y = minY; y <= maxY && y - minY < 24; y++) {
            layers.add("y+" + (y - minY) + ": " + layerSummary(blocks, y));
        }
        return new BlueprintInfo(name,
                Math.max(0, maxX - minX + 1), Math.max(0, maxY - minY + 1), Math.max(0, maxZ - minZ + 1),
                materials, layers);
    }

    private static String layerSummary(List<BlueprintBlock> blocks, int y) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (BlueprintBlock b : blocks) {
            if (b.y() == y) m.merge(b.blockId(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (var e : m.entrySet()) {
            if (n >= 5) { sb.append("+").append(m.size() - n).append(" 种"); break; }
            if (n > 0) sb.append(", ");
            sb.append(e.getValue()).append("x").append(e.getKey());
            n++;
        }
        return sb.toString();
    }

    /** 蓝图目录 (blueprints/ 下所有 .json 文件名) */
    public static List<String> availableNames(Path dir) {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) return names;
        try (var ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path f : ds) names.add(f.getFileName().toString().replace(".json", ""));
        } catch (Exception ignored) {
        }
        return names;
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 老全局文件 {@code config/littlemaidmoreaction/farm_regions.json} → 女仆 NBT 的**一次性导入**
 * (v79.64 用户裁定: 老数据要留, 不能静默丢)。
 *
 * <p>老格式: {@code Map<女仆uuid, List<区域>>} (无维度字段) ⇒ 导入时把维度补成**该女仆当前所在维度**
 * (老数据的区域就是玩家在该维度框选的)。
 *
 * <p><b>触发方式</b>: 惰性 — 任务启动/读写区域前先调 {@link #maybeImportFor(EntityMaid)} (幂等, 单进程一次扫描)。
 * 每只女仆只有一次机会被导入; 当**文件里所有 uuid 都遇到过**时, 把文件改名为 {@code .imported} 收口 ✓
 * (避免"改名太早 ⇒ 后续才加载的女仆数据丢"的最坏情况)。
 *
 * <p><b>临时代码</b>: 老文件导完 (改名) 后本类可整体删除; 解析层 {@link #parseLegacy(String)} 纯函数可单测。
 */
public final class FarmRegionLegacyImport {

    private FarmRegionLegacyImport() {}

    /** 老文件是否已收口 (本进程内) — false = 仍需检查 */
    private static boolean sealed = false;
    /** 老文件已解析成功 (本进程内) — 解析一次后不再重读/重解析 (v79.64.1) */
    private static boolean sealedByParse = false;
    /** 已在老文件里见过(并已导入)的 uuid */
    private static final Set<String> SEEN = new HashSet<>();

    /** 惰性导入: 该女仆在老文件里有区域 → 写进她的 NBT (幂等) */
    public static void maybeImportFor(EntityMaid maid) {
        if (sealed || sealedByParse) return;
        Path file = legacyFile();
        if (!Files.isRegularFile(file)) {
            sealed = true;   // 没有老文件 = 无需导入 (后续调用直接短路)
            return;
        }
        Map<String, List<FarmRegionStorage.FarmRegion>> legacy;
        try {
            legacy = parseLegacy(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (Exception e) {
            sealed = true;   // 读坏: 当作无数据, 不再反复读盘
            return;
        }
        if (legacy.isEmpty()) {
            sealed = true;
            return;
        }
        // ★ v79.64.1: 解析成功即**收口标记** (本进程不再重读) — 原逻辑等"集齐所有 uuid 才 seal",
        //   而老文件里的 uuid 未必都会加载 ⇒ 永不解封 ⇒ 每次 getFor 都读盘重解析 ✗ (浪费 + 放大递归风险)
        //   现语义: 每只女仆**首次访问时**导入一次 (幂等: 已存在则跳过) ✓
        sealedByParse = true;
        String uuid = maid.getStringUUID();
        SEEN.add(uuid);
        List<FarmRegionStorage.FarmRegion> mine = legacy.get(uuid);
        if (mine != null && !mine.isEmpty()) {
            // ★ v79.64.1 修崩服 (StackOverflowError): 这里**不能**调 {@code FarmRegionStorage.getFor}
            //   — 它内部会回调本方法 ⇒ 无限递归 (实测崩服 crash-2026-09-17_12.49.53) ✗
            //   改为直读该女仆 NBT 的 lma_cfg_farm.regions (纯数据, 无回调) ✓
            List<FarmRegionStorage.FarmRegion> existing = FarmRegionStorage.readNbt(maid);
            if (existing.isEmpty()) {
                // 女仆当前维度作为老区域的维度 (老数据无维度字段)
                String dim = maid.level().dimension().location().toString();
                List<FarmRegionStorage.FarmRegion> withDim = new ArrayList<>(mine.size());
                for (var r : mine) {
                    withDim.add(new FarmRegionStorage.FarmRegion(
                            r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(),
                            r.cropId(), r.harvestMode(), r.seedX(), r.seedY(), r.seedZ(),
                            r.harvestX(), r.harvestY(), r.harvestZ(), r.name(), dim));
                }
                FarmRegionStorage.putRegions(maid, withDim);
                LittleMaidMoreAction.LOGGER.info("[LMA/Farm] 老 farm_regions.json 已导入女仆 {} — {} 个区域 (维度 {})",
                        uuid, withDim.size(), dim);
            }
        }
        // 全部 uuid 都遇到过 ⇒ 收口改名 (此后不再读; 未遇到的女仆说明其 uuid 不在文件里)
        if (SEEN.containsAll(legacy.keySet())) {
            seal(file);
        }
    }

    /** 收口: 老文件改名 .imported (保留内容备查; 失败仅告警, 下次启动重试) */
    private static void seal(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".imported"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            sealed = true;
            LittleMaidMoreAction.LOGGER.info("[LMA/Farm] 老 farm_regions.json 导入完成 — 已改名 .imported");
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/Farm] 老 farm_regions.json 改名失败 (下次启动重试): {}", e.toString());
        }
    }

    /** 测试用: 覆盖老文件路径 (null = 生产路径 config/littlemaidmoreaction/farm_regions.json) */
    private static Path legacyFileOverride = null;

    /** 测试用: 设置/清除路径覆盖 (配合 resetForTest 使用; 生产路径不受影响 — 默认 null) */
    public static void setLegacyFileOverrideForTest(Path path) {
        legacyFileOverride = path;
        sealed = false;
        sealedByParse = false;
        SEEN.clear();
    }

    private static Path legacyFile() {
        return legacyFileOverride != null ? legacyFileOverride
                : LittleMaidMoreAction.CONFIG_DIR.resolve("farm_regions.json");
    }

    /** 测试用: 重置进程内状态 */
    static void resetForTest() {
        sealed = false;
        sealedByParse = false;
        SEEN.clear();
    }

    /**
     * 解析老格式 (纯函数, JVM 可测): {@code {uuid: [区域...]}} → {@code uuid → 区域列表}。
     * 坏条目/坏 uuid 跳过 (铁律: 不因单条坏数据整体失效)。
     */
    static Map<String, List<FarmRegionStorage.FarmRegion>> parseLegacy(String json) {
        Map<String, List<FarmRegionStorage.FarmRegion>> out = new HashMap<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String uuid = e.getKey();
                if (uuid == null || uuid.isBlank()) continue;
                List<FarmRegionStorage.FarmRegion> list = new ArrayList<>();
                JsonElement v = e.getValue();
                if (v != null && v.isJsonArray()) {
                    JsonArray arr = v.getAsJsonArray();
                    for (JsonElement je : arr) {
                        FarmRegionStorage.FarmRegion r = parseOne(je);
                        if (r != null && r.isValid()) list.add(r);
                    }
                }
                if (!list.isEmpty()) out.put(uuid, list);
            }
        } catch (Exception ignored) {
            // 坏 JSON → 空 (调用方据此收口)
        }
        return out;
    }

    /** 单条解析 — 缺字段用 0/空兜底 (与老 Gson 宽松解析等价) */
    private static FarmRegionStorage.FarmRegion parseOne(JsonElement je) {
        try {
            JsonObject o = je.getAsJsonObject();
            return new FarmRegionStorage.FarmRegion(
                    intOf(o, "minX"), intOf(o, "minY"), intOf(o, "minZ"),
                    intOf(o, "maxX"), intOf(o, "maxY"), intOf(o, "maxZ"),
                    strOf(o, "cropId"), strOf(o, "harvestMode"),
                    nullableInt(o, "seedX"), nullableInt(o, "seedY"), nullableInt(o, "seedZ"),
                    nullableInt(o, "harvestX"), nullableInt(o, "harvestY"), nullableInt(o, "harvestZ"),
                    strOf(o, "name"), strOf(o, "dimension"));
        } catch (Exception e) {
            return null;
        }
    }

    private static int intOf(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
    }

    private static Integer nullableInt(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : null;
    }

    private static String strOf(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}

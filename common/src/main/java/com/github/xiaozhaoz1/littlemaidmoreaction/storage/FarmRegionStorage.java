package com.github.xiaozhaoz1.littlemaidmoreaction.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单女仆作物区域存储 (v79.62 用户裁定: "比较大的单女仆配置可以放 config 里按女仆 uuid 区分") —
 * {@code config/littlemaidmoreaction/farm_regions.json}:
 * {@code Map<uuid, List<FarmRegion>>}. 每个女仆一个区域列表, 每区域一个指定作物 (cropId).
 *
 * <p>区域来源: 木棒选区 (AABB, 整型端点) + 指定作物 (物品注册名).
 * 女仆 farm 任务遍历自身 uuid 的区域列表干活 (收成熟 + 种指定作物, 跨区块续).
 *
 * <p>纯 JVM: Gson + 文件 IO, 零 MC 依赖 (record 纯数据) — 可 round-trip 单测.
 * 读取容错: 坏 JSON/坏条目跳过 (铁律: 用户手改 config 不应让功能整体失效).
 */
public final class FarmRegionStorage {

    /** 单个种植区域 — 纯数据 (AABB 端点 + 指定作物 + 收获方式 + 本区域种子箱/目标箱坐标).
     *  <p>v79.62 改: 种子箱/目标箱 per-region (原为女仆 PD 全局) — 箱子坐标 Integer 可空
     *  (null = 未绑定; Gson 缺字段 → null, 旧 JSON 兼容)。cropId 允许空 = 只收不种区域
     *  (绑定后 GUI 里补作物)。 */
    public record FarmRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             String cropId, String harvestMode,
                             Integer seedX, Integer seedY, Integer seedZ,
                             Integer harvestX, Integer harvestY, Integer harvestZ, String name) {

        /** 14 参便捷构造 (v79.62.1 加 name 前兼容) — name 默认空 (显示名兜底「区域」) */
        public FarmRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          String cropId, String harvestMode,
                          Integer seedX, Integer seedY, Integer seedZ,
                          Integer harvestX, Integer harvestY, Integer harvestZ) {
            this(minX, minY, minZ, maxX, maxY, maxZ, cropId, harvestMode,
                    seedX, seedY, seedZ, harvestX, harvestY, harvestZ, "");
        }

        /** 显示名 — name 空则「区域」 (v79.62.1 列表/详情界面用) */
        public String displayName() {
            return name == null || name.isBlank() ? "区域" : name;
        }

        /** 收获方式: "left" = 左键摧毁 (默认) / "right" = 右键交互 (FakePlayer, 右键收获作物/果树果实) */
        public boolean isRightHarvest() { return "right".equals(harvestMode); }

        /** 本区域是否绑定了种子源箱 */
        public boolean hasSeedBox() { return seedX != null; }

        /** 本区域是否绑定了收获目标箱 */
        public boolean hasHarvestBox() { return harvestX != null; }

        /** 是否包含某方块坐标 (闭区间) */
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** 检查 region 合法性 (端点顺序 + 收获方式合法; cropId 可空 = 只收不种) — 纯逻辑可测 */
        public boolean isValid() {
            return minX <= maxX && minY <= maxY && minZ <= maxZ
                    && ("left".equals(harvestMode) || "right".equals(harvestMode));
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 无箱哨兵 — Integer.MIN_VALUE (MC 世界坐标远达不到; 不能用 -1, 负坐标合法) */
    public static final int NO_BOX = Integer.MIN_VALUE;

    /** 当前生效的区域表 (uuid → 区域列表), 单服进程内共享; 服务端读, 写侧经 GUI 保存 */
    private static final Map<String, List<FarmRegion>> REGIONS = new LinkedHashMap<>();

    /**
     * 区域文件路径 — 调用方传 {@link LittleMaidMoreAction#CONFIG_DIR} (已是 config/littlemaidmoreaction),
     * 直接落 farm_regions.json (v79.62 修双嵌套: 原再拼一层 littlemaidmoreaction 导致
     * config/littlemaidmoreaction/littlemaidmoreaction/…).
     */
    private static Path filePath(Path configDir) {
        return configDir.resolve("farm_regions.json");
    }

    private FarmRegionStorage() {}

    /** 清空内存态 (世界重载/测试) */
    static void reset() {
        REGIONS.clear();
    }

    // ── 内存态 (服务端运行期真源; 文件为持久层) ──

    /** 读取某女仆的种植区域列表 (只读视图; 无 → 空列表) */
    public static List<FarmRegion> getFor(String maidUuid) {
        List<FarmRegion> list = REGIONS.get(maidUuid);
        return list != null ? list : List.of();
    }

    /** 是否有区域 */
    public static boolean hasAny(String maidUuid) {
        return !getFor(maidUuid).isEmpty();
    }

    // ── 持久层 (写侧 = 区域管理 GUI; 服务端启动/保存时同步) ──

    /** 保存一个女仆的区域列表到内存态 (GUI 写侧入口) */
    public static void putRegions(String maidUuid, List<FarmRegion> regions) {
        List<FarmRegion> valid = new ArrayList<>();
        for (FarmRegion r : regions) {
            if (r != null && r.isValid()) valid.add(r);
        }
        REGIONS.put(maidUuid, valid);
    }

    /** 追加单个区域 (GUI 添加按钮) */
    public static void addRegion(String maidUuid, FarmRegion region) {
        if (region == null || !region.isValid()) return;
        List<FarmRegion> list = new ArrayList<>(getFor(maidUuid));
        list.add(region);
        REGIONS.put(maidUuid, list);
    }

    /** 删除某女仆的第 index 个区域 */
    public static boolean removeRegion(String maidUuid, int index) {
        List<FarmRegion> list = new ArrayList<>(getFor(maidUuid));
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        REGIONS.put(maidUuid, list);
        return true;
    }

    /** 从文件加载 (服务端启动/ onCreate) — 容错: 坏 JSON → 空表 */
    public static void load(Path configDir) {
        REGIONS.clear();
        Path file = filePath(configDir);
        if (!Files.isRegularFile(file)) return;
        try {
            String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            for (var e : root.entrySet()) {
                String uuid = e.getKey();
                List<FarmRegion> list = new ArrayList<>();
                for (JsonElement je : e.getValue().getAsJsonArray()) {
                    try {
                        FarmRegion r = GSON.fromJson(je, FarmRegion.class);
                        if (r != null && r.isValid()) list.add(r);
                    } catch (JsonSyntaxException ex) {
                        // 单条坏记录跳过 (铁律容错)
                    }
                }
                if (!list.isEmpty()) REGIONS.put(uuid, list);
            }
        } catch (Exception e) {
            // 坏 JSON/IO — 空表, 不炸启动
        }
    }

    /** 保存全部到文件 (区域 GUI 每次修改后调用; 容错: 写失败日志级, 不抛上) */
    public static void save(Path configDir) {
        Path file = filePath(configDir);
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            for (var e : REGIONS.entrySet()) {
                JsonArray arr = new JsonArray();
                for (FarmRegion r : e.getValue()) arr.add(GSON.toJsonTree(r));
                root.add(e.getKey(), arr);
            }
            Files.write(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 写失败 — 记录侧不炸 (用户可重试)
        }
    }

    /** 纯数据转 region (gui/选区用) — 保证端点归一化; 收获方式默认 left (左键毁); 无箱 */
    public static FarmRegion of(int x1, int y1, int z1, int x2, int y2, int z2, String cropId) {
        return of(x1, y1, z1, x2, y2, z2, cropId, "left", NO_BOX, NO_BOX, NO_BOX, NO_BOX, NO_BOX, NO_BOX);
    }

    /** 纯数据转 region (gui/选区用) — 指定收获方式; 无箱 */
    public static FarmRegion of(int x1, int y1, int z1, int x2, int y2, int z2, String cropId, String harvestMode) {
        return of(x1, y1, z1, x2, y2, z2, cropId, harvestMode, NO_BOX, NO_BOX, NO_BOX, NO_BOX, NO_BOX, NO_BOX);
    }

    /**
     * 纯数据转 region — 指定收获方式 + 本区域种子箱/目标箱 (v79.62 区域制绑定).
     * <p>无箱哨兵 = {@link #NO_BOX} (Integer.MIN_VALUE) — 不能用 -1: MC 世界坐标可为负
     * (gametest y=-59 实证, 2026-08-20 修复; -1 会误判负坐标箱为「无箱」→ NPE).
     */
    public static FarmRegion of(int x1, int y1, int z1, int x2, int y2, int z2, String cropId, String harvestMode,
            int seedX, int seedY, int seedZ, int harvestX, int harvestY, int harvestZ) {
        return of(x1, y1, z1, x2, y2, z2, cropId, harvestMode,
                seedX, seedY, seedZ, harvestX, harvestY, harvestZ, "");
    }

    /** 纯数据转 region — 带区域名 (v79.62.1 详情界面) */
    public static FarmRegion of(int x1, int y1, int z1, int x2, int y2, int z2, String cropId, String harvestMode,
            int seedX, int seedY, int seedZ, int harvestX, int harvestY, int harvestZ, String name) {
        return new FarmRegion(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), cropId, harvestMode,
                seedX == NO_BOX ? null : seedX, seedY == NO_BOX ? null : seedY, seedZ == NO_BOX ? null : seedZ,
                harvestX == NO_BOX ? null : harvestX, harvestY == NO_BOX ? null : harvestY, harvestZ == NO_BOX ? null : harvestZ,
                name);
    }

    /** 更新某女仆第 index 个区域 (cropId/收获方式; 保留端点与箱 — GUI 逐行编辑) — 非法返回 false */
    public static boolean updateRegion(String maidUuid, int index, String cropId, String harvestMode) {
        List<FarmRegion> list = new ArrayList<>(getFor(maidUuid));
        if (index < 0 || index >= list.size()) return false;
        FarmRegion old = list.get(index);
        FarmRegion upd = new FarmRegion(old.minX(), old.minY(), old.minZ(), old.maxX(), old.maxY(), old.maxZ(),
                cropId, harvestMode, old.seedX(), old.seedY(), old.seedZ(),
                old.harvestX(), old.harvestY(), old.harvestZ());
        if (!upd.isValid()) return false;
        list.set(index, upd);
        REGIONS.put(maidUuid, list);
        return true;
    }

    /** v79.62.1 详情界面全更新 — name/cropId/左右手/箱子/坐标 (null 字段保留原值; 无参改) */
    public static boolean updateRegionFull(String maidUuid, int index, String name, String cropId, String harvestMode,
            Integer seedX, Integer seedY, Integer seedZ, Integer harvestX, Integer harvestY, Integer harvestZ,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<FarmRegion> list = new ArrayList<>(getFor(maidUuid));
        if (index < 0 || index >= list.size()) return false;
        FarmRegion old = list.get(index);
        FarmRegion upd = new FarmRegion(minX, minY, minZ, maxX, maxY, maxZ,
                cropId == null ? old.cropId() : cropId,
                harvestMode == null ? old.harvestMode() : harvestMode,
                seedX != null && seedX != NO_BOX ? seedX : old.seedX(),
                seedY != null && seedY != NO_BOX ? seedY : old.seedY(),
                seedZ != null && seedZ != NO_BOX ? seedZ : old.seedZ(),
                harvestX != null && harvestX != NO_BOX ? harvestX : old.harvestX(),
                harvestY != null && harvestY != NO_BOX ? harvestY : old.harvestY(),
                harvestZ != null && harvestZ != NO_BOX ? harvestZ : old.harvestZ(),
                name == null ? old.displayName() : name);
        if (!upd.isValid()) return false;
        list.set(index, upd);
        REGIONS.put(maidUuid, list);
        return true;
    }
}

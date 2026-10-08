package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world;

/**
 * 区域盒纯数据 (v79.63) — 六元组 AABB + 常用几何判定。**零 MC 依赖** (JVM 可单测)。
 *
 * <p><b>为什么存在</b>: 原 {@code CropQuery} (vanilla/input/search) 直接 import
 * {@code storage.FarmRegionStorage.FarmRegion} — 这是 **vanilla → storage 的反向越层**
 * (架构审计批 A4 欠账之一)。作物查询真正需要的只是"一个盒子"这一数据形状, 不需要 storage 的
 * 区域语义 (cropId/harvestMode/标记点/显示名) → 抽出本盒, 由**调用方** (task 层, 可同时认识两边)
 * 负责把区域记录转成本盒。与 {@code StructBox} (结构边界盒) 同族: 都是纯数据 record。
 *
 * <p><b>契约</b>: 归一化 (min ≤ max) 由调用方保证 (与 {@code FarmRegionStorage.of} 同口径);
 * 本类不做校验, 只做判定 — 避免在这里悄悄"修正"数据掩盖上游错误。
 */
public record RegionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** 便捷构造 (语义化命名, 便于调用方一眼看出参数顺序) */
    public static RegionBox of(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new RegionBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 三维包含判定 (闭区间) */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Y 轴各扩展 1 格的包含判定 — 作物场景用 (耕地层在 minY 下方, 作物/果实实体层在 maxY 上方);
     * 与 FarmExecute.getRegionAABB 的 ±1 语义一致 (错题 #238 同族: 判定口径必须与 AABB 一致)。
     */
    public boolean containsYExtended(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY - 1 && y <= maxY + 1
                && z >= minZ && z <= maxZ;
    }

    /** 盒中心 (整数floor 除, 与区块扫描中心口径一致) */
    public int centerX() { return (minX + maxX) / 2; }

    public int centerY() { return (minY + maxY) / 2; }

    public int centerZ() { return (minZ + maxZ) / 2; }

    /** 最大边长 (X/Y/Z 三轴取最大) */
    public int maxDimension() {
        return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
    }

    /** 水平 (XZ) 面积格数 — 诊断/日志用 */
    public int horizontalArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    /** 内含格数 (三维) */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}

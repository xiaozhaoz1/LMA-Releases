package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world;

/**
 * 结构边界盒纯数据 (v79.6x BB 语义) — 从 MC BoundingBox 抽取 int 六元组,
 * 纯函数 JVM 可测 (错题 #174 铁律: MC 类型不进纯测试)。
 *
 * <p>相位判定基准从「结构中心」改为「结构边界」: 距盒距离 (在盒内 = 0)。
 * 进入 = 距盒 ≤enterDist; 离开 = 距盒 &gt;leaveDist; 刷新环带 = enterDist~leaveDist。
 */
public record StructBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** 方块坐标点到边界盒的平方距离 (在盒内或盒面 = 0) */
    public double distSqrFrom(int x, int y, int z) {
        double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
        double dy = y < minY ? minY - y : y > maxY ? y - maxY : 0;
        double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 盒中心方块中心坐标 (方向词锚点 — 与 BoundingBox.getCenter 同口径) */
    public double centerX() { return minX + (maxX - minX + 1) / 2.0; }
    public double centerY() { return minY + (maxY - minY + 1) / 2.0; }
    public double centerZ() { return minZ + (maxZ - minZ + 1) / 2.0; }
}

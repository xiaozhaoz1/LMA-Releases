package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自我中心语义网格 (v77.6 移植自 Numen LookAroundTool) — 周围方块渲染为字符地形图。
 *
 * <p>每格 = 一个方块, 语义池化为移动 affordance (flat/step-up/step-down/drop/wall/water/lava),
 * LLM 读图式空间推理。核心 classify 依赖抽象 {@link ColumnFacts} (全坐标版) — 纯逻辑可测。
 *
 * <p>字符: @ 你 | . 平地 | ^ 上 1 阶 | , 下 1-2 | v 掉 3+ | # 墙 | ~ 水 | ! 岩浆/危险 | x 危险旁 | T 树
 */
public final class LookAroundGrid {

    public static final int DROP_DEPTH = 3;
    public static final char YOU = '@';
    public static final char FLAT = '.';
    public static final char STEP_UP = '^';
    public static final char STEP_DOWN = ',';
    public static final char DROP = 'v';
    public static final char WALL = '#';
    public static final char WATER = '~';
    public static final char HAZARD = '!';
    public static final char CAUTION = 'x';
    public static final char TREE = 'T';

    private LookAroundGrid() {}

    /** 列事实抽象 (全坐标版 — 测试桩零 MC 依赖) */
    public interface ColumnFacts {
        boolean isLavaAt(int x, int y, int z);
        boolean isLiquidAt(int x, int y, int z);
        boolean isTreeAt(int x, int y, int z);
        /** 该列 (x,z) 在 y 处可站立 (脚下支撑 + 身体空) */
        boolean canStandAt(int x, int y, int z);
    }

    /** 世界默认实现 */
    public static final class WorldFacts implements ColumnFacts {
        private final Level level;
        public WorldFacts(Level level) { this.level = level; }

        private BlockState at(int x, int y, int z) { return level.getBlockState(new BlockPos(x, y, z)); }
        @Override public boolean isLavaAt(int x, int y, int z) {
            BlockState s = at(x, y, z);
            return s.getBlock() == net.minecraft.world.level.block.Blocks.LAVA;
        }
        @Override public boolean isLiquidAt(int x, int y, int z) {
            return at(x, y, z).getBlock() instanceof LiquidBlock;
        }
        @Override public boolean isTreeAt(int x, int y, int z) {
            BlockState s = at(x, y, z);
            return s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES);
        }
        @Override public boolean canStandAt(int x, int y, int z) {
            BlockState feet = at(x, y, z);
            BlockState body = at(x, y + 1, z);
            if (feet.isAir() || body.isAir()) return false;
            return !feet.canBeReplaced() && !body.canBeReplaced();
        }
    }

    /** 渲染语义网格 — North 顶 (-Z), East 右 (+X), 中心 @; 危险膨胀 (x) */
    public static String render(Level level, BlockPos feet, int radius) {
        return render(new WorldFacts(level), feet, radius);
    }

    public static String render(ColumnFacts facts, BlockPos feet, int radius) {
        int size = 2 * radius + 1;
        char[][] grid = new char[size][size];
        for (int r = 0; r < size; r++) {
            int dz = r - radius;
            for (int c = 0; c < size; c++) {
                int dx = c - radius;
                grid[r][c] = (dx == 0 && dz == 0)
                        ? YOU
                        : classify(facts, feet.getX() + dx, feet.getY(), feet.getZ() + dz);
            }
        }
        inflateHazards(grid, size);
        StringBuilder sb = new StringBuilder();
        sb.append("look_around center=(").append(feet.getX()).append(',').append(feet.getY()).append(',')
                .append(feet.getZ()).append(") | 1 cell = 1 block, @ = you, North = up (-Z)\n\n");
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                sb.append(grid[r][c]);
                if (c < size - 1) sb.append(' ');
            }
            sb.append('\n');
        }
        sb.append("\nlegend: @ you | . flat | ^ step-up 1 | , step-down 1-2 | v drop>=")
                .append(DROP_DEPTH).append(" | # wall | ~ water | ! lava | x caution | T tree\n");
        return sb.toString();
    }

    /** 语义池化: 该列在女仆 Y 带的移动 affordance 字符 */
    static char classify(ColumnFacts facts, int x, int feetY, int z) {
        if (facts.isLavaAt(x, feetY, z) || facts.isLavaAt(x, feetY + 1, z)) return HAZARD;
        if (facts.isLiquidAt(x, feetY, z) || facts.isLiquidAt(x, feetY + 1, z)) return WATER;

        // 站立面扫描: feetY+1 (跳上) 到 feetY-DROP_DEPTH (深掉) — 取最高可站
        Integer standY = null;
        for (int y = feetY + 1; y >= feetY - DROP_DEPTH; y--) {
            if (facts.canStandAt(x, y, z)) { standY = y; break; }
        }
        if (standY == null) {
            return facts.isTreeAt(x, feetY, z) ? TREE : WALL;
        }
        int dy = standY - feetY;
        if (dy == 0) return FLAT;
        if (dy == 1) return STEP_UP;
        if (dy == -1 || dy == -2) return STEP_DOWN;
        return DROP;
    }

    /** 危险膨胀: 岩浆/危险旁一格 → x */
    static void inflateHazards(char[][] grid, int size) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (grid[r][c] == HAZARD) {
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            int nr = r + dr, nc = c + dc;
                            if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                            if (grid[nr][nc] == FLAT || grid[nr][nc] == STEP_UP
                                    || grid[nr][nc] == STEP_DOWN) {
                                grid[nr][nc] = CAUTION;
                            }
                        }
                    }
                }
            }
        }
    }
}

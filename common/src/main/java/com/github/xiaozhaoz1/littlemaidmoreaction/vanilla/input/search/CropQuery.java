package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.FarmRegion;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 作物区域扫描原语 (v79.62) — 走 {@link BlockPatternCache} 区块缓存 (CROP/FARMLAND),
 * 区域 AABB 过滤 + 细筛防脏读. 纯读 (vanilla 层).
 *
 * <p><b>双面</b>:
 * <ul>
 *   <li>成熟作物 {#{@link #scanMature}} — 区域内全部「可收成熟作物」 (用户裁定: 收区域内全部成熟, 不限指定)</li>
 *   <li>可种耕地 {@link #scanPlantable} — 区域内可容纳指定种子的耕地 (种草指定作物)</li>
 * </ul>
 * 右键收 (果树/右键作物) 与左键收共用 {@link #scanMature}, 动作差异在 FarmExecute 按 harvestMode.
 */
public final class CropQuery {

    private CropQuery() {}

    /**
     * 区域内全部成熟可收作物 (含骨粉可催熟 fallback 方块), 按距中心升序.
     * 粗扫描走 CROP 缓存, 细筛 region.contains.
     */
    public static List<BlockPos> scanMature(ServerLevel level, BlockPos center, FarmRegion region,
                                            BlockPos origin) {
        BlockPos c = regionCenter(region);
        int r = regionRadius(region);
        List<BlockPos> found = BlockPatternCache.scanBlocks(level, c, r, BlockPatternCache.PatternType.CROP);
        List<BlockPos> out = new ArrayList<>();
        int inRegion = 0;
        for (BlockPos p : found) {
            // v79.62.1: Y 轴扩展 ±1 (与 getRegionAABB 一致: 含耕地层下方+作物层上方)
            if (containsExtended(region, p)) { out.add(p); inRegion++; }
        }
        // v79.62.1 降噪: MISS 是正常缓存失配 (多农田区域共享 CROP 缓存, 非本区域作物),
        // INFO→DEBUG 防每 tick 刷屏 (spark 实证卡顿后清尾); 排查时开 DEBUG 可见
        if (found.size() > 0 && inRegion == 0 && com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, found.size()); i++) {
                BlockPos p = found.get(i);
                sb.append("(").append(p.getX()).append(",").append(p.getY()).append(",").append(p.getZ()).append(") ");
            }
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                    "[LMA/Farm] MISS region=({},{},{})~({},{},{}) cache[0:3]={}",
                    region.minX(), region.minY(), region.minZ(),
                    region.maxX(), region.maxY(), region.maxZ(), sb);
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return out;
    }

    /** Y 轴扩展 ±1 的 contains (与 getRegionAABB 一致) */
    private static boolean containsExtended(FarmRegion region, BlockPos p) {
        return p.getX() >= region.minX() && p.getX() <= region.maxX()
                && p.getY() >= region.minY() - 1 && p.getY() <= region.maxY() + 1
                && p.getZ() >= region.minZ() && p.getZ() <= region.maxZ();
    }

    /**
     * 区域内可播种指定种子的耕地 (上方可为方块), 按距原点升序.
     * 粗扫描 FARMLAND 缓存 + 细筛 canPlantOn + 上方可替换.
     */
    public static List<BlockPos> scanPlantable(ServerLevel level, FarmRegion region, BlockPos origin,
                                               ItemStack seed) {
        if (seed.isEmpty()) return List.of();
        List<BlockPos> out = new ArrayList<>();
        // v79.62.1 统一: 扫区域 AABB 内所有方块, 用 canPlantOn 判断 (不再依赖 FARMLAND 缓存)
        // 耕地/泥土/灵魂沙/沙子等基座都能找到, 适配所有作物类型
        // 可可豆种在原木侧面 (不检查上方可替换 — 原木墙中间的原木上方是原木, 但侧面可种)
        boolean isCocoa = seed.getItem() instanceof net.minecraft.world.item.ItemNameBlockItem ci
                && ci.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY() - 1; y <= region.maxY() + 1; y++) {  // Y 轴扩展 ±1
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    mp.set(x, y, z);
                    BlockState base = level.getBlockState(mp);
                    if (!CropRegistry.canPlantOn(base, seed)) continue;
                    if (!isCocoa) {
                        BlockState above = level.getBlockState(mp.above());
                        if (!above.canBeReplaced()) continue;
                    }
                    out.add(mp.immutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return out;
    }

    /** 区域内是否还有成熟作物 (快速有无判定 — 无则区域种完/收完) */
    public static boolean hasMature(ServerLevel level, FarmRegion region, BlockPos origin) {
        BlockPos c = regionCenter(region);
        int r = regionRadius(region);
        for (BlockPos p : BlockPatternCache.scanBlocks(level, c, r, BlockPatternCache.PatternType.CROP)) {
            if (containsExtended(region, p)) return true;
        }
        return false;
    }

    // ── 区域几何 (归一化已由 FarmRegionStorage.of 保证; 中心/半径按区块扫描) ──

    public static BlockPos regionCenter(FarmRegion region) {
        return new BlockPos((region.minX() + region.maxX()) / 2,
                (region.minY() + region.maxY()) / 2,
                (region.minZ() + region.maxZ()) / 2);
    }

    public static int regionRadius(FarmRegion region) {
        // v79.62.1 修复多区块区域只扫一个区块: 半径按最大边长一半 + 16 余量,
        // 保证 chunkRadius = radius>>4 覆盖区域所有区块 (跨多区块农田/作物)
        int dx = region.maxX() - region.minX();
        int dy = region.maxY() - region.minY();
        int dz = region.maxZ() - region.minZ();
        int maxDim = Math.max(dx, Math.max(dy, dz));
        return Math.max(16, (int) Math.ceil(maxDim / 2.0) + 16);
    }
}

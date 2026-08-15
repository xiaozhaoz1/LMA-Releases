package com.github.xiaozhaoz1.littlemaidmoreaction.api.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvEdgeDetector;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvRules;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvScanner;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSenseBroadcaster;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.ScanFilters;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.ScanJob;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.ScanScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 环境感知 API (v79.3) — 环境感知优化执行层对管道/外部 mod 的暴露面 (仿 PathingApi)。
 *
 * <p>分层: io 层 (vanilla/input/) = 底层交互; 本类 = 执行层 (task/sense/ 扫描/信号/节流/快照)
 * 的静态薄封装。管线 (TaskPipeline 实现) 与外部 mod 经此获取环境数据, 不直接触碰 io 原语。
 *
 * <p>主线程约束: 所有方法须在服务端主线程调用 (扫描/快照无锁, 依赖 server tick 单线程)。
 * null 语义: 女仆无快照 (未开启环境感知/未到首个广播周期) → snapshot/worldInfo 返回 null。
 */
public final class SenseApi {

    private SenseApi() {}

    // ── 快照查询 (O(1), 无扫描触发; 200t 广播周期缓存) ──

    /** 女仆最新环境快照 (null = 无快照 — 未开感知/首轮广播前/maid 为 null) */
    @Nullable
    public static EnvSnapshot snapshot(EntityMaid maid) {
        if (maid == null) return null;
        return EnvSenseBroadcaster.getSnapshot(maid);
    }

    /** 女仆最新世界状态 (维度/温度/天气/时段/biome/站立结构; null = 无快照/maid 为 null) */
    @Nullable
    public static EnvSnapshot.WorldInfo worldInfo(EntityMaid maid) {
        if (maid == null) return null;
        EnvSnapshot snap = EnvSenseBroadcaster.getSnapshot(maid);
        return snap == null ? null : snap.world();
    }

    // ── 轻量直读 (读时现查, 不入快照) ──

    /** 女仆所在生物群系 registry id (namespace:path; 未知 "unknown") */
    public static String biomeAt(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel sl)) return "unknown";
        return EnvScanner.readWorld(sl, maid.blockPosition()).biomeId();
    }

    /** 女仆站立点所在结构 id 列表 (排序; 空 = 不在任何结构; 零成本瞬时查询) */
    public static List<String> structuresAt(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel sl)) return List.of();
        String joined = EnvScanner.structuresAt(sl, maid.blockPosition());
        return joined.isEmpty() ? List.of() : List.of(joined.split(","));
    }

    // ── 同步扫描 (主线程有界半径 — 文档警告: tick 内同步, 半径勿大) ──

    /**
     * 最近目标方块搜索 (ChainHarvest.findNearestValid 泛化提升) — BlockScanner
     * palette 短路 (稀疏目标 50-200× 提速) + skip 集过滤 + 最近优先。
     *
     * @param radius  搜索半径 (方块)
     * @param vRange  垂直范围 (±Y, 0 = 不限) — 档位扫描 (AGGRESSIVE 高 5 地下 5)
     * @param filter  方块过滤 (可用 {@link ScanFilters} 组合)
     * @param skip    跳过集 (方块 longHash — 已尝试/不可采目标; 可 null)
     * @param maxHits 扫描预算上限 (越大越准, 每 tick 同步成本越高)
     * @return 最近命中; 无 → null
     */
    @Nullable
    public static BlockPos findNearestBlock(EntityMaid maid, int radius, int vRange,
                                            Predicate<BlockState> filter,
                                            @Nullable java.util.Set<Long> skip, int maxHits) {
        if (!(maid.level() instanceof ServerLevel sl)) return null;
        // radius (格) → chunk 半径 ceil 换算 — 原 radius/16+1: 16 → 2 chunk =
        // 32 格半径 (超预期 1 倍); 用户: "我说的周围16格是半径16格" → 16 格 = 1 chunk。
        // Math.max(1, ...): 0-16 格 → 1 chunk (BlockScanner 最少 1 环)
        int chunkRadius = Math.max(1, (radius + 15) / 16);
        var matches = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.BlockScanner.scan(
                sl, maid.blockPosition(), chunkRadius, vRange, filter, Math.max(8, maxHits));
        for (var m : matches) {
            if (skip == null || !skip.contains(m.pos().asLong())) return m.pos();
        }
        return null;
    }

    /** 扫描附近实体 (分类 monster/friendly/maid, 按距离排序截断) */
    public static Map<String, List<LivingEntity>> scanEntities(EntityMaid maid, int radius, int maxHits) {
        if (!(maid.level() instanceof ServerLevel sl)) return Map.of();
        return EnvScanner.scanEntities(sl, maid, radius, maxHits);
    }

    /** 扫描附近雪层 (同步有界) */
    public static List<BlockPos> scanSnow(EntityMaid maid, int radius) {
        if (!(maid.level() instanceof ServerLevel sl)) return List.of();
        return EnvScanner.scanSnowBlocks(sl, maid.blockPosition(), radius);
    }


    // ── 预算化异步扫描 (ScanScheduler 集中调度, 主线程逐 tick 切片) ──

    /**
     * 提交预算化异步方块扫描 — 不阻塞 tick, 断点续扫, DEADLINE 600t 转部分结果。
     * 女仆卸载自动取消 (ScanScheduler.cancelFor 清理闭环)。
     *
     * @param radiusChunks 扫描半径 (chunk 单位, 上限 {@link net.minecraft.core.SectionPos} 螺旋)
     * @param filter       方块过滤 (可用 {@link ScanFilters} 常量组合)
     * @param onDone       完成回调 (主线程, 仅一次; cancel 后不触发)
     * @return 任务句柄 (轮询 isScanDone/scanResults 或等 onDone)
     */
    public static ScanJob startScan(EntityMaid maid, int radiusChunks,
                                    Predicate<BlockState> filter, int maxHits,
                                    Runnable onDone) {
        if (!(maid.level() instanceof ServerLevel sl)) {
            throw new IllegalArgumentException("SenseApi.startScan 需服务端女仆");
        }
        return ScanScheduler.submit(sl, maid.blockPosition(), radiusChunks, filter,
                maxHits, sl.getServer().getTickCount(), maid.getId(), onDone);
    }

    /** 取消扫描 (onDone 不再触发) */
    public static void cancelScan(ScanJob job) {
        if (job != null) ScanScheduler.cancel(job);
    }

    /** 扫描是否完成 (含 DEADLINE 部分结果) */
    public static boolean isScanDone(ScanJob job) {
        return job != null && job.isDone();
    }

    /** 扫描结果 (按距离排序, 防御拷贝; 未完成时 = 当前部分收集) */
    public static List<com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.BlockScanner.Match> scanResults(ScanJob job) {
        return job == null ? List.of() : job.matches();
    }

    // ── 事件注入 (给外部 mod 驱动既有管线) ──

    /** 注入环境信号 (env: 前缀) — 延迟到广播末尾统一分发 (事件回调期间不并发改状态) */
    public static void emit(EntityMaid maid, String signalId) {
        if (!(maid.level() instanceof ServerLevel sl)) return;
        EnvSenseBroadcaster.emit(sl, maid, signalId);
    }

    /**
     * 按方块 id 最近搜索 (FindTargetAction 组合吸收) — BlockSearch nearest-first。
     * 空 blockId = 任意非空气方块。仅搜索, 导航由调用方经 NavigationUtil 组合。
     *
     * @return 最近命中坐标; 无 → null
     */
    @Nullable
    public static BlockPos findBlockNearest(EntityMaid maid, @Nullable String blockId,
                                            int range, int vertical) {
        if (!(maid.level() instanceof ServerLevel sl)) return null;
        net.minecraft.world.level.block.Block targetBlock = resolveBlock(blockId);
        var matches = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.BlockSearch.findBlocks(
                sl, maid.blockPosition(), Math.max(1, range), Math.max(1, vertical),
                (pos, state) -> targetBlock == null || state.is(targetBlock));
        return matches.isEmpty() ? null : matches.get(0).pos();
    }

    /** block id → Block (双平台注册表; 空/非法 → null) */
    @Nullable
    private static net.minecraft.world.level.block.Block resolveBlock(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) return null;
//? if 1.20.1 {
        return net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(rl);
//?} else {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
//?}
    }

    /** 女仆血量比例 (WaitUntilMaidHealthAction 谓词吸收) */
    public static float healthRatio(EntityMaid maid) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.MaidStateReader.getHealthRatio(maid);
    }

    // ── 纯逻辑 (零 MC 依赖, 无女仆也可用) ──

    /** 温度档: COLD(<0.15) / OCEAN(<0.55) / MEDIUM(<0.95) / WARM */
    public static String tempCategory(float baseTemp) {
        return EnvRules.tempCategory(baseTemp);
    }

    /** 时间段: DAY(0-11999) / DUSK(12000-13799) / NIGHT(13800-22199) / DAWN(22200-23999) */
    public static String timeSegment(long dayTime) {
        return EnvRules.timeSegment(dayTime);
    }

    /** 边沿检测纯核心 (外部扩展信号时可用) */
    public static EnvEdgeDetector.EnvConfig envConfig(float coldThreshold, float hotThreshold, int darknessThreshold) {
        return new EnvEdgeDetector.EnvConfig(coldThreshold, hotThreshold, darknessThreshold);
    }
}

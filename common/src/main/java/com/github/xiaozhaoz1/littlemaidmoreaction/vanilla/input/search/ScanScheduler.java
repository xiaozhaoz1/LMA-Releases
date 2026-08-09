package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * 扫描任务集中调度 (v79.3) — MC 薄封装 (错题 #70 模式: 纯核心 ScanSchedulerCore + 薄壳)。
 *
 * <p>由 TaskTickHandler 每服务端 tick 驱动 {@link #tick}; 女仆卸载时
 * {@link #cancelFor} 清理 (内存泄漏必堵口)。任务句柄 = {@link ScanJob}
 * (游标切片 + ScanBudget 预算 + DEADLINE 部分结果, v77.5 移植)。
 */
public final class ScanScheduler {

    private static final ScanSchedulerCore<ScanJob> CORE = new ScanSchedulerCore<>();

    private ScanScheduler() {}

    /** 提交预算化异步扫描 — 返回任务句柄 (轮询 isDone/matches 或等 onDone) */
    public static ScanJob submit(ServerLevel level, BlockPos center, int maxRing,
                                 Predicate<BlockState> filter, int maxHits, int serverTick,
                                 int ownerId, Runnable onDone) {
        ScanJob job = ScanJob.start(level, center, maxRing, filter, maxHits, serverTick, onDone, ownerId);
        CORE.submit(job, ownerId);
        return job;
    }

    /** 取消指定任务 (onDone 不再触发) */
    public static void cancel(ScanJob job) {
        job.cancel();
        CORE.cancel(job);
    }

    /** 取消归属者的全部任务 (女仆卸载/任务取消清理闭环) */
    public static void cancelFor(int ownerId) {
        CORE.cancelFor(ownerId);
    }

    /** 每服务端 tick 驱动 — 由 TaskTickHandler 挂载 (全维度共享预算) */
    public static void tick(int serverTick) {
        CORE.tick(serverTick);
    }

    /** 活动任务数 (调试) */
    public static int activeCount() {
        return CORE.activeCount();
    }
}

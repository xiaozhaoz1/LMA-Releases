package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描任务调度核心 (v79.3) — 纯 JVM 泛型 (T extends Tickable), 零 MC 依赖可测。
 *
 * <p>FCFS 顺序逐 tick 推进; done 即驱逐 (执行中 delete-if-absent);
 * ownerId 定向取消 (女仆卸载清理闭环, 防任务悬空烧预算)。
 */
public final class ScanSchedulerCore<T extends Tickable> {

    private record Entry<T>(T job, int ownerId) {}

    private final List<Entry<T>> jobs = new ArrayList<>();

    /** 提交任务 (ownerId = 归属者, -1 = 无) */
    public void submit(T job, int ownerId) {
        jobs.add(new Entry<>(job, ownerId));
    }

    /** 取消指定任务实例 */
    public void cancel(T job) {
        jobs.removeIf(e -> e.job() == job);
    }

    /** 取消归属者的全部任务 (女仆卸载/任务取消) */
    public void cancelFor(int ownerId) {
        jobs.removeIf(e -> e.ownerId() == ownerId);
    }

    /** 逐 tick 推进 — 快照迭代防并发修改; done 驱逐 */
    public void tick(int serverTick) {
        for (Entry<T> e : List.copyOf(jobs)) {
            if (e.job().isDone()) {
                jobs.remove(e);
                continue;
            }
            e.job().tick(serverTick);
            if (e.job().isDone()) {
                jobs.remove(e);
            }
        }
    }

    /** 活动任务数 (调试/测试) */
    public int activeCount() {
        return jobs.size();
    }
}

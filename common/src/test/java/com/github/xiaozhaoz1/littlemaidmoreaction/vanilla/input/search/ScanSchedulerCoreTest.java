package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScanSchedulerCore} 纯 JVM 测试 (v79.3) — 提交/推进/驱逐/定向取消。
 */
class ScanSchedulerCoreTest {

    /** 假任务 — 计数 tick, 可设 done 条件 */
    private static final class FakeJob implements Tickable {
        int ticks;
        final int doneAfter;   // ticks 达到后 done
        FakeJob(int doneAfter) { this.doneAfter = doneAfter; }
        @Override public void tick(int serverTick) { ticks++; }
        @Override public boolean isDone() { return ticks >= doneAfter; }
    }

    @Test
    @DisplayName("submit → tick 逐任务推进 (FCFS)")
    void tick_advancesAllJobs() {
        ScanSchedulerCore<FakeJob> core = new ScanSchedulerCore<>();
        FakeJob a = new FakeJob(3);
        FakeJob b = new FakeJob(5);
        core.submit(a, 1);
        core.submit(b, 2);

        core.tick(100);
        assertEquals(1, a.ticks);
        assertEquals(1, b.ticks, "FCFS 顺序推进");
    }

    @Test
    @DisplayName("done 驱逐: 完成后不再 tick, activeCount 下降")
    void tick_doneEvicted() {
        ScanSchedulerCore<FakeJob> core = new ScanSchedulerCore<>();
        FakeJob a = new FakeJob(2);
        core.submit(a, 1);
        core.tick(100);
        core.tick(101);   // a done → 驱逐
        assertEquals(2, a.ticks);
        assertEquals(0, core.activeCount(), "done 后驱逐");
        core.tick(102);
        assertEquals(2, a.ticks, "驱逐后不再推进");
    }

    @Test
    @DisplayName("cancelFor: 只清目标 owner 的任务")
    void cancelFor_targetsOwnerOnly() {
        ScanSchedulerCore<FakeJob> core = new ScanSchedulerCore<>();
        FakeJob a = new FakeJob(10);
        FakeJob b = new FakeJob(10);
        FakeJob c = new FakeJob(10);
        core.submit(a, 1);
        core.submit(b, 2);
        core.submit(c, 1);

        core.cancelFor(1);
        assertEquals(1, core.activeCount(), "owner 1 两个任务被清, owner 2 保留");
        core.tick(100);
        assertEquals(0, a.ticks, "已取消不推进");
        assertEquals(0, c.ticks);
        assertEquals(1, b.ticks, "保留任务正常推进");
    }

    @Test
    @DisplayName("cancel: 指定任务实例取消")
    void cancel_specificJob() {
        ScanSchedulerCore<FakeJob> core = new ScanSchedulerCore<>();
        FakeJob a = new FakeJob(10);
        FakeJob b = new FakeJob(10);
        core.submit(a, 1);
        core.submit(b, 1);
        core.cancel(a);
        assertEquals(1, core.activeCount());
        core.tick(100);
        assertEquals(0, a.ticks);
        assertEquals(1, b.ticks);
    }

    @Test
    @DisplayName("tick 幂等/空核安全")
    void tick_emptyAndRepeatSafe() {
        ScanSchedulerCore<FakeJob> core = new ScanSchedulerCore<>();
        core.tick(100);   // 空核不炸
        FakeJob a = new FakeJob(2);
        core.submit(a, 1);
        core.tick(100);
        core.tick(100);   // 同 tick 重复调用 — 已 done 不再推进
        assertEquals(2, a.ticks);
    }
}

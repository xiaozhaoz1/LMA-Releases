package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

/**
 * 可调度 tick 任务 (v79.3) — ScanScheduler 集中驱动的统一契约。
 */
public interface Tickable {

    /** 每 tick 推进 (serverTick = 服务端 tick 计数, 预算刷新/超时判定用) */
    void tick(int serverTick);

    /** 是否已完成 (done 后调度器驱逐) */
    boolean isDone();
}

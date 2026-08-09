package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

/**
 * 被动 tick 预算轮转纯函数 (v79) — 每女仆每 tick 最多执行的被动管线数。
 *
 * <p>环形起点 = {@code (now + maidId) % size} — 确定性、零 per-maid 状态、零清理、
 * 单 tick 线程安全。预算不足时各被动管线轮转获得 tick 机会 (公平 + 时序偏移可预期)。
 */
public final class PassiveRotation {

    private PassiveRotation() {}

    /** 环形轮转起点 (size ≥ 1 由调用方保证) */
    public static int startIndex(long now, int maidId, int size) {
        return Math.floorMod(now + maidId, size);
    }
}

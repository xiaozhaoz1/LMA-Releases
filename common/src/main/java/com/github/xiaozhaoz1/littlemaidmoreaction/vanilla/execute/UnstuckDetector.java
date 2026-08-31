package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

/**
 * 卡住检测纯内核 (v79.61x 移植自 Numen UnstuckDetector, dwinovo/minecraft-numen LGPL) —
 * 环形缓冲滚动窗口, 零 MC 依赖, 纯 JVM 可测。
 *
 * <p>卡住签名: 一个完整窗口内, 实体在大部分 tick "尝试移动" (有移动意图) 却始终留在
 * 一个小圆盘内 — 被地形楔住的标志 (导航一直推但位置不动)。空闲 (无移动意图) 的 tick
 * 不计入尝试 — 正常待机永不被误判。
 *
 * <p>女仆适配: "尝试移动" = TLM 导航激活态 ({@code !maid.getNavigation().isDone()},
 * TLM SendMaidDebugDataEvent 同款判定) — 由调用方 (UnstuckCoordinator) 采样传入。
 *
 * @param window          窗口长度 (tick)
 * @param moveThreshold   圆盘半径 (格) — 全程位移小于它才算"没动"
 * @param tryingFraction  窗口内尝试移动占比下限 (0-1)
 */
public final class UnstuckDetector {

    /** 窗口长度 (tick) — Numen 原值 */
    public static final int WINDOW = 40;
    /** "没动"圆盘半径 (格) — Numen 原值 */
    public static final double MOVE_THRESHOLD = 0.75;
    /** 尝试占比下限 — 窗口内至少 80% 的 tick 在尝试移动才算卡住 */
    public static final double TRYING_FRACTION = 0.8;

    private final int window;
    private final double moveThresholdSqr;
    private final double tryingFraction;

    private final double[] xs;
    private final double[] zs;
    private final boolean[] trying;
    private int size;   // 有效样本数 (封顶 window)
    private int head;   // 环形写游标

    public UnstuckDetector() {
        this(WINDOW, MOVE_THRESHOLD, TRYING_FRACTION);
    }

    public UnstuckDetector(int window, double moveThreshold) {
        this(window, moveThreshold, TRYING_FRACTION);
    }

    public UnstuckDetector(int window, double moveThreshold, double tryingFraction) {
        this.window = window;
        this.moveThresholdSqr = moveThreshold * moveThreshold;
        this.tryingFraction = tryingFraction;
        this.xs = new double[window];
        this.zs = new double[window];
        this.trying = new boolean[window];
    }

    /** 记录本 tick 的水平位置 + 是否在尝试移动 (导航激活态)。 */
    public void record(double x, double z, boolean movingInput) {
        xs[head] = x;
        zs[head] = z;
        trying[head] = movingInput;
        head = (head + 1) % window;
        if (size < window) size++;
    }

    /** 清空全部样本 — 脱困尝试后调用, 下一窗口重新评估。 */
    public void reset() {
        size = 0;
        head = 0;
    }

    /** 当前有效样本数 (窗口未满时 < window)。 */
    public int size() {
        return size;
    }

    /**
     * 卡住判定 — 完整窗口内尝试占比 ≥ tryingFraction, 且全程位移都在
     * moveThreshold 圆盘内 (以最新样本为圆心)。
     */
    public boolean isStuck() {
        if (size < window) return false;
        int newest = (head - 1 + window) % window;
        double nx = xs[newest];
        double nz = zs[newest];
        int tryingCount = 0;
        double maxDistSqr = 0.0;
        for (int i = 0; i < window; i++) {
            if (trying[i]) tryingCount++;
            double dx = xs[i] - nx;
            double dz = zs[i] - nz;
            double d = dx * dx + dz * dz;
            if (d > maxDistSqr) maxDistSqr = d;
        }
        return tryingCount >= Math.ceil(window * tryingFraction)
                && maxDistSqr < moveThresholdSqr;
    }
}

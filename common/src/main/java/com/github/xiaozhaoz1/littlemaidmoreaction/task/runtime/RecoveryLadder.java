package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import java.util.List;
import java.util.function.Predicate;

/**
 * 恢复阶梯纯类 (v79.61x 移植自 Numen RecoveryLadder, dwinovo/minecraft-numen LGPL) —
 * 一个有限目标的<b>有序兜底执行阶梯</b>: 方案 A 失败降方案 B, 再失败降方案 C, 耗尽放弃。
 *
 * <p><b>恢复边界规则</b> (Numen 原话): 阶梯只提供"父任务已拥有目标"的替代执行,
 * 永不获取前置条件、永不扩大目标 — 每档 {@link Rung} 用 {@code handles} 声明自己
 * 愿意接的失败类型; 前置类失败 (无材料/工具不对 — 该"踢回认知层"的类目) 不匹配任何
 * 档 → {@link #advance} 返回 false (穿透), 父任务放弃并携带原失败原因。
 *
 * <p>v1 纯推进逻辑 (确定性 + Minecraft-free): 策略绑定 (LMA 管线对象的 Supplier) 由
 * 调用方持有 — {@link #enterCurrent} 标记进入, 执行由父任务驱动; 接 ChainHarvest
 * 守卫链是第二阶段 (行为级改动, 单独排期)。
 *
 * @param <C> 失败原因类型 (LMA 现用 String; 未来可换枚举)
 */
public final class RecoveryLadder<C> {

    /**
     * 一档兜底方案。
     *
     * @param handles     本档愿意接的失败类型判定 (不匹配 = 本档不接, 继续往下找)
     * @param maxAttempts 本档执行次数上限 (≥1; 达到后推进下一档)
     */
    public record Rung<C>(Predicate<C> handles, int maxAttempts) {
        public Rung {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("rung maxAttempts 必须 ≥1: " + maxAttempts);
            }
        }
    }

    private final List<Rung<C>> rungs;
    private final int[] attempts;
    private int index;

    private RecoveryLadder(List<Rung<C>> rungs) {
        this.rungs = rungs;
        this.attempts = new int[rungs.size()];
    }

    /** 构建阶梯 — 至少一档 (空阶梯 = 没有兜底可试) */
    public static <C> RecoveryLadder<C> of(List<Rung<C>> rungs) {
        if (rungs == null || rungs.isEmpty()) {
            throw new IllegalArgumentException("recovery ladder 至少需要一档");
        }
        return new RecoveryLadder<>(rungs);
    }

    /** 当前档下标 — 耗尽时 == 档数 */
    public int currentIndex() {
        return index;
    }

    /** 档数 */
    public int size() {
        return rungs.size();
    }

    /** 阶梯耗尽 — 所有档都试完了 */
    public boolean exhausted() {
        return index >= rungs.size();
    }

    /** 某档已执行次数 (未进入过的档为 0) */
    public int attemptsOn(int i) {
        return attempts[i];
    }

    /** 标记进入当前档 (调用方开始执行策略) — 首次进入记为第 1 次 */
    public void enterCurrent() {
        if (!exhausted()) {
            attempts[index]++;
        }
    }

    /**
     * 失败推进 (Numen 语义):
     * <ul>
     *   <li>当前档接得住 (handles 命中) 且未超重试上限 → 停留当前档, 次数 +1, 返回 true</li>
     *   <li>超上限 / 当前档不接 → 往后找第一个接得住的档, 进入并计 1 次, 返回 true</li>
     *   <li>没有档接得住 (含前置类失败) → 耗尽, 返回 false (父任务放弃)</li>
     * </ul>
     */
    public boolean advance(C cause) {
        if (exhausted()) return false;
        Rung<C> current = rungs.get(index);
        if (current.handles().test(cause) && attempts[index] < current.maxAttempts()) {
            attempts[index]++;
            return true;
        }
        for (int i = index + 1; i < rungs.size(); i++) {
            Rung<C> next = rungs.get(i);
            if (next.handles().test(cause)) {
                index = i;
                attempts[i] = 1;
                return true;
            }
        }
        index = rungs.size();
        return false;
    }

    /** 回到第一档, 全部计数清零 */
    public void reset() {
        index = 0;
        java.util.Arrays.fill(attempts, 0);
    }
}

package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;

import java.util.List;

/**
 * 并行组 — 一组可同时执行的动作，或一个流程控制步骤。
 */
public record ParallelGroup(
    List<ActionStep> actions,   // 动作列表（控制组中为单元素或空）
    int resumeIndex,            // WAIT/REPEAT 恢复索引，-1 = 不适用
    int repeatIdx,              // REPEAT 跳回索引，-1 = 不适用
    int repeatCount,            // REPEAT 剩余次数，-1 = 不适用
    boolean isConditional,      // CONDITIONAL 组
    boolean isCancel,           // CANCEL_EVENT 组
    boolean isRandom,           // RANDOM 概率门组
    double randomChance,        // RANDOM 通过概率 0.0~1.0
    int randomSkip              // RANDOM 未通过时跳过组数
) {
    /** 是否需挂起恢复（WAIT 或 REPEAT） */
    public boolean isAsync() { return resumeIndex >= 0 || repeatIdx >= 0; }

    // --- 工厂方法 ---

    static ParallelGroup actionGroup(List<ActionStep> actions) {
        return new ParallelGroup(List.copyOf(actions), -1, -1, -1, false, false, false, 0, 0);
    }

    static ParallelGroup waitGroup(int idx) {
        return new ParallelGroup(List.of(), idx, -1, -1, false, false, false, 0, 0);
    }

    static ParallelGroup repeatGroup(int idx, int count) {
        return new ParallelGroup(List.of(), idx, idx, count, false, false, false, 0, 0);
    }

    static ParallelGroup cancelGroup() {
        return new ParallelGroup(List.of(), -1, -1, -1, false, true, false, 0, 0);
    }

    static ParallelGroup randomGroup(double chance, int skip) {
        return new ParallelGroup(List.of(), -1, -1, -1, false, false, true, chance, skip);
    }
}

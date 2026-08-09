package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.Set;

/** 管道执行结果 (v63: 新增 needsSignals 字段; v72: 信号泛化为 String id) */
public record PipelineResult(boolean completed, String feedback, @Nullable ItemStack output,
                              Set<String> needsSignals) {
    public static PipelineResult ok(String feedback) {
        return new PipelineResult(true, feedback, null, Set.of());
    }
    public static PipelineResult ok(String feedback, ItemStack output) {
        return new PipelineResult(true, feedback, output, Set.of());
    }
    /** v63: 声明信号需求 (v72: String 信号 id, event:/env: 前缀) */
    public static PipelineResult ok(String feedback, Set<String> needsSignals) {
        return new PipelineResult(true, feedback, null, needsSignals);
    }
    public static PipelineResult failed(String feedback) {
        return new PipelineResult(false, feedback, null, Set.of());
    }
}

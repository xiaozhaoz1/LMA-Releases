package littlemaidmoreaction.littlemaidmoreaction.task.data;

import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSignal;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.Set;

/** 管道执行结果 (v63: 新增 needsSignals 字段) */
public record PipelineResult(boolean completed, String feedback, @Nullable ItemStack output,
                              Set<EnvSignal> needsSignals) {
    public static PipelineResult ok(String feedback) {
        return new PipelineResult(true, feedback, null, Set.of());
    }
    public static PipelineResult ok(String feedback, ItemStack output) {
        return new PipelineResult(true, feedback, output, Set.of());
    }
    /** v63: 声明环境感知信号需求 */
    public static PipelineResult ok(String feedback, Set<EnvSignal> needsSignals) {
        return new PipelineResult(true, feedback, null, needsSignals);
    }
    public static PipelineResult failed(String feedback) {
        return new PipelineResult(false, feedback, null, Set.of());
    }
}

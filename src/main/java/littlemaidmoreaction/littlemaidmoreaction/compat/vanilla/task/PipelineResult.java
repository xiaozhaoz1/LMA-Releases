package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task;

import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

/** 管道执行结果 */
public record PipelineResult(boolean completed, String feedback, @Nullable ItemStack output) {
    public static PipelineResult ok(String feedback) {
        return new PipelineResult(true, feedback, null);
    }
    public static PipelineResult ok(String feedback, ItemStack output) {
        return new PipelineResult(true, feedback, output);
    }
    public static PipelineResult failed(String feedback) {
        return new PipelineResult(false, feedback, null);
    }
}

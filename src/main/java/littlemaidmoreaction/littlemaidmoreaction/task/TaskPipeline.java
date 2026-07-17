package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** 任务管道抽象 — 每个任务类型一个独立实现 */
public interface TaskPipeline {
    String taskType();
    /** @deprecated v18: 使用 validate() 替代 */
    @Deprecated
    PipelineResult execute(ServerLevel level, EntityMaid maid, PipelineContext ctx);
    /** v18: 验证材料 + 写任务参数到 PersistentData */
    PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx);

    /** v35.2: 任务子步骤声明 (供任务树 GUI 展示) */
    default List<TaskStep> steps() { return List.of(); }

    /** 任务子步骤 */
    record TaskStep(String id, String label, StepType type, List<String> dependsOn) {}

    enum StepType { COLLECT, CRAFT, INTERACT, DELIVER, WAIT }
}

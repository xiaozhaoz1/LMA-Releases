package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerLevel;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI入口 — 按 task_type 路由到对应 Pipeline。
 * 在 StartTaskTool.onCall() 中调用。
 */
public final class TaskOrchestrator {

    private static final Map<String, TaskPipeline> PIPELINES = new LinkedHashMap<>();

    /** 注册 Pipeline (在 mod 构造期间调用) */
    public static void register(TaskPipeline pipeline) {
        PIPELINES.put(pipeline.taskType(), pipeline);
    }

    /**
     * v18: 仅验证材料 + 写任务参数到 PersistentData，不写规则文件。
     * Brain Behavior 直接读取参数执行导航+交互。
     */
    public static PipelineResult validate(EntityMaid maid, String taskType, String taskId,
                                          String target, int targetCount) {
        LittleMaidMoreAction.LOGGER.info("[V18] [Orchestrator] validate: task_type={}, target={}, count={}", taskType, target, targetCount);

        TaskPipeline pipeline = PIPELINES.get(taskType);
        if (pipeline == null) {
            return PipelineResult.failed("未知任务类型: " + taskType);
        }

        if (!(maid.level() instanceof ServerLevel level)) {
            return PipelineResult.failed("仅在服务端可用");
        }

        return pipeline.validate(level, maid, new PipelineContext(target, targetCount, taskId));
    }
}

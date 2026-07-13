package littlemaidmoreaction.littlemaidmoreaction.compat.ai.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/**
 * v10 流程驱动任务数据模型 — 状态机驱动 (预留)。
 *
 * <p>状态流转: QUEUED → IN_PROGRESS → PAUSED/COMPLETED/FAILED</p>
 */
public record FlowTask(
    String taskId,
    String taskType,
    String state,
    int currentStep,
    long startedAt,
    long lastStepAt,
    String assignedBy,
    Map<String, String> params
) {
    public static final Codec<FlowTask> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("task_id").forGetter(FlowTask::taskId),
        Codec.STRING.fieldOf("task_type").forGetter(FlowTask::taskType),
        Codec.STRING.fieldOf("state").forGetter(FlowTask::state),
        Codec.INT.fieldOf("current_step").forGetter(FlowTask::currentStep),
        Codec.LONG.fieldOf("started_at").forGetter(FlowTask::startedAt),
        Codec.LONG.fieldOf("last_step_at").forGetter(FlowTask::lastStepAt),
        Codec.STRING.fieldOf("assigned_by").forGetter(FlowTask::assignedBy),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("params", Map.of())
            .forGetter(FlowTask::params)
    ).apply(instance, FlowTask::new));
}

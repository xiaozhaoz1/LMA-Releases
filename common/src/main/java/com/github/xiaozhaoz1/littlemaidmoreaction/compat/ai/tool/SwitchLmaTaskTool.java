package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * AI 工具: 切换 LMA 任务 (v73) — 让女仆执行 LMA 任务 (craft_chain/furnace/collect_wood/
 * ai_control 等)。参数枚举 = TaskRegistry.taskTypes() (动态, 同 SwitchWorkTaskTool 模式)。
 * 权限: AI 操控任务开启 (防止 AI 乱切任务)。
 */
public final class SwitchLmaTaskTool implements ITool<SwitchLmaTaskTool.Result> {

    private static final String TASK_ID = "task_id";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf(TASK_ID).forGetter(Result::taskId)
    ).apply(i, Result::new));

    @Override public String id() { return "switch_lma_task"; }

    @Override public String summary(EntityMaid maid) {
        return "Switch the maid to an LMA task (crafting, furnace, wood collection, ore collection, etc). "
                + "Use when the user wants the maid to do a specific job.";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        StringParameter taskId = StringParameter.create().setDescription("LMA task id to switch to");
        TaskRegistry.taskTypes().stream().sorted().forEach(taskId::addEnumValues);   // varargs: 逐个添加 (同 SwitchWorkTaskTool 模式)
        root.addProperties(TASK_ID, taskId);
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        String taskId = result.taskId();
        if (TaskRegistry.get(taskId) == null) {
            List<String> values = TaskRegistry.taskTypes().stream().sorted().toList();
            return callback.addToolResult(
                    ITool.invalidParam(TASK_ID, values, "Unknown task_id '%s'".formatted(taskId)), toolCallId);
        }
        boolean ok = TaskDispatcher.submit(maid, taskId, null, 0);
        return callback.addToolResult(ok
                ? "Task '%s' started".formatted(taskId)
                : "Task '%s' failed to start (requirements not met)".formatted(taskId), toolCallId);
    }

    @Override
    public boolean trigger(EntityMaid maid,
                           com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion chatCompletion) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid);
    }

    public record Result(String taskId) {}
}

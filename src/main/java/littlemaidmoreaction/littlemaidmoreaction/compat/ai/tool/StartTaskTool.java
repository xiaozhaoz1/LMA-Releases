package littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter.LmaFlowTask;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineResult;

/**
 * v16: AI 唯一任务入口 — 取代 AssignTaskTool + CreateRuleTool + QueryRecipeTool + ...
 * <p>
 * AI 一次调用: lma_start_task(task_type="craft_chain", target="minecraft:stick")
 * → TaskOrchestrator 路由到对应 Pipeline → 自动完成配方/材料/规则/执行
 * <p>
 * 与 TLM 原生 switch_work_task 模式一致: AI 一次调用, 任务自行处理。
 */
public final class StartTaskTool implements ITool<StartTaskTool.Params> {

    public record Params(String taskType, String target, int targetCount) {
        private static volatile Codec<Params> CODEC;

        static Codec<Params> paramsCodec() {
            if (CODEC == null) {
                synchronized (Params.class) {
                    if (CODEC == null) {
                        CODEC = RecordCodecBuilder.create(i -> i.group(
                                Codec.STRING.fieldOf("task_type").forGetter(Params::taskType),
                                Codec.STRING.fieldOf("target").forGetter(Params::target),
                                Codec.INT.optionalFieldOf("target_count", -1).forGetter(Params::targetCount)
                        ).apply(i, Params::new));
                    }
                }
            }
            return CODEC;
        }
    }

    @Override
    public String id() {
        return "lma_start_task";
    }

    @Override
    public String summary(EntityMaid maid) {
        return "Start a task for the maid. Call this DIRECTLY when the owner wants the maid to " +
               "craft/smelt/play music/ring bell. " +
               "task_type: craft_chain | furnace | jukebox | bell_ring. " +
               "target: the item ID (e.g. minecraft:stick). For jukebox: music disc ID. For bell_ring: empty string. " +
               "target_count: -1=unlimited (default), positive number when owner says exact quantity. " +
               "Do NOT query inventory, recipes, or create rules — the system handles everything internally.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties("task_type", StringParameter.create()
                .setDescription("Task type: craft_chain (crafting), furnace (smelt/cook), " +
                        "jukebox (play music), bell_ring. brewing is DELETED."));
        root.addProperties("target", StringParameter.create()
                .setDescription("Target item ID (e.g. minecraft:stick). For jukebox: music disc ID (optional). " +
                        "For bell_ring: empty string."));
        root.addProperties("target_count", IntegerParameter.create()
                .setDescription("How many to make. -1=unlimited (until materials run out). " +
                        "Only specify when owner says exact number. Default -1."), false);
        return root;
    }

    @Override
    public Codec<Params> codec() {
        return Params.paramsCodec();
    }

    @Override
    public LLMCallback onCall(String toolCallId, Params p, LLMCallback cb) {
        LittleMaidMoreAction.LOGGER.info("[V16] [StartTaskTool] AI called lma_start_task: task_type={}, target={}, target_count={}",
                p.taskType, p.target, p.targetCount);
        var maid = cb.getMaid();

        // ★ 清除旧任务残留 (跨session PersistentData)
        var data = maid.getPersistentData();
        String oldTask = data.getString("lma_flow_task");
        String state = data.getString("lma_flow_state");

        // ★ v23: 同任务热重载 — 更新参数但不重建Brain
        if (p.taskType.equals(oldTask) && !oldTask.isEmpty() && "in_progress".equals(state)) {
            data.putString("lma_task_target", p.target);
            data.remove("lma_flow_cached");

            // 唱片机换碟: 根据当前阶段智能切换
            if ("jukebox".equals(p.taskType)) {
                String jukeboxPhase = data.getString("lma_jukebox_phase");
                data.putString("lma_task_target", p.target);
                switch (jukeboxPhase) {
                    case "PLAYING" -> {
                        data.putString("lma_jukebox_phase", "EJECTING");
                        data.putLong("lma_jukebox_tick", maid.level().getGameTime());
                        LittleMaidMoreAction.LOGGER.info("[V23] [StartTaskTool] jukebox change disc: PLAYING→EJECTING, next={}", p.target);
                        return cb.addToolResult("弹出当前唱片，下一轮插入: " + (p.target.isEmpty() ? "随机" : p.target), toolCallId);
                    }
                    case "INSERTING", "PICKUP_WAIT" -> {
                        // 强制重置 INSERTING 以触发立即重新选碟
                        data.putString("lma_jukebox_phase", "INSERTING");
                        data.putLong("lma_jukebox_tick", maid.level().getGameTime());
                        data.remove("lma_jukebox_last");
                        LittleMaidMoreAction.LOGGER.info("[V23] [StartTaskTool] jukebox change disc: {}→INSERTING, target={}", jukeboxPhase, p.target);
                        return cb.addToolResult("换唱片: " + (p.target.isEmpty() ? "随机" : p.target), toolCallId);
                    }
                    default -> {
                        return cb.addToolResult("唱片机正在" + jukeboxPhase + "，已更新下一张唱片", toolCallId);
                    }
                }
            }

            // 合成/熔炉: 更新目标物品和数量
            if ("craft_chain".equals(p.taskType) || "furnace".equals(p.taskType)) {
                data.putInt("lma_flow_max_count", p.targetCount > 0 ? p.targetCount : 0);
                LittleMaidMoreAction.LOGGER.info("[V23] [StartTaskTool] updated task params: target={}, count={}", p.target, p.targetCount);
                return cb.addToolResult("更新任务参数: " + p.target, toolCallId);
            }

            return cb.addToolResult("任务已在运行: " + p.taskType, toolCallId);
        }

        if (!oldTask.isEmpty() && !oldTask.equals(p.taskType)) {
            LittleMaidMoreAction.LOGGER.info("[V16] [StartTaskTool] cleaning old task '{}' before starting '{}'", oldTask, p.taskType);
            LmaFlowTask.restorePreviousTask(maid);
        }
        // 写入新任务标识（先写数据，等Pipeline写完规则后再切换Task触发Brain）
        data.putString("lma_flow_task", p.taskType);
        data.putString("lma_flow_state", "in_progress");
        data.putInt("lma_flow_step", 0);
        data.putInt("lma_flow_max_count", p.targetCount > 0 ? p.targetCount : 0);
        data.putInt("lma_flow_counter", 0);
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        data.putString("lma_task_target", p.target);  // ★ v18.1: Brain 直接读这个字段
        data.remove("lma_flow_cached");
        LmaFlowTask.savePreviousTask(maid);

        // ★ v18: Pipeline 验证材料 → Brain Behavior 直接执行
        String sharedTaskId = String.valueOf(System.currentTimeMillis() % 100000);
        data.putString("lma_flow_task_id", sharedTaskId);
        PipelineResult result = TaskRegistry.validate(maid, p.taskType, sharedTaskId, p.target, p.targetCount);

        if (!result.completed()) {
            data.remove("lma_flow_task"); data.remove("lma_flow_task_id");
            data.remove("lma_flow_state"); data.remove("lma_flow_step");
            LmaFlowTask.restorePreviousTask(maid);
            LittleMaidMoreAction.LOGGER.warn("[V18] [StartTaskTool] validation failed for {}: {}", p.taskType, result.feedback());
            return cb.addToolResult("无法执行" + p.taskType + "任务: " + result.feedback(), toolCallId);
        }

        // 清除 Brain dedup 缓存 + 切换任务 → Brain Behavior 直接导航+交互
        data.remove("lma_flow_cached");
        var newTask = LmaTaskTypeRegistry.findByTaskType(p.taskType);
        maid.setTask(newTask);
        LittleMaidMoreAction.LOGGER.info("[V18] [StartTaskTool] validated, switching to task {}", newTask.getUid());

        LittleMaidMoreAction.LOGGER.info("[V16] [StartTaskTool] result: completed={}, feedback={}", result.completed(), result.feedback());

        String message = result.completed()
                ? "Task '" + p.taskType + "' started: " + result.feedback()
                : "Task '" + p.taskType + "' failed: " + result.feedback();

        return cb.addToolResult(message, toolCallId);
    }

    @Override
    public String invocationSummary(Params p) {
        return "lma_start_task { " + p.taskType + " -> " + p.target + " }";
    }
}

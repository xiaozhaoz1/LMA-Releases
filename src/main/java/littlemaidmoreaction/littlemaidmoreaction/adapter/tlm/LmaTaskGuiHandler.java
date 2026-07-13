package littlemaidmoreaction.littlemaidmoreaction.adapter.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTaskEnableEvent;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.nbt.CompoundTag;

/**
 * GUI 任务切换处理器 — 玩家从 TLM 任务 GUI 手动选择 LMA 任务时的逻辑。
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>添加启用条件描述 — TLM GUI 中显示为什么任务可用/不可用</li>
 *   <li><b>不在此执行任务</b> — MaidTaskEnableEvent 是可用性检查，不是切换通知。</li>
 * </ol>
 *
 * <h3>实际执行</h3>
 * 简单任务的自动启动由 {@link LmaFlowCoordinationBehavior} 在 brain 启动时检测并处理。
 * 复杂任务无 AI 内容时，behavior 不会启动导航，女仆原地等待。
 *
 * <p>调用时机: {@link TlmEventAdapter#onMaidTaskEnable}
 */
public final class LmaTaskGuiHandler {

    /** 翻译 key — 在 zh_cn.json 中定义 */
    static final String KEY_CAN_EXECUTE = "overlay.lma.task_can_execute";
    static final String KEY_NEEDS_AI = "overlay.lma.task_needs_ai_content";

    private LmaTaskGuiHandler() {}

    /**
     * 处理 MaidTaskEnableEvent — 仅为 LMA 任务添加启用条件描述。
     */
    public static void handle(MaidTaskEnableEvent event) {
        IMaidTask target = event.getTargetTask();
        String uidPath = target.getUid().getPath();

        if (!LmaFlowTask.isLmaTask(target)) return;
        if ("flow_task".equals(uidPath)) return;

        String taskType = LmaTaskTypeRegistry.extractTaskType(uidPath);
        if (taskType == null) return;

        EntityMaid maid = event.getEntityMaid();

        // ── 添加启用条件描述 (TLM GUI 显示) ──
        if (LmaTaskTypeRegistry.isSimple(taskType)) {
            // 简单任务 — 永远可执行
            event.addEnableConditionDesc(KEY_CAN_EXECUTE, m -> true);
        } else {
            // 复杂任务 — 需要 AI 预设内容
            event.addEnableConditionDesc(KEY_NEEDS_AI, LmaTaskGuiHandler::hasTaskData);
        }

        LittleMaidMoreAction.LOGGER.debug("[LmaTaskGui] Enable check for '{}': simple={}",
            taskType, LmaTaskTypeRegistry.isSimple(taskType));
    }

    /** 检查 PersistentData 中是否有有效任务数据 (AI 或上次遗留) */
    static boolean hasTaskData(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        // 1. 有活跃任务
        String task = data.getString("lma_flow_task");
        if (!task.isEmpty() && !"none".equals(task)) return true;
        // 2. 有 JSON 数据 (上次 AI 设定)
        String flowData = data.getString("lma_flow_data");
        return flowData != null && !flowData.isEmpty() && !"{}".equals(flowData);
    }
}

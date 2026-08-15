package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTaskEnableEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
//? if 1.20.1 {
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
//?} else {
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * TLM 事件桥接器 — 任务系统相关事件。
 *
 * <p>规则引擎事件链已裁撤 (impl 裁撤后动作无法执行, 30+ 纯转发 handler 删除)。
 * 仅保留任务系统真实现: {@link #onMaidTaskEnable} (GUI 任务切换分流) +
 * {@link #onEntityJoin} (魂符/跨 session 任务恢复)。规则引擎恢复时需重接事件转发。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class TlmEventAdapter {

    // ═══ GUI 手动任务切换 (简单/复杂任务分流) ═══

    @SubscribeEvent public static void onMaidTaskEnable(MaidTaskEnableEvent e) {
        LmaTaskGuiHandler.handle(e);
    }

    // ===== Entity Join — 恢复任务状态 =====

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent e) {
        if (!(e.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide()) return;

        var data = maid.getPersistentData();
        // 门面收编 (remove 段保留 — 键常量已有)
        String task = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid);
        if (task.isEmpty()) return;

        long now = maid.level().getGameTime();
        long savedTick = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTick(maid);

        // 区分场景: 魂符重放置 / 区块加载 (状态完整) vs 跨 session 重启 (tick 失效需清理重提交)
        if (savedTick > 0 && savedTick <= now) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Restore] 魂符恢复任务 '{}' (state={})", task,
                data.getString(TaskKeys.FLOW_STATE));
            // v79.53: 魂符/区块卸载期间心跳停 → FLOW_TICK 陈旧 → 恢复首 tick 看门狗
            // 立即超时 (日志实证 collect_ore 5270t > 1200t, 任务重置丢 KEY_QUEUE/跳过集) →
            // 清 tick 让心跳重起 (状态保留不重置; 跨 session 分支 L68 已有同款清理)
            data.remove(TaskKeys.FLOW_TICK);
        } else {
            LittleMaidMoreAction.LOGGER.info("[LMA/Restore] 跨session恢复任务 '{}'", task);
            data.remove(TaskKeys.FLOW_STATE);
            data.remove(TaskKeys.FLOW_TICK);
            data.remove(TaskKeys.TLM_SWITCH);
            data.remove(TaskKeys.GUI_INIT);
            // ANIM 运行时键全清 — 跨 session 重启后客户端 lastAnimSeq=0,
            // 残留 SEQ 会被 provider 视为新请求重播旧动画。
            for (String key : TaskKeys.ANIM_RUNTIME_KEYS) {
                data.remove(key);
            }
        }

        // 恢复 TLM 任务 + 清理链采瞬态 (PREV_TASK 链路已删 v79.54 — 写方全死恒 idle, 由下方 findTask 覆盖)
        var tlmTask = com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager
            .findTask(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "task/" + task))
            .or(() -> com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager
                .findTask(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, task)));
        if (tlmTask.isPresent()) {
            maid.setTask(tlmTask.get());
        } else {
            maid.setTask(com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager.getIdleTask());
        }

        // 清理链采 BFS/缓存 (跨session和魂符都需要)
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute
            .ChainHarvestExecute.clearChainData(data);

        // 仅跨session重提交, 魂符由Brain自然激活
        if (savedTick <= 0 || savedTick > now) {
            TaskDispatcher.submit(maid, task, null, 0);
        }
    }

    private TlmEventAdapter() {}
}

package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

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
        String task = data.getString(TaskKeys.FLOW_TASK);
        if (task.isEmpty()) return;

        long now = maid.level().getGameTime();
        long savedTick = data.getLong(TaskKeys.FLOW_TICK);

        // 区分场景: 魂符重放置 / 区块加载 (状态完整) vs 跨 session 重启 (tick 失效需清理重提交)
        if (savedTick > 0 && savedTick <= now) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Restore] 魂符恢复任务 '{}' (state={})", task,
                data.getString(TaskKeys.FLOW_STATE));
        } else {
            LittleMaidMoreAction.LOGGER.info("[LMA/Restore] 跨session恢复任务 '{}'", task);
            data.remove(TaskKeys.FLOW_STATE);
            data.remove(TaskKeys.FLOW_TICK);
            data.remove(TaskKeys.FLOW_STEP);
            data.remove(TaskKeys.FLOW_TIMEOUT);
            data.remove(TaskKeys.TLM_SWITCH);
            data.remove(TaskKeys.GUI_INIT);
        }

        // 恢复 TLM 任务 + 清理链采瞬态
        LmaFlowTask.restorePreviousTask(maid);
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

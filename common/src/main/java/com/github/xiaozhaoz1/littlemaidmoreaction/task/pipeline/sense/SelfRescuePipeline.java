package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.SelfRescueCoordinator;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.SelfRescueState;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * v79.58: 自救被动任务 — 掉血触发 (MaidDamageListener → submitPassive),
 * 被埋窒息块瞬破脱困。与主动任务<b>并行执行</b> (用户裁定修订: 不暂停主动任务 —
 * 被埋瞬破与主动动作互不干预; 原 GMPM 暂停守卫方案已删)。
 *
 * <p><b>自救动作分发中心</b> (用户裁定: 被动 tick 结构预留未来更多自救方法):
 * 动作判定链在本 tick 内按 {@link SelfRescueState} 上下文分发 — v1 仅被埋瞬破
 * ({@link SelfRescueCoordinator}); 未来追加: 低血进食 / 岩浆脱困 / 逃跑寻路 等。
 *
 * <p>自终结闭环 (TorchLight 范本): 无自救动作 → cancelPassive (主动任务不受影响)。
 */
public final class SelfRescuePipeline implements TaskPipeline {

    @Override public String taskType() { return "self_rescue"; }
    /** 无信号依赖 — 事件直启; 自终结 (非长任务, 无看门狗) */
    @Override public boolean isLongRunning() { return false; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("", Set.of());
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        // 自救全局开关 (独立配置段 — 从链采集迁出, 预留更多自救设置)
        if (!PassiveTaskConfig.SELF_RESCUE_ENABLED.get()) {
            TaskDispatcher.cancelPassive(maid, taskType());
            return;
        }
        // ── 自救动作判定链 (v79.58 v1 仅被埋瞬破; 未来在此按 SelfRescueState 分发追加方法) ──
        if (SelfRescueCoordinator.tick(world, maid)) {
            return;  // 被埋 → 瞬破 (每 tick, 破完 AABB 不相交自然收敛)
        }
        // 无自救动作 → 上下文消费完 → 自终结 (主动任务下 tick 无缝恢复)
        SelfRescueState.clear(maid);
        TaskDispatcher.cancelPassive(maid, taskType());
    }
}

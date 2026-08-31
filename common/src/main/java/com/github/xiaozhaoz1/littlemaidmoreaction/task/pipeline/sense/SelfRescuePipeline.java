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
 * 与主动任务<b>并行执行</b> (用户裁定修订: 不暂停主动任务)。
 *
 * <p><b>自救动作分发中心</b> — 固定序判定链 (v79.61x 用户裁定, Numen 反射哲学:
 * 布尔判定 + 固定顺序, 最迫近死法在前, 不用浮点优先级):
 * <ol>
 *   <li>被埋窒息瞬破 ({@link SelfRescueCoordinator}, v1 — 窒息持续掉血最迫近)</li>
 *   <li>摔落自救 ({@link com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.MlgRescueCoordinator},
 *       v79.61x — 倒水/垫软方块 + 收水闭环, Numen MLGChain 移植)</li>
 *   <li>卡住脱困 ({@link com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.UnstuckCoordinator},
 *       v79.61x — 导航推不动 40t 检测 + 30t 挣脱 burst, Numen UnstuckChain 移植)</li>
 * </ol>
 * 每 tick 首个触发者执行并独占本轮; 全不触发 → 上下文消费完 → 自终结
 * (TorchLight 范本 — 主动任务不受影响)。换气/低血进食/逃跑不在此列
 * (TLM 原生 MaidBreathAirTask/MaidHealSelfTask/MaidPanicTask 已覆盖, 2026-08-16 源码实证)。
 *
 * <p>MLG 状态 (mlg_* 键) 存 pipelineData — cancelPassive → clearPipelineData 自动清理;
 * Unstuck 窗口状态 per-maid 内存态 — 自终结/卸载清理。
 */
public final class SelfRescuePipeline implements TaskPipeline, com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable {

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
        // ── 自救动作判定链 (固定序: 被埋 > 摔落 > 卡住 — 用户裁定) ──
        if (SelfRescueCoordinator.tick(world, maid)) {
            return;  // 1. 被埋 → 瞬破 (每 tick, 破完 AABB 不相交自然收敛)
        }
        var pd = pipelineData(maid);
        if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.MlgRescueCoordinator.tick(world, maid, pd)) {
            return;  // 2. 摔落中/收水中 → 倒水垫块 (每 tick 重探, 落地收水后自然收敛)
        }
        if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.UnstuckCoordinator.tick(world, maid)) {
            return;  // 3. 卡住 → 30t 挣脱 burst (burst 完窗口重评, 自然收敛)
        }
        // 无自救动作 → 上下文消费完 → 自终结 (主动任务下 tick 无缝恢复)
        SelfRescueState.clear(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.UnstuckCoordinator.clear(maid);
        TaskDispatcher.cancelPassive(maid, taskType());
    }
}

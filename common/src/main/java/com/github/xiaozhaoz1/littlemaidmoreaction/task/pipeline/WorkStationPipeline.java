package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 工作站类管线基类 (furnace/craft_chain/jukebox/bell_ring) — v79.45 双驱动终局。
 *
 * <p>needsGameTick 已删 (v79.46b: GMPM 驱动所有 in_progress 主动管线) — 本类 tick 由
 * GameTickPipelineManager 每 tick 驱动 (心跳 20t + 看门狗均启用 — isLongRunning=true;
 * 2026-08-11c 修正: 原注释"心跳豁免看门狗"写反 — GMPM L101-103/L133 看门狗与心跳
 * 都仅对 isLongRunning 生效, 即长任务有超时保护+续命, 非长任务自终结无兜底);
 * Brain (LmaFlowCoordinationBehavior) 只管导航 + 目标失效重搜。
 * 到达后按 {@link #executeInterval()} 节拍执行 {@link #executeOne}, SUCCESS → 计数/完成判定
 * (原 Brain doExecute L172-195 逻辑迁入)。
 */
public abstract class WorkStationPipeline implements TaskPipeline {

    /** 工作站固定工作点 — TLM 骑乘中不脱离坐骑 */
    @Override public final boolean workPointTask() { return true; }
    /** 工作站全长运行 — GMPM 心跳续命 + 看门狗超时保护 (isLongRunning=true 双生效; 原 furnace/craft_chain/bell_ring 一致; jukebox false→true 安全) */
    @Override public final boolean isLongRunning() { return true; }

    /** 节拍间隔 — 原 Brain EXECUTE_INTERVAL=30 (行为不变) */
    @Override public int executeInterval() { return 30; }

    /**
     * 目标方块判断 — v79.46b 抽象化 (接口默认 false = 忘覆写 → 目标恒"失效" →
     * 擦记忆→重搜无限循环; 编译期强制子类实现)。
     */
    @Override public abstract boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m);

    @Override
    public final void tick(ServerLevel w, EntityMaid m) {
        var mem = m.getBrain().getMemory(InitEntities.TARGET_POS.get());
        if (mem.isEmpty()) return;  // Brain 导航中/无目标 — 等重搜
        BlockPos target = mem.get().currentBlockPosition();

        // 未到达 → 导航中 (心跳由 GMPM 20t 全局写, 不误杀)
        if (target.distSqr(m.blockPosition()) >= VanillaConstants.ARRIVE_DIST_SQR) return;

        // 节拍
        if (w.getGameTime() % executeInterval() != 0) return;

        // 目标失效 → 擦导航记忆 (Brain tick 检测后重搜)
        if (!isTargetBlock(w, target, w.getBlockState(target), m)) {
            m.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return;
        }

        switch (executeOne(w, m, target)) {
            case SUCCESS -> countSuccess(m);
            case FAILED -> MaidChatBubbleApi.showFail(m, taskType() + " 失败");
            case CONTINUE -> { /* 持续执行 */ }
        }
    }

    /** 一次工作单元 (原 Brain 驱动的 execute 迁入) */
    protected abstract TaskResult executeOne(ServerLevel w, EntityMaid m, BlockPos pos);

    /**
     * SUCCESS 计数链 + 完成判定 (原 Brain doExecute L172-195 迁入; TaskDispatcher.complete
     * 不含 setTask(idle)/erase 导航记忆 — 此处显式清理)。
     * v79.46b: 删 max=0 一次性分支 (基类恒 isLongRunning=true → 永假; 工作站 max=0 = 永续任务)。
     */
    protected final void countSuccess(EntityMaid m) {
        int counter = (int) FlowTaskData.getCounter(m) + 1;
        FlowTaskData.setCounter(m, counter);
        int max = (int) FlowTaskData.getMaxCount(m);
        if (max > 0 && counter >= max) {
            m.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            m.setTask(TaskManager.getIdleTask());
            TaskDispatcher.complete(m);
        }
    }
}

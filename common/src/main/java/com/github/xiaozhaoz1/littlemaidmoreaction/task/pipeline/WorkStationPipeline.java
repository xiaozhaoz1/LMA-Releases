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
        BlockPos target = gate(w, m, this);
        if (target == null) return;

        switch (executeOne(w, m, target)) {
            case SUCCESS -> countSuccess(m);
            case FAILED -> MaidChatBubbleApi.showFail(m, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.station.failed", taskType()));
            case CONTINUE -> { /* 持续执行 */ }
        }
    }

    /** 一次工作单元 (原 Brain 驱动的 execute 迁入) */
    protected abstract TaskResult executeOne(ServerLevel w, EntityMaid m, BlockPos pos);

    /**
     * 工作站门 — 到达 + 节拍 + 目标失效 (v79.61x 抽取单一实现, 供
     * {@link WorkStationPipeline#tick} 与 TaskStateMachine(workStationGated) 共用,
     * 消除两处漂移)。
     *
     * @param p 实现 {@link #isTargetBlock}/{@link #executeInterval()} 的管线 (this 或 FSM)
     * @return 已到达且在节拍上的目标方块; 未就绪/导航中/目标失效返回 null (不派发工作单元)
     */
    public static BlockPos gate(ServerLevel w, EntityMaid m, TaskPipeline p) {
        var mem = m.getBrain().getMemory(InitEntities.TARGET_POS.get());
        // v79.63 修**工作站任务不走/不干活** (用户实机: 敲钟/烧炉女仆不移动; gametest 复现 TARGET_POS 恒 false):
        //   Brain 的 searchForDestination 不可靠 (BFS 预筛选/半径/时序多因) ⇒ 这里**管线圈自搜兜底**。
        //   先例 = VoidExcavationPipeline: "LMA 不自写寻路, 只给坐标, 移动靠 TLM" —
        //   设 TARGET_POS (让本行为 tick 不提前返回) + WALK_TARGET (TLM MoveToTargetSink 读它走)。
        if (mem.isEmpty()) {
            if (m.level().getGameTime() % 20 == 0) {          // 1s 一次的节流扫描 (避免每 tick 全扫)
                BlockPos found = searchWorkStation(w, m, p);
                if (found != null) {
                    m.getBrain().setMemory(InitEntities.TARGET_POS.get(),
                            new net.minecraft.world.entity.ai.behavior.BlockPosTracker(found));
                    net.minecraft.world.entity.ai.behavior.BehaviorUtils
                            .setWalkAndLookTargetMemories(m, found, 1.0F, 0);
                }
            }
            return null;   // 本 tick 只负责"发现 + 起走", 到位后由下面逻辑按节拍执行
        }
        BlockPos target = mem.get().currentBlockPosition();

        // 未到达 → 导航中: **每 tick 续写 WALK_TARGET** (TLM MoveToTargetSink 靠它推进; 先例同款)
        if (target.distSqr(m.blockPosition()) >= VanillaConstants.ARRIVE_DIST_SQR) {
            net.minecraft.world.entity.ai.behavior.BehaviorUtils
                    .setWalkAndLookTargetMemories(m, target, 1.0F, 0);
            return null;
        }

        // 节拍
        if (w.getGameTime() % p.executeInterval() != 0) return null;

        // 目标失效 → 擦导航记忆 (Brain tick 检测后重搜)
        if (!p.isTargetBlock(w, target, w.getBlockState(target), m)) {
            m.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return null;
        }
        return target;
    }

    /**
     * 管线圈**自搜工作站** (Brain 搜索不可靠时的兜底) — 有界扫描 + 尊重 home 模式半径。
     *
     * <p>设计对齐用户表述: "熔炉就是一条线的任务 — 先看有无产物, 再看燃料, 再看待烧物" ⇒
     * 工作逻辑是清晰的线性相位机 (见 {@link #executeOne} 的实现), **缺的只是"先走到炉子前"**。
     * 本方法补的就是那一步: 给坐标, 移动交给 TLM。
     *
     * @return 最近的匹配方块 (无则 null)
     */
    private static BlockPos searchWorkStation(ServerLevel w, EntityMaid m, TaskPipeline p) {
        int r = (int) m.getRestrictRadius();
        int range = (m.isHomeModeEnable() && r > 0) ? r : 12;   // home 模式才受 restrict 限制 (同 LmaFlowCoordinationBehavior 修复)
        BlockPos c = m.blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-range, -4, -range), c.offset(range, 4, range))) {
            if (!w.hasChunk(bp.getX() >> 4, bp.getZ() >> 4)) continue;   // 未加载区块不查 (避免卡顿, 同 shouldMoveTo)
            if (!p.isTargetBlock(w, bp, w.getBlockState(bp), m)) continue;
            double d = c.distSqr(bp);
            if (d < bestSq) { bestSq = d; best = bp.immutable(); }
        }
        return best;
    }

    /**
     * SUCCESS 计数链 + 完成判定 (原 Brain doExecute L172-195 迁入; TaskDispatcher.complete
     * 不含 setTask(idle)/erase 导航记忆 — 此处显式清理)。
     * v79.61x 改 static — TaskStateMachine 迁移的 furnace 复用 (按拍计数的 FSM)。
     * v79.46b: 删 max=0 一次性分支 (基类恒 isLongRunning=true → 永假; 工作站 max=0 = 永续任务)。
     */
    static void countSuccess(EntityMaid m) {
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

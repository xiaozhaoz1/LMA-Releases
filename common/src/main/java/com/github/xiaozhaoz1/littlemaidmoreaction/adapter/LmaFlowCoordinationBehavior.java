package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.RecoveryLadder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.List;

/**
 * v53: Brain 导航 + 循环执行。
 * v64: tick 持续循环 — 不擦TARGET_POS, 工作站锚定, 冷却节流。
 * v79.45: 纯导航化 — 执行/计数/完成全迁 GameTickPipelineManager (WorkStationPipeline 基类 tick),
 *   本行为只负责: 导航 (searchForDestination/shouldMoveTo) + 切换检测 (checkExtraStartConditions) +
 *   目标失效重搜。心跳归 GMPM (每 20t, isLongRunning 分支)。
 *
 * <p>工作站任务 (furnace/jukebox/bell_ring/craft_chain):
 * <ul>
 *   <li>start → searchForDestination → WALK_TARGET → 到达</li>
 *   <li>GMPM tick → WorkStationPipeline.tick 节拍执行, TARGET_POS 保留</li>
 *   <li>目标失效(方块被破坏) → 本行为 tick 重搜索 (erase 后 Brain 不自动重搜)</li>
 *   <li>完成判定在管线基类 (SUCCESS 计数链 → TaskDispatcher.complete)</li>
 * </ul>
 */
public final class LmaFlowCoordinationBehavior extends MaidMoveToBlockTask {

    private static final double ARRIVE_DIST_SQR = VanillaConstants.ARRIVE_DIST_SQR;

    /**
     * 导航兜底阶梯 (v79.61x 接守卫链 — 每女仆 Brain 构建时 new, 实例字段安全):
     * R1 重搜 (换同类型目标) ×2 → R2 等重试 (玩家清路) ×3 → R3 放弃 (擦目标+气泡)。
     * 触发源: {@link NavProgressGuard} (目标未变 + 位移 &lt; 0.5 格持续 100t)。
     */
    private final RecoveryLadder<String> ladder = RecoveryLadder.of(List.of(
            new RecoveryLadder.Rung<>(c -> "nav_stuck".equals(c), 2),
            new RecoveryLadder.Rung<>(c -> "nav_stuck".equals(c), 3),
            new RecoveryLadder.Rung<>(c -> "nav_stuck".equals(c), 1)
    ));

    /** v79.62.1 task 缓存 (spark 实证: shouldMoveTo 每候选位置读 NBT getTask = 343% 热点).
     *  per-maid Brain 构建实例, start 读一次, shouldMoveTo 用缓存 — 任务运行中类型不变. */
    private String cachedTask = "";

    /** 导航搜索节流 (tick) — searchForDestination 是 shouldMoveTo 逐候选扫描的入口,
     *  v79.6x 加守卫: 限制每 5t 最多一次, 防「Brain 重搜风暴」× 多女仆把主线程打满.
     *  v79.62.2: 空置域目标坐标由 pipeline 直接设 (TARGET_POS/WALK_TARGET), 不依赖此搜索;
     *  其他任务 (工作站式) 仍走 TLM 搜索 — 限频防风暴. */
    private long lastSearchTick = Long.MIN_VALUE;
    private static final long SEARCH_COOLDOWN = 5;

    private void searchThrottled(ServerLevel world, EntityMaid maid) {
        long now = world.getGameTime();
        if (now - lastSearchTick < SEARCH_COOLDOWN) return;
        lastSearchTick = now;
        searchForDestination(world, maid);
    }

    public LmaFlowCoordinationBehavior() {
        super(1.0F, 4);
        // 2026-08-16 用户实测「点任务好几秒才动」: TLM 父类默认 120t / 原 NAV_CHECK_INTERVAL 100t
        // (5s) 行为检查间隔 → 提交后 Brain 最多等该间隔才 start → searchForDestination 延迟;
        // 降 10t (0.5s) 提交后立即开始找目标 (检查开销: 每 10t 一次条件判断, 可忽略)
        setMaxCheckRate(10);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, EntityMaid maid) {
        if (!super.checkExtraStartConditions(world, maid)) return false;
        // 2026-08-16 用户实测「坐下还是一直移动」: 坐下 (isMaidInSittingPose) 时禁止启动导航行为
        if (maid.isMaidInSittingPose()) return false;

        String task = FlowTaskData.getTask(maid);
        if (!task.isEmpty() && !"none".equals(task)) {
            if (TaskKeys.STATE_IN_PROGRESS.equals(FlowTaskData.getState(maid))) {
                var curTask = maid.getTask();
                if (LmaFlowTask.isLmaTask(curTask)) {
                    String curType = LmaTaskTypeRegistry.extractTaskType(curTask.getUid().getPath());
                    if (curType != null && !curType.equals(task) && TaskRegistry.get(curType) != null) {
                        // 值格式契约: 消费方 GameTickPipelineManager.tickActive 用 ResourceLocation.tryParse
                        // 期望完整 uid (如 "lma:task/craft_chain"); 裸 taskType 无 ":" 解析失败 → 误 cancel (错题 #179)
                        TaskMetaData.setTlmSwitch(maid, curTask.getUid().toString());
                    }
                }
                return true;
            }
            String flowState = FlowTaskData.getState(maid);
            if (!flowState.isEmpty() && !TaskKeys.STATE_IN_PROGRESS.equals(flowState)) {
                return false;
            }
        }

        // FLOW_TASK 为空 + TLM task 是 LMA + 非 CANCELLED → 自动启动
        // (CANCELLED 检查保留 — 启动守卫语义: 防异常路径/旧档残留状态带病自动启动,
        // 成本零; 与 tick 路径的取消检查不同, 后者 2026-08-16 已删)
        var maidTask = maid.getTask();
        if (LmaFlowTask.isLmaTask(maidTask)) {
            if (TaskKeys.STATE_CANCELLED.equals(FlowTaskData.getState(maid))) {
                return false;
            }
            String taskType = LmaTaskTypeRegistry.extractTaskType(maidTask.getUid().getPath());
            if (taskType != null && TaskRegistry.get(taskType) != null && TaskToggle.isEnabled(taskType)) {
                TaskMetaData.setGuiInit(maid, taskType);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, EntityMaid maid, long gameTime) {
        return TaskKeys.STATE_IN_PROGRESS.equals(
            FlowTaskData.getState(maid));
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel world, EntityMaid maid, BlockPos pos) {
        // v79.62.1 零计算: 用 start 缓存的 task (不读 NBT — spark 实证 getTask 343% 热点)
        var handler = cachedTask.isEmpty() ? null : TaskRegistry.get(cachedTask);
        if (handler == null) return false;
        // v79.62.1 修卡顿 (spark 实证: shouldMoveTo→getBlockState→getChunk→managedBlock/parkNanos 阻塞
        // 等区块生成, 主线程 66% 时间等待 = MSPT 61s): 目标区块未加载 → false (不 getBlockState, 不阻塞)
        if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        return handler.pipeline().isTargetBlock(world, pos, world.getBlockState(pos), maid);
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTime) {
        String taskType = FlowTaskData.getTask(maid);
        this.cachedTask = taskType;   // v79.62.1 缓存 (shouldMoveTo 用, 任务运行中不变)
        if (taskType.isEmpty()) return;

        var handler = TaskRegistry.get(taskType);
        if (handler == null) return;

        LittleMaidMoreAction.LOGGER.info("[LMA/Brain] start task={} at {}", taskType, maid.blockPosition().toShortString());
        searchThrottled(world, maid);
        if (maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Brain] navigating to target for {}", taskType);
        } else {
            // 无目标不再就地执行 (执行归 GMPM/管线 tick) — 目标失效场景由 tick 重搜兜底
            LittleMaidMoreAction.LOGGER.debug("[LMA/Brain] no target found for {}, awaiting GMPM tick", taskType);  // 2026-08-16 降噪: 高频 INFO→DEBUG
        }
    }

    /**
     * 纯导航 tick — 目标失效重搜 + 导航进展守护 (erase 后 Brain 不自动重搜 — canStillUse 恒 true)。
     * 心跳/节拍/执行全删 (归 GMPM + WorkStationPipeline.tick)。
     */
    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        // 2026-08-16 用户实测: 坐下时运行中的导航也停 (TLM 原生任务坐下即停 — LMA 行为对齐)
        if (maid.isMaidInSittingPose()) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return;
        }
        var mem = maid.getBrain().getMemory(InitEntities.TARGET_POS.get());
        if (mem.isEmpty()) {
            // [NAV-DBG] 诊断: TARGET_POS 空 — 看 WALK_TARGET 是否由 pipeline 设置 (MoveToTargetSink 靠它走)
            if (world.getGameTime() % 200 == 0) {
                boolean hasWalk = maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET);
                boolean hasNav = maid.getBrain().hasMemoryValue(LmaMemoryModuleRegistry.NAV_TARGET.get());
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[NAV-DBG] no-target maid={} pos={} walkTarget={} navTarget={} task={}",
                    maid.getStringUUID().substring(0, 8), maid.blockPosition().toShortString(),
                    hasWalk, hasNav, cachedTask);
            }
            return;
        }

        BlockPos target = mem.get().currentBlockPosition();

        // 目标失效(方块被破坏/替换) → 重搜索
        if (!shouldMoveTo(world, maid, target)) {
            // [NAV-DBG] 诊断: 目标失效 (isTargetBlock false / 区块未加载)
            if (world.getGameTime() % 200 == 0) {
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[NAV-DBG] target-invalid maid={} target={} hasChunk={} isTarget={}",
                    maid.getStringUUID().substring(0, 8), target.toShortString(),
                    world.hasChunk(target.getX() >> 4, target.getZ() >> 4),
                    shouldMoveTo(world, maid, target));
            }
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            searchThrottled(world, maid);
            NavProgressGuard.reset(maid); // 新目标重新计时
            return;
        }

        // v79.61x 导航进展守护 — 目标有效但 TLM 走不到 (墙后/悬崖/被围) →
        // RecoveryLadder 兜底: R1 重搜 ×2 → R2 等重试 ×3 → R3 放弃 (擦目标+气泡)
        NavProgressGuard.track(maid, target, gameTime);
        if (NavProgressGuard.isStuck(maid, gameTime)) {
            // [NAV-DBG] 诊断: 导航进展卡住 (TLM 走不到目标)
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                "[NAV-DBG] nav-stuck maid={} target={} pos={} ladder={}",
                maid.getStringUUID().substring(0, 8), target.toShortString(),
                maid.blockPosition().toShortString(), ladder.currentIndex());
        }
        if (!NavProgressGuard.isStuck(maid, gameTime)) return;

        if (!ladder.advance("nav_stuck")) {
            // 耗尽兜底 (全档不接 — 理论不可达): 幂等脱困
            abandonTarget(maid);
            return;
        }
        ladder.enterCurrent();
        NavProgressGuard.reset(maid); // 进入新档重新计时
        switch (ladder.currentIndex()) {
            case 0 -> {
                // R1 重搜 — 可能找到更近/可达的同类型目标
                maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                searchThrottled(world, maid);
            }
            case 1 -> {
                // R2 等重试 — 玩家可能清路; guard 已 reset, 下一窗口再判
            }
            case 2 -> abandonTarget(maid); // R3 放弃
            default -> { }
        }
    }

    /** 放弃当前目标 — 擦 TARGET_POS/WALK_TARGET + 清守护 + 气泡 (任务保留, TLM 重选重来) */
    private void abandonTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        NavProgressGuard.clear(maid);
        MaidChatBubbleApi.showFail(maid, "到不了目标");
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        ladder.reset();          // 任务结束 → 阶梯回第一档 (防跨任务残留)
        NavProgressGuard.clear(maid);
        // v79.62.2 区块工作制已移入 pipeline 内部 (ChunkWorkArea 方法) — behavior 不再管 home/workPos/restrict
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskProgressDisplay;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.WorkStationPipeline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 泛型状态机引擎 (v46).
 *
 * <p>子类定义 {@code S} 枚举作为状态集合。引擎自动处理:
 * <ul>
 *   <li>状态存储 (v79.31 内存化) — {@code MaidData.pl(maid, "<type>.fsm")} 的 FSM_KEY，零 NBT</li>
 *   <li>转换合法性验证 — {@link #transitions()} 图，非法转换被拦截 + 日志</li>
 *   <li>取消检测 — 每 tick 入口检查 {@link TaskKeys#STATE_CANCELLED}</li>
 *   <li>进入/退出钩子 — {@link #onEnter(Enum, ServerLevel, EntityMaid)} / {@link #onExit(Enum, EntityMaid)}</li>
 *   <li>执行 — 子类覆写 {@link #tick(Enum, ServerLevel, EntityMaid)}, GameTick 驱动</li>
 * </ul>
 *
 * <h3>任务终结</h3>
 * 引擎本身不负责"完成"判断。子类应在适当的时机主动调用:
 * <pre>{@code
 * TaskDispatcher.complete(maid);   // 任务成功
 * TaskDispatcher.fail(maid, reason); // 任务失败
 * }</pre>
 * 调用后 {@code TaskDispatcher} 设置 {@code STATE_COMPLETED/STATE_FAILED} 并 {@code clearAll}，
 * {@code TaskTickHandler} 检测到非 {@code in_progress} 后自然停止 tick。
 *
 * <h3>子类标准长相 (四段式约定, v79.61) — 固定顺序: 状态枚举 → transitions() → onEnter/onExit → tick()</h3>
 *
 * <h3>子类必须实现</h3>
 * <ul>
 *   <li>{@link #taskType()} — 任务类型标识</li>
 *   <li>{@link #stateClass()} — 状态枚举 Class 对象（用于 NBT 序列化）</li>
 *   <li>{@link #initialState()} — 初始状态</li>
 *   <li>{@link #tick(Enum, ServerLevel, EntityMaid)} — 业务逻辑 + 状态转换</li>
 * </ul>
 *
 * <h3>子类可选覆写</h3>
 * <ul>
 *   <li>{@link #transitions()} — 转换合法性约束</li>
 *   <li>{@link #steps()} — 任务步骤（供 GUI 展示）</li>
 *   <li>{@link #onEnter(Enum, ServerLevel, EntityMaid)} — 进入状态钩子</li>
 *   <li>{@link #onExit(Enum, EntityMaid)} — 退出状态钩子</li>
 *   <li>{@link #cleanup(EntityMaid)} — 清理额外 NBT 键</li>
 *   <li>{@link #validate(ServerLevel, EntityMaid, PipelineContext)} — 自定义验证</li>
 * </ul>
 *
 * @param <S> 状态枚举类型，必须实现 {@code Enum<S>}
 */
public abstract class TaskStateMachine<S extends Enum<S>> implements TaskPipeline {

    // ── 子类必须实现 ──

    /** 状态枚举 Class — 用于 NBT 序列化 {@link Enum#valueOf(Class, String)} */
    protected abstract Class<S> stateClass();

    /** 初始状态 — 首次 tick 或无状态记录时进入 */
    protected abstract S initialState();

    /**
     * 每 tick 执行的业务逻辑.
     *
     * @param currentState 当前状态（已从 NBT 读取，保证非 null）
     * @param world        服务端世界
     * @param maid         女仆实体
     * @return {@code null} = 保持当前状态; 返回新状态 = 触发转换
     */
    protected abstract S tick(S currentState, ServerLevel world, EntityMaid maid);

    @Override
    public abstract String taskType();

    // ── 可选覆写 ──

    /**
     * 状态转换图 — 定义合法转换.
     * 返回空 Map ({@code Map.of()}) = 不限制转换（轻量级使用）。
     * 非法转换被引擎拦截 + 日志警告 + 保持当前状态。
     */
    protected Map<S, Set<S>> transitions() { return Map.of(); }

    /** 进入状态时调用 */
    protected void onEnter(S state, ServerLevel world, EntityMaid maid) {}

    /** 退出状态时调用 */
    protected void onExit(S state, EntityMaid maid) {}

    @Override
    public List<TaskStep> steps() { return List.of(); }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");
    }

    /**
     * 清理钩子 — 子类覆写以移除管线特有的 NBT 键.
     * 在所有终结路径（cancel/complete/fail/timeout）都会调用。
     */
    protected void cleanup(EntityMaid maid) {
        clearState(maid);
    }

    // ── TaskPipeline 自动实现 ──

    @Override
    public boolean isLongRunning() { return true; }

    /**
     * 工作站式门 (到达 + 节拍 + 目标失效) — v79.61x 方案 A: FSM 引擎共用
     * {@link WorkStationPipeline#gate} 单一实现, 迁移后的工作站状态机 (furnace/jukebox)
     * 覆写返回 true 获得与原 WorkStationPipeline.tick 相同的门控语义。
     * 默认 false = 每 tick 直接派发 FSM (导航型状态机 crank/power/… 保持现状)。
     */
    protected boolean workStationGated() { return false; }

    @Override
    public void interrupt(EntityMaid maid) {
        onExit(readState(maid), maid);
        onCleanup(maid);  // interrupt 统一走 onCleanup
    }

    /**
     * onCleanup 委托 cleanup()，确保子类覆写的清理逻辑在所有终结路径生效。
     */
    @Override
    public void onCleanup(EntityMaid maid) {
        cleanup(maid);
        TaskPipeline.super.onCleanup(maid);
    }

    // ── 状态存储 (内存化 — 存 MaidData.pl(maid, "<type>.fsm") 的 "s" 键,
    //   tick 内零 NBT 读 (原每 tick getString + Enum.valueOf + try/catch);
    //   flush 由心跳 flushAllPl / 实体离开 / ServerStopping / 终结兜底) ──

    private static final String FSM_KEY = "s";
    private static final String FSM_SUFFIX = ".fsm";

    /** 状态存储键 (PL 缓存内独立键 — 与管线 pipelineData(lma_pl_<type>) 不冲突) */
    private String fsmKey() {
        return taskType() + FSM_SUFFIX;
    }

    // ── 纯函数 (单测直接调用 — 禁 MC 类加载) ──

    /**
     * 转换合法性校验 — 纯逻辑。
     * 空图 (Map.of()) = 不限制转换; current 无出边或 next 不在出边集 = 非法 (引擎拦截)。
     */
    static <S extends Enum<S>> boolean isTransitionAllowed(S current, S next, Map<S, Set<S>> transitions) {
        if (transitions.isEmpty()) return true;
        return transitions.getOrDefault(current, Set.of()).contains(next);
    }

    /**
     * 状态名 → 枚举解析容错 — 纯逻辑 (防 NBT 损坏防线)。
     * 空/null/未知名/异常 → fallback (initialState)。
     */
    static <S extends Enum<S>> S parseState(Class<S> stateClass, String raw, S fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Enum.valueOf(stateClass, raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * 从内存态读取当前状态。异常时返回 {@link #initialState()} (防 NBT 损坏)。
     */
    protected S readState(EntityMaid maid) {
        try {
            String raw = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, fsmKey()).getString(FSM_KEY);
            return parseState(stateClass(), raw, initialState());
        } catch (Exception e) {
            return initialState();
        }
    }

    /** 写入状态到内存态 (flush 落盘) */
    protected void writeState(EntityMaid maid, S state) {
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, fsmKey()).putString(FSM_KEY, state.name());
    }

    /** 移除状态 (缓存 + NBT) */
    protected void clearState(EntityMaid maid) {
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.removePl(maid, fsmKey());
    }

    // ── 主 tick ──

    /**
     * 每 tick 由 {@code TaskTickHandler} (→ GameTickPipelineManager.tickActive) 驱动.
     *
     * <p>流程:
     * <ol>
     *   <li>取消检测 — {@code STATE_CANCELLED} → {@link #interrupt(EntityMaid)}</li>
     *   <li>读取当前状态（首 tick 初始化为 {@link #initialState()} + 调 {@link #onEnter}）</li>
     *   <li>执行子类的 {@link #tick(Enum, ServerLevel, EntityMaid)}</li>
     *   <li>转换验证 → {@link #onExit} → 写入 → {@link #onEnter}</li>
     * </ol>
     */
    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        // 防御 — 任务已被终结/切换 (clearAll 后 getTask 空, 或已切到其他任务) →
        // 不 tick, 清状态残留 (防跨任务污染; 错题 #124 同类: 终结后继续执行的通用防线 —
        // 覆盖 GameTickPipelineManager/外部 mod 全驱动路径)
        String flowTask = FlowTaskData.getTask(maid);
        if (flowTask.isEmpty() || !taskType().equals(flowTask)) {
            clearState(maid);
            return;
        }
        // 1. 取消检测
        if (TaskKeys.STATE_CANCELLED.equals(FlowTaskData.getState(maid))) {
            interrupt(maid);
            return;
        }

        // 1.5 工作站式门 (到达 + 节拍 + 目标失效) — furnace/jukebox 迁移用; 默认关
        if (workStationGated() && WorkStationPipeline.gate(world, maid, this) == null) {
            return;
        }

        // 2. 读取当前状态 (内存态 — isFirstTick 判 "s" 键是否存在)
        S current = readState(maid);
        boolean isFirstTick = !com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, fsmKey()).contains(FSM_KEY);
        if (isFirstTick) {
            writeState(maid, current);
            onEnter(current, world, maid);
            showStepBubble(maid, current);   // 步骤气泡 (替换式)
        }

        // 3. 执行业务逻辑
        S next = tick(current, world, maid);

        // 4. 状态转换
        if (next != null && next != current) {
            // 验证转换合法性
            if (!isTransitionAllowed(current, next, transitions())) {
                LittleMaidMoreAction.LOGGER.warn(
                    "[LMA/FSM] {} illegal transition: {} -> {}", taskType(), current, next);
                return; // 拒绝非法转换，保持当前状态
            }
            LittleMaidMoreAction.LOGGER.debug(
                "[LMA/FSM] {} {} -> {}", taskType(), current, next);
            onExit(current, maid);
            writeState(maid, next);
            onEnter(next, world, maid);
            showStepBubble(maid, next);      // 步骤气泡 (替换式)
        }
    }

    // ── 步骤气泡 ──

    /**
     * 状态步骤气泡 — 首 tick + 每次合法转换触发, 替换式进度气泡 (循环状态不堆积).
     * 引擎直接调用 (独立于 onEnter 钩子 — 子类覆写 onEnter 不影响本行为).
     */
    private void showStepBubble(EntityMaid maid, S state) {
        LmaTaskProgressDisplay.showStep(maid, taskType(), state.name());
    }

    // executor/execute 删除 (v79.45) — 执行全归 GMPM tick 驱动, 无需覆写
}

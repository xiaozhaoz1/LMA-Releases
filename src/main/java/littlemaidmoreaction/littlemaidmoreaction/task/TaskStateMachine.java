package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
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
 *   <li>状态 NBT 持久化 — 键名 {@code "lma_fsm_<taskType>"}，与 {@code lma_flow_*} 隔离</li>
 *   <li>转换合法性验证 — {@link #transitions()} 图，非法转换被拦截 + 日志</li>
 *   <li>取消检测 — 每 tick 入口检查 {@link TaskKeys#STATE_CANCELLED}</li>
 *   <li>进入/退出钩子 — {@link #onEnter(Enum, ServerLevel, EntityMaid)} / {@link #onExit(Enum, EntityMaid)}</li>
 *   <li>默认 executor — {@link #executor()} 返回匿名 IExecutor，含 onStop 清理</li>
 * </ul>
 *
 * <h3>任务终结</h3>
 * 引擎本身不负责"完成"判断。子类应在适当的时机主动调用:
 * <pre>{@code
 * TaskDispatcher.complete(maid);   // 任务成功
 * TaskDispatcher.fail(maid, reason); // 任务失败
 * }</pre>
 * 调用后 {@code TaskDispatcher} 设置 {@code STATE_COMPLETED/STATE_FAILED} 并 {@code clearAll}，
 * {@code CreateEventListener} 检测到非 {@code in_progress} 后自然停止 tick。
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

    @Override
    public void interrupt(EntityMaid maid) {
        onExit(readState(maid), maid);
        onCleanup(maid);
    }

    /**
     * v46 修正: onCleanup 委托 cleanup()，确保子类覆写的清理逻辑在所有终结路径生效。
     */
    @Override
    public void onCleanup(EntityMaid maid) {
        cleanup(maid);
    }

    // ── NBT 序列化 ──

    /** 状态 NBT 键 — 格式 {@code "lma_fsm_<taskType>"}，与 {@code lma_flow_*} 隔离 */
    protected String stateKey() {
        return "lma_fsm_" + taskType();
    }

    /**
     * 从 NBT 读取当前状态。异常时返回 {@link #initialState()}。
     */
    protected S readState(EntityMaid maid) {
        try {
            String raw = maid.getPersistentData().getString(stateKey());
            if (raw.isEmpty()) return initialState();
            return Enum.valueOf(stateClass(), raw);
        } catch (Exception e) {
            return initialState();
        }
    }

    /** 写入状态到 NBT */
    protected void writeState(EntityMaid maid, S state) {
        maid.getPersistentData().putString(stateKey(), state.name());
    }

    /** 移除 NBT 中的状态键 */
    protected void clearState(EntityMaid maid) {
        maid.getPersistentData().remove(stateKey());
    }

    // ── 主 tick ──

    /**
     * 每 tick 由 {@code CreateEventListener} 或 {@link #executor()} 驱动.
     *
     * <p>流程:
     * <ol>
     *   <li>取消检测 — {@code STATE_CANCELLED} → {@link #interrupt(EntityMaid)}</li>
     *   <li>读取当前状态（首 tick 初始化为 {@link #initialState()} + 调 {@link #onEnter}）</li>
     *   <li>执行子类的 {@link #tick(Enum, ServerLevel, EntityMaid)}</li>
     *   <li>转换验证 → {@link #onExit} → 写入 → {@link #onEnter}</li>
     * </ol>
     */
    protected void tick(ServerLevel world, EntityMaid maid) {
        // 1. 取消检测
        if (TaskKeys.STATE_CANCELLED.equals(LmaTaskDataHelper.getFlowState(maid))) {
            interrupt(maid);
            return;
        }

        // 2. 读取当前状态
        S current = readState(maid);

        // 3. 执行业务逻辑
        S next = tick(current, world, maid);

        // 4. 状态转换
        if (next != null && next != current) {
            // 验证转换合法性
            Map<S, Set<S>> t = transitions();
            if (!t.isEmpty() && !t.getOrDefault(current, Set.of()).contains(next)) {
                LittleMaidMoreAction.LOGGER.warn(
                    "[LMA/FSM] {} illegal transition: {} -> {}", taskType(), current, next);
                return; // 拒绝非法转换，保持当前状态
            }
            LittleMaidMoreAction.LOGGER.debug(
                "[LMA/FSM] {} {} -> {}", taskType(), current, next);
            onExit(current, maid);
            writeState(maid, next);
            onEnter(next, world, maid);
        }
    }

    // ── Executor ──

    /**
     * 默认 executor — 委托给 {@link #tick(ServerLevel, EntityMaid)}.
     *
     * <p>使用匿名类（非 lambda）以确保 {@code onStop()} 可覆写。
     */
    public IExecutor executor() {
        return new IExecutor() {
            @Override
            public TaskResult execute(ServerLevel world, EntityMaid maid, net.minecraft.core.BlockPos pos, CompoundTag data) {
                tick(world, maid);
                return TaskResult.CONTINUE;
            }

            @Override
            public void onStop(EntityMaid maid) {
                cleanup(maid);
            }
        };
    }
}

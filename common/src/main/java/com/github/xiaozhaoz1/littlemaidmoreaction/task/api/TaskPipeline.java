package com.github.xiaozhaoz1.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 任务管道抽象 (v79.28 接口瘦身) — 每个任务类型一个独立实现。
 *
 * <p>仅承载核心契约: 执行 (tick) + 生命周期 (interrupt/onCleanup/
 * isLongRunning) + 验证/展示 (validate/steps/priority/isTargetBlock)。
 * 配置维度见 {@link TaskConfigurable} (按需实现), 信号维度见 {@link TaskSignalListener}。
 *
 * <p>v79.36: 重试机制 (RetryPolicy) 删除 — 主动任务靠 TLM 任务栏自动重启, 被动靠信号重触发。
 *
 * <p>v79.61 架构裁定: 管道类标准长相 (四段式 — 任何管道 30 秒读懂):
 * <pre>{@code
 * class XxxPipeline implements TaskPipeline[, TaskConfigurable][, BlockTargetNavigation] {
 *     // 1. 身份: taskType() / steps() / isLongRunning()
 *     // 2. 行为: tick() — FSM 类固定顺序: 状态枚举 → transitions() → onEnter/onExit → tick()
 *     //    tick 用 switch 分派 (短状态内联, 长状态拆顶层 per-state 方法 — v79.61x 收敛, 无处理器表双重间接)
 *     // 3. 配置: (可选) TaskConfigurable 面
 *     // 4. 私有业务方法 — 复杂业务算法抽 service 类 (五层尺: pipeline/execute/service/behavior/input-output)
 * }}</pre>
 * 同类任务多个变体 → 一个类 + 构造参数 (ChainHarvestPipeline(Mode) 先例), 不建工厂类。
 */
public interface TaskPipeline {
    String taskType();

    /** 纯验证 — 必须无副作用。默认返回 ok。 */
    default PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");
    }

    /** 任务子步骤声明 (供任务树 GUI 展示) */
    default List<TaskStep> steps() { return List.of(); }

    /**
     * 中断回调。默认委托 onCleanup()。
     */
    default void interrupt(EntityMaid maid) {
        onCleanup(maid);
    }

    /**
     * 清理钩子。默认清除 pipelineData() (实现 TaskConfigurable 时)。
     * 覆写时调 super.onCleanup() 或手动清理。
     */
    default void onCleanup(EntityMaid maid) {
        if (this instanceof TaskConfigurable c) {
            c.clearPipelineData(maid);
        }
    }

    /** 长运行任务 — 需要调度器自动心跳 */
    default boolean isLongRunning() { return false; }

    /**
     * 任务优先级 — 提交冲突时决策 (TaskDispatcher.submit).
     * 语义: 新任务优先级严格低于当前任务 → 拒绝; 等/高 → 抢占.
     * 默认 0 — 树内全部任务等优先级, 保留既有切换行为; 外部 mod 选择加入.
     */
    default int priority() { return 0; }

    /** 目标方块判断 — 供 Brain 导航匹配 */
    default boolean isTargetBlock(ServerLevel world, BlockPos pos, BlockState state, EntityMaid maid) { return false; }

    /**
     * 每游戏 tick 回调 — v79.46b: GMPM 驱动所有 in_progress 主动管线 (needsGameTick 字段已删,
     * 防新管线忘声明 = 静默死任务)。
     */
    default void tick(ServerLevel world, EntityMaid maid) {}

    /**
     * 是否有固定工作点 (工作站类任务: 熔炉/合成/唱片机/敲钟/搬运/交互)。
     * 对齐 TLM {@code IMaidTask.workPointTask} 语义 — 影响 TLM 骑乘调度
     * (工作点任务骑乘中不脱离坐骑) + KubeJS 构建器兼容。
     */
    default boolean workPointTask() { return false; }

    /**
     * 工作站节拍间隔 (tick) — WorkStationPipeline 覆写 30 (原 Brain EXECUTE_INTERVAL)。
     * 默认 10; 语义 = 到达后 now % interval == 0 才执行一次工作单元。
     */
    default int executeInterval() { return 10; }

    /** 任务子步骤 */
    record TaskStep(String id, String label, StepType type, List<String> dependsOn) {}

    enum StepType { COLLECT, CRAFT, INTERACT, DELIVER }
}

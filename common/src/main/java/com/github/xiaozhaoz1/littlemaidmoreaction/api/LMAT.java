package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.io.IExecutor;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;

/**
 * LMA 任务系统统一入口 — 外部 mod 只需 import 这一个类。
 *
 * <pre>
 * import com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT;
 *
 * // 简单任务 (继承 LMAT.Task 即可)
 * class MyTask extends LMAT.Task {
 *     MyTask() { super("my_task"); }
 * }
 * LMAT.register("my_task", new MyTask(), LMAT.exec((w, m, p, d) -> {
 *     doWork(w, m, p);
 *     return LMAT.OK;
 * }));
 *
 * // FSM 任务
 * LMAT.register(new MyFSM());
 *
 * // 触发
 * LMAT.submit(maid, "my_task", "minecraft:diamond", 64);
 * </pre>
 */
public final class LMAT {

    private LMAT() {}

    // ── 快捷类型 (消除外部 import) ──

    /** 简单任务基类 — 继承即可, 只覆写你需要的方法 */
    public abstract static class Task implements TaskPipeline {
        private final String type;
        protected Task(String taskType) { this.type = taskType; }
        @Override public String taskType() { return type; }
    }

    /** 执行成功 */
    public static final TaskResult OK = TaskResult.SUCCESS;
    /** 执行中 (持续任务) */
    public static final TaskResult CONTINUE = TaskResult.CONTINUE;

    /** 验证通过 */
    public static PipelineResult ok(String msg) { return PipelineResult.ok(msg); }
    /** 验证失败 */
    public static PipelineResult failed(String msg) { return PipelineResult.failed(msg); }
    /** 包装执行器 (lambda → IExecutor) */
    public static IExecutor exec(IExecutor e) { return e; }

    // ── 注册 ──

    /** 注册任务 (默认显示在TLM任务栏) */
    public static void register(String taskType, TaskPipeline pipeline, IExecutor executor) {
        TaskRegistry.register(taskType, pipeline, executor);
    }

    /** 注册任务 (控制是否在TLM任务栏显示) */
    public static void register(String taskType, TaskPipeline pipeline, IExecutor executor, boolean showInBar) {
        TaskRegistry.register(taskType, pipeline, executor, showInBar);
    }

    /** 注册 FSM 任务 (TaskStateMachine 自带 executor, 默认显示) */
    public static <S extends Enum<S>> void register(TaskStateMachine<S> fsm) {
        TaskRegistry.register(fsm.taskType(), fsm, fsm.executor());
    }

    /** 注册 FSM 任务 + 控制任务栏显示 */
    public static <S extends Enum<S>> void register(TaskStateMachine<S> fsm, boolean showInBar) {
        TaskRegistry.register(fsm.taskType(), fsm, fsm.executor(), showInBar);
    }

    /** 注册被动任务 (不显示在任务栏, 由 EnvSense/AI/规则引擎内部触发) */
    public static void registerPassive(String taskType, TaskPipeline pipeline) {
        TaskRegistry.registerPassive(taskType, pipeline);
    }

    /**

    /**

    // ── 生命周期 ──

    /** 启动任务。返回 false 表示验证失败 (材料不足/目标无效) */
    public static boolean submit(EntityMaid maid, String taskType, String target, int count) {
        return TaskDispatcher.submit(maid, taskType, target, count);
    }

    /** 取消当前任务 (执行 interrupt → onCleanup → clearAll) */
    public static void cancel(EntityMaid maid) {
        TaskDispatcher.cancel(maid);
    }

    // ── 查询 ──

    /** 当前任务类型，无任务时返回空字符串 {@code ""} */
    public static String currentTask(EntityMaid maid) {
        return FlowTaskData.getTask(maid);
    }

    /** 当前任务状态：in_progress / completed / failed / cancelled / stopped / queued */
    public static String currentState(EntityMaid maid) {
        return FlowTaskData.getState(maid);
    }

    /** 是否有任务正在执行 */
    public static boolean isBusy(EntityMaid maid) {
        return TaskKeys.STATE_IN_PROGRESS.equals(FlowTaskData.getState(maid));
    }

    /**
     * TLM 女仆界面「任务设置」标签 GUI — 自动查找当前 Pipeline 的配置界面。
     * Pipeline 覆写 {@link TaskPipeline#getConfigGuiProvider(EntityMaid)} 后即可生效。
     *
     * @return 配置 GUI 的 MenuProvider，无任务或未覆写时返回 {@code null}
     */
    @javax.annotation.Nullable
    public static net.minecraft.world.MenuProvider configGui(EntityMaid maid) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory.of(maid);
    }
}

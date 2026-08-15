package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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
 * <p><b>一句话注册:</b> {@code LMAT.register("my_task", new MyTask());} 一行即可注册进 LMA
 * 任务系统 (驱动/验证/优先级/TLM 任务栏全接好), 与 LMA 注册顺序无关 —
 * 早于 LMA 走扫描路径, 晚于 LMA 走迟注册钩子补注册, 任务栏可见性无顺序依赖。
 *
 * <pre>
 * import com.github.xiaozhaoz1.littlemaidmoreaction.api.LMAT;
 *
 * // 简单任务 (继承 LMAT.Task, 只覆写 tick 即可)
 * class MyTask extends LMAT.Task {
 *     MyTask() { super("my_task"); }
 *     {@literal @}Override public void tick(ServerLevel world, EntityMaid maid) {
 *         doWork(world, maid);
 *     }
 * }
 * LMAT.register("my_task", new MyTask());
 *
 * // 触发
 * LMAT.submit(maid, "my_task", "minecraft:diamond", 64);
 * </pre>
 *
 * <h3>注册预设 (按任务形态选一)</h3>
 * <ul>
 *   <li><b>简单任务</b> — 继承 {@link #Task}, 只覆写 tick: {@code LMAT.register("my_task", new MyTask());}</li>
 *   <li><b>FSM 任务</b> — 继承 {@link TaskStateMachine}: {@code LMAT.register(new MyFSM());}</li>
 *   <li><b>带图标</b> — {@code LMAT.registerTask("my_task", new MyTask(), "craft");} (图标关键词表解析)</li>
 *   <li><b>被动任务</b> — {@link #registerPassive} 注册, {@link #submitPassive}/{@link #cancelPassive} 触发 (不进任务栏)</li>
 *   <li><b>带配置 GUI</b> — implements TaskConfigurable, 覆写 getConfigGuiProvider (见 {@link #configGui})</li>
 * </ul>
 *
 * <h3>命名约定 (全局唯一命名空间)</h3>
 * <ul>
 *   <li>taskType 全局唯一 — 与 LMA 内置规格表 (TaskRegistryManifest) 或其他 Mod 重名, 注册时抛
 *       IllegalStateException (fail-fast, 启动即炸而非静默覆盖)</li>
 *   <li>只使用小写字母/数字/下划线, 建议 mod 前缀 (如 {@code "mymod_cook"}) — TLM uid 由 taskType
 *       净化生成 ({@code lma:task/&lt;type&gt;}): 大写被转小写、空格等被替换为下划线,
 *       "my task" 与 "my_task" 会撞同一个 uid (重名 fail-fast 不覆盖净化后同名)</li>
 *   <li>任务栏显示名走 lang key {@code task.littlemaidmoreaction.&lt;type&gt;} — 在自己的语言文件补 zh/en</li>
 * </ul>
 *
 * <h3>注册时机</h3>
 * 推荐在 TLM {@code addMaidTask} 阶段注册 (与 LMA 同事件, 任何顺序均可)。更晚注册任务仍可经
 * {@link #submit} 驱动, 但 TLM 任务栏已冻结 (TaskManager.init 末尾 ImmutableMap 快照),
 * 迟注册钩子会 fail-soft 并打 WARN 提示。
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

    // ── 注册 (工作单元 = pipeline.tick, 由 GMPM 每 tick 驱动) ──

    /** 注册任务 (可见性由任务树 TaskToggle 管理, 注册无 showInBar 参数) */
    public static void register(String taskType, TaskPipeline pipeline) {
        TaskRegistry.register(taskType, pipeline);
        com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry.onTaskRegistered(taskType);
    }

    /** 注册 FSM 任务 */
    public static <S extends Enum<S>> void register(TaskStateMachine<S> fsm) {
        TaskRegistry.register(fsm.taskType(), fsm);
        com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry.onTaskRegistered(fsm.taskType());
    }

    /** 注册被动任务 (不显示在任务栏, 由 EnvSense/AI/规则引擎内部触发) */
    public static void registerPassive(String taskType, TaskPipeline pipeline) {
        TaskRegistry.registerPassive(taskType, pipeline);
    }

    /**
     * 一键注册 (批次 C 聚合助手): 任务 + TLM 任务栏图标。
     * iconKeyword 经 LmaTaskTypeRegistry ICON_MAP 关键词表解析 (如 "craft"/"bell"/"arm"),
     * 未命中关键词 → 默认图标。
     *
     * <p>lang key 双文件手改: zh_cn.json/en_us.json 各加
     * {@code "task.littlemaidmoreaction.<type>"} — JSON 程序化写入不优雅 (资源构建期合并,
     * 运行期写源树是反模式), 由 LangConsistencyTest 对称守卫兜底。
     */
    public static void registerTask(String taskType, TaskPipeline pipeline, String iconKeyword) {
        TaskRegistry.register(taskType, pipeline);
        com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry.registerIcon(taskType, iconKeyword);
        // 迟注册钩子必须在 registerIcon 之后 — LmaTypedFlowTask 构造期读取 TASK_ICONS
        com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry.onTaskRegistered(taskType);
    }

    // ── 生命周期 ──

    /** 启动任务。返回 false 表示验证失败 (材料不足/目标无效) */
    public static boolean submit(EntityMaid maid, String taskType, String target, int count) {
        return TaskDispatcher.submit(maid, taskType, target, count);
    }

    /** 取消当前任务 (执行 interrupt → onCleanup → clearAll) */
    public static void cancel(EntityMaid maid) {
        TaskDispatcher.cancel(maid);
    }

    // ── 被动任务 (与主动任务隔离, 可并行运行) ──

    /** 触发被动任务 — 开关/哈气互斥检查在调度器内, 不满足时静默忽略 (重复触发幂等) */
    public static void submitPassive(EntityMaid maid, String taskType) {
        TaskDispatcher.submitPassive(maid, taskType);
    }

    /** 取消被动任务 (onCleanup → 清 passive key → 掩码缓存失效) */
    public static void cancelPassive(EntityMaid maid, String taskType) {
        TaskDispatcher.cancelPassive(maid, taskType);
    }

    // ── 查询 ──

    /** 当前任务类型，无任务时返回空字符串 {@code ""} */
    public static String currentTask(EntityMaid maid) {
        return FlowTaskData.getTask(maid);
    }

    /** 当前任务状态：in_progress / completed / failed / cancelled；无任务时返回空字符串 {@code ""} */
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

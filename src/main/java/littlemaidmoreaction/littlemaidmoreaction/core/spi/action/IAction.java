package littlemaidmoreaction.littlemaidmoreaction.core.spi.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * v5 动作 SPI 接口 — 替代 v4 {@code IAction(String category, Map<String, ParamDef> paramSchema)}。
 *
 * <p>采用 {@link List}&lt;{@link TypedParam}&lt;?&gt;&gt; 替代 Map 形式参数 schema，
 * 在编译期保证参数类型正确性。增加异步执行、超时控制、并发冲突标记等企业级特性。</p>
 *
 * <p>扩展新动作只需：
 * <ol>
 *   <li>实现此接口</li>
 *   <li>实现 id / displayName / category / params</li>
 *   <li>根据需求覆写 execute / executeAsync</li>
 *   <li>通过 {@code ActionRegistry.register(new MyAction())} 注册</li>
 * </ol>
 *
 * @see ActionCategory
 * @see littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry
 */
public interface IAction {

    /** 动作唯一 ID，如 "deal_damage"。对应 JSON "type" 字段。 */
    String id();

    /** GUI 显示名，如 "造成伤害"。 */
    String displayName();

    /** 分类枚举。编辑器可用此分组。 */
    ActionCategory category();

    /**
     * 类型安全参数定义列表。
     *
     * <p>替代 v4 {@code Map<String, ParamDef> paramSchema()}，
     * 在编译期保证参数类型且天然支持遍历。编辑器据此动态生成输入表单。
     * 无参数返回空 List。</p>
     *
     * @return TypedParam 列表（不可变）
     */
    List<TypedParam<?>> params();

    /**
     * 是否为异步动作。
     *
     * <p>异步动作（如 WAIT、REPEAT）需要 {@code TickScheduler} 挂起和恢复，
     * 不会在事件处理线程中同步完成。</p>
     *
     * @return true 表示此动作是异步的
     */
    default boolean isAsync() { return false; }

    /**
     * 执行后是否取消当前事件。
     *
     * <p>仅在流程控制类动作（如 cancel_event）返回 true。
     * 为 true 时事件总线将不再处理其他监听器。</p>
     *
     * @return true 表示需要取消事件
     */
    default boolean cancelsEvent() { return false; }

    /**
     * 超时控制（游戏刻）。
     *
     * <p>0 表示不超时。正值表示执行超过指定刻数后将触发超时中断，
     * 防止动作卡死游戏逻辑。仅对异步动作有效。</p>
     *
     * @return 超时刻数，0 表示不超时
     */
    default int timeoutTicks() { return 0; }

    /**
     * 与本动作互斥的动作 ID 列表。
     *
     * <p>用于声明两类动作不可同时执行。例如攻击动作和治愈动作可能互斥。
     * 框架在执行前检查冲突，若已调度了冲突动作则跳过。</p>
     *
     * @return 冲突动作 ID 列表（不可变）
     */
    default List<String> conflicts() { return List.of(); }

    /**
     * 是否修改游戏状态。
     *
     * <p>线程安全门控标记：
     * <ul>
     *   <li>{@code true}（默认）— 动作修改游戏状态（伤害、生成物品等），
     *       只能在 Minecraft 主线程执行或通过 {@code enqueueWork} 同步到主线程</li>
     *   <li>{@code false} — 动作不修改游戏状态（纯计算、日志等），
     *       可在任意线程安全执行</li>
     * </ul>
     * </p>
     *
     * @return true 表示修改游戏状态
     */
    default boolean isGameStateMutating() { return true; }

    /**
     * 同步执行入口（默认实现）。
     *
     * <p>默认委托给 {@link #executeAsync(RuleContext, Map)} 并阻塞等待完成。
     * 覆写此方法可提供纯同步实现。</p>
     *
     * @param ctx       规则上下文（maid 永非 null）
     * @param rawParams 已合并完成的参数（默认值←覆盖←$表达式求值）
     */
    default void execute(RuleContext ctx, Map<String, String> rawParams) {
        executeAsync(ctx, rawParams).join();
    }

    /**
     * 异步执行入口 — 支持 {@link CompletableFuture} 异步编排。
     *
     * <p>默认实现通过 {@link CompletableFuture#runAsync(Runnable)} 包装
     * {@link #execute(RuleContext, Map)}，在 ForkJoinPool 公共池中执行。
     * 覆写此方法可提供自定义异步逻辑（如使用 TickScheduler 挂起恢复）。</p>
     *
     * @param ctx       规则上下文（maid 永非 null）
     * @param rawParams 已合并完成的参数
     * @return CompletableFuture，完成时表示动作执行完毕
     */
    default CompletableFuture<Void> executeAsync(RuleContext ctx, Map<String, String> rawParams) {
        return CompletableFuture.runAsync(() -> execute(ctx, rawParams));
    }
}

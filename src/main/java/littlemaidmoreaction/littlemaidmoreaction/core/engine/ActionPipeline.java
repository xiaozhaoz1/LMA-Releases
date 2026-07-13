package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.debug.RuleTracer;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.engine.TickScheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 异步动作执行管道。
 *
 * <p>核心语义：
 * <ul>
 *   <li>相邻无冲突动作 → 并行提交</li>
 *   <li>游戏状态修改动作 → 当前线程同步执行（调用者已在服务端线程）</li>
 *   <li>纯视觉效果动作 → ForkJoinPool 异步</li>
 *   <li>WAIT/REPEAT → 挂起管道，TickScheduler 恢复</li>
 *   <li>超时动作 → orTimeout + 日志警告</li>
 * </ul>
 *
 * <p>此管道替换旧的 {@code ActionDispatcher.executeFrom()} 同步循环，
 * 是规则引擎的核心执行路径。
 *
 * @return true 表示序列中有 cancel_event 步骤（事件应被取消）
 */
public final class ActionPipeline {

    /**
     * 执行规则的全部动作序列。
     *
     * @param rule 当前规则定义
     * @param ctx  规则执行上下文
     * @return true 表示应取消触发事件
     */
    public static boolean execute(RuleDef rule, RuleContext ctx) {
        int maidId = ctx.maid().getId();
        List<ActionStep> steps = rule.actions();
        List<ParallelGroup> groups = GroupBuilder.build(steps);
        boolean shouldCancel = false;

        LittleMaidMoreAction.LOGGER.debug("[LMA/Pipeline] >>> START rule='{}' maid={} steps={} groups={}",
            rule.name(), maidId, steps.size(), groups.size());

        for (int i = 0; i < groups.size(); i++) {
            ParallelGroup group = groups.get(i);
            LittleMaidMoreAction.LOGGER.debug("[LMA/Pipeline] group[{}/{}] async={} cancel={} actions={}",
                i, groups.size(), group.isAsync(), group.isCancel(), group.actions().size());

            // 流程控制：挂起（WAIT / REPEAT）
            if (group.isAsync()) {
                ActionStep step = steps.get(group.resumeIndex());
                IAction action = ActionRegistry.get(step.typeId());
                if (action != null) {
                    Map<String, String> merged = ParamMerger.merge(action, step, ctx);
                    action.execute(ctx, merged);
                }
                LittleMaidMoreAction.LOGGER.debug("[LMA/Pipeline] <<< ASYNC maid={} type={} resumeIdx={}", maidId, step.typeId(), group.resumeIndex());
                // ★ 修复: resumeIdx+1 跳过 wait 步骤自身，防止恢复时重新遇到同一 wait 步骤导致无限循环
                TickScheduler.schedule(rule, ctx.maid(), ctx.target(),
                    group.resumeIndex() + 1, group.repeatIdx(), group.repeatCount());
                return shouldCancel;
            }

            // 流程控制：cancel_event
            if (group.isCancel()) {
                shouldCancel = true;
                LittleMaidMoreAction.LOGGER.debug("[LMA/Pipeline] CANCEL group[{}/{}]", i, groups.size());
                continue;
            }

            // 流程控制：random 概率门 — 未通过则跳过后续 N 组
            if (group.isRandom()) {
                double roll = ctx.maid().getRandom().nextDouble();
                if (roll > group.randomChance()) {
                    i += group.randomSkip();
                    LittleMaidMoreAction.LOGGER.debug(
                        "[LMA/Pipeline] 随机未通过 ({} > {}), 跳过 {} 组",
                        String.format("%.2f", roll),
                        String.format("%.2f", group.randomChance()),
                        group.randomSkip());
                }
                continue;
            }

            // 并行组：提交组内所有动作 + 追踪
            CompletableFuture<?>[] futures = group.actions().stream()
                .map(step -> {
                    long start = System.nanoTime();
                    CompletableFuture<Void> f = executeOne(step, ctx);
                    f.thenRun(() -> {
                        long elapsed = (System.nanoTime() - start) / 1_000_000;
                        RuleTracer.addAction(steps.indexOf(step), step.typeId(), true, null);
                    }).exceptionally(ex -> {
                        RuleTracer.addAction(steps.indexOf(step), step.typeId(), false, ex.getMessage());
                        return null;
                    });
                    return f;
                })
                .toArray(CompletableFuture[]::new);

            // 等待组内全部完成再进入下一组
            try {
                CompletableFuture.allOf(futures).join();
            } catch (Exception e) {
                LittleMaidMoreAction.LOGGER.error("[LMA/Pipeline] 并行组执行异常", e);
            }

            // ★ auto_wait: 若本组含 play_anim/play_weapon_anim + auto_wait，挂起管道
            if (!group.actions().isEmpty()) {
                ActionStep first = group.actions().get(0);
                if (("play_anim".equals(first.typeId()) || "play_weapon_anim".equals(first.typeId()))
                    && "true".equals(first.params().getOrDefault("auto_wait", "true"))) {
                    int resumeIdx = steps.indexOf(first) + 1;
                    if (resumeIdx < steps.size()) {
                        TickScheduler.schedule(rule, ctx.maid(), ctx.target(), resumeIdx);
                        LittleMaidMoreAction.LOGGER.info("[LMA/Pipeline] auto_wait suspend maid={} resumeIdx={}", maidId, resumeIdx);
                        return shouldCancel;
                    }
                }
            }
        }

        // 序列结束：重置旧系统动画状态（v7 由 Provider 管理）
        resetAnim(ctx);
        LittleMaidMoreAction.LOGGER.debug("[LMA/Pipeline] <<< DONE rule='{}' maid={} shouldCancel={}", rule.name(), maidId, shouldCancel);
        return shouldCancel;
    }

    /**
     * 执行单个动作步骤。
     *
     * <p>根据 IAction 的 isGameStateMutating() 决定执行路径：
     * <ul>
     *   <li>修改游戏状态 → 当前线程直接同步执行（调用者始终在服务端线程，
     *       避免通过 ServerTaskExecutor 排队后再 join() 造成死锁）</li>
     *   <li>纯视觉/计算 → 直接调用 {@link IAction#executeAsync(RuleContext, Map)} 异步执行</li>
     * </ul>
     *
     * @param step 当前动作步骤
     * @param ctx  规则执行上下文
     * @return CompletableFuture，完成时表示该动作执行完毕
     */
    private static CompletableFuture<Void> executeOne(ActionStep step, RuleContext ctx) {
        IAction action = ActionRegistry.get(step.typeId());
        if (action == null) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/Pipeline] 未知动作: {}", step.typeId());
            return CompletableFuture.completedFuture(null);
        }

        LittleMaidMoreAction.LOGGER.debug("[LMA/Pipeline] exec type={} params={}", step.typeId(), step.params());
        Map<String, String> mergedParams = ParamMerger.merge(action, step, ctx);

        CompletableFuture<Void> future;
        if (action.isGameStateMutating()) {
            // 游戏状态修改 → 直接在当前线程同步执行
            // 调用者（TlmEventAdapter @SubscribeEvent）始终在服务端线程，
            // 提交到 ServerTaskExecutor 后再 join() 会阻塞服务端线程等待
            // 下一个 ServerTickEvent.END 消费队列 → 造成死锁。
            // 旧 ActionDispatcher 直接调用 action.execute() 无此问题。
            try {
                action.execute(ctx, mergedParams);
                future = CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                future = CompletableFuture.failedFuture(e);
            }
        } else {
            // 纯视觉效果 → 异步执行
            future = action.executeAsync(ctx, mergedParams);
        }

        // 超时处理
        if (action.timeoutTicks() > 0) {
            future = future.orTimeout(
                action.timeoutTicks() * 50L, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    LittleMaidMoreAction.LOGGER.warn(
                        "[LMA/Pipeline] 动作超时: {} ({}ms)",
                        action.id(), action.timeoutTicks() * 50);
                    return null;
                });
        }

        return future;
    }

    /**
     * ★ v7: 从指定索引恢复动作序列执行。
     *
     * <p>由 TickScheduler 在 WAIT/REPEAT 到期时调用，替代旧 ActionDispatcher.continueSequence()。
     * 复用 GroupBuilder 重新分析剩余步骤，支持并行组、异步、ParamMerger 等全部 v5 特性。</p>
     *
     * @param rule        规则定义 (core.model)
     * @param ctx         规则上下文
     * @param resumeIdx   恢复起始步骤索引
     * @param repeatIdx   REPEAT 回跳位置 (-1 表示普通 WAIT)
     * @param repeatCount 剩余循环次数
     */
    public static void resumeFrom(RuleDef rule, RuleContext ctx, int resumeIdx, int repeatIdx, int repeatCount) {
        int maidId = ctx.maid().getId();
        List<ActionStep> steps = rule.actions();

        // REPEAT: 回跳到指定位置，减计数
        if (repeatIdx >= 0 && repeatCount > 0) {
            TickScheduler.schedule(rule, ctx.maid(), ctx.target(),
                repeatIdx, repeatIdx, repeatCount - 1);
            LittleMaidMoreAction.LOGGER.debug("[LMA/Pipeline] REPEAT resume maid={} from={} remaining={}", maidId, repeatIdx, repeatCount);
            resumeIdx = repeatIdx;
        }

        // 截取剩余步骤并执行
        if (resumeIdx >= steps.size()) {
            resetAnim(ctx);
            LittleMaidMoreAction.LOGGER.info("[LMA/Pipeline] RESUME abort maid={} resumeIdx={} >= steps.size={}",
                maidId, resumeIdx, steps.size());
            return;
        }
        List<ActionStep> remaining = steps.subList(resumeIdx, steps.size());
        List<ParallelGroup> groups = GroupBuilder.build(remaining);
        boolean shouldCancel = false;

        LittleMaidMoreAction.LOGGER.info("[LMA/Pipeline] >>> RESUME rule='{}' maid={} from={} remaining={} groups={}",
            rule.name(), maidId, resumeIdx, remaining.size(), groups.size());

        // ★ v8.3: 恢复时启动追踪
        RuleTracer.TraceRecord trace = RuleTracer.start(rule, "resume", ctx.maid());
        if (trace != null) trace.matched = true;

        for (int i = 0; i < groups.size(); i++) {
            ParallelGroup group = groups.get(i);
            if (group.isAsync()) {
                ActionStep step = remaining.get(group.resumeIndex());
                IAction action = ActionRegistry.get(step.typeId());
                if (action != null) {
                    Map<String, String> merged = ParamMerger.merge(action, step, ctx);
                    action.execute(ctx, merged);
                }
                TickScheduler.schedule(rule, ctx.maid(), ctx.target(),
                    resumeIdx + group.resumeIndex() + 1, group.repeatIdx(), group.repeatCount());
                RuleTracer.finish();
                return;
            }
            if (group.isCancel()) { shouldCancel = true; continue; }
            if (group.isRandom()) {
                double roll = ctx.maid().getRandom().nextDouble();
                if (roll > group.randomChance()) { i += group.randomSkip(); }
                continue;
            }
            CompletableFuture<?>[] futures = group.actions().stream()
                .map(step -> {
                    long start = System.nanoTime();
                    CompletableFuture<Void> f = executeOne(step, ctx);
                    f.thenRun(() -> {
                        RuleTracer.addAction(steps.indexOf(step), step.typeId(), true, null);
                    }).exceptionally(ex -> {
                        RuleTracer.addAction(steps.indexOf(step), step.typeId(), false, ex.getMessage());
                        return null;
                    });
                    return f;
                })
                .toArray(CompletableFuture[]::new);
            try { CompletableFuture.allOf(futures).join(); }
            catch (Exception e) { LittleMaidMoreAction.LOGGER.error("[LMA/Pipeline] RESUME 并行组异常", e); }

            // ★ auto_wait: 恢复管道也需检测 play_anim 自等待并挂起
            if (!group.actions().isEmpty()) {
                ActionStep first = group.actions().get(0);
                if (("play_anim".equals(first.typeId()) || "play_weapon_anim".equals(first.typeId()))
                    && "true".equals(first.params().getOrDefault("auto_wait", "true"))) {
                    int absIdx = resumeIdx + remaining.indexOf(first);
                    if (absIdx + 1 < steps.size()) {
                        TickScheduler.schedule(rule, ctx.maid(), ctx.target(), absIdx + 1);
                        LittleMaidMoreAction.LOGGER.info("[LMA/Pipeline] RESUME auto_wait suspend maid={} absIdx={}", maidId, absIdx);
                        RuleTracer.finish();
                        return;
                    }
                }
            }
        }
        resetAnim(ctx);
        RuleTracer.finish();
        LittleMaidMoreAction.LOGGER.info("[LMA/Pipeline] <<< RESUME DONE rule='{}' maid={}", rule.name(), maidId);
    }

    /**
     * 重置女仆状态（序列结束时调用）。
     *
     * <p>管道正常完成（所有 auto_wait 已结束）时，清理 v7 动画 key 解除 RuleEngine 护卫。
     * Provider 客户端已在首帧处理动画（seq 去重 → 第 2 帧 client-side cleanup），
     * 服务端保留 key 只会导致动画完成后引擎仍被阻塞，直到 timeout 到期。
     * 仅清理旧 animationId 系统残留 + v7 key + 恢复 AI 移动。
     */
    static void resetAnim(RuleContext ctx) {
        var data = ctx.maid().getPersistentData();
        String oldMode = data.getString("lma_anim_mode");
        // ── 旧 animationId 系统残留清理 ──
        data.remove("lma_anim_id");
        data.remove("lma_anim_time");
        // ★ 清理 v7 动画 key — 解除 RuleEngine 动画护卫
        data.remove("lma_anim");
        data.remove("lma_anim_mode");
        data.remove("lma_anim_phase");
        data.remove("lma_anim_start");
        data.remove("lma_anim_casting");
        data.remove("lma_anim_end");
        data.remove("lma_dur_start");
        data.remove("lma_dur_casting");
        data.remove("lma_dur_end");
        data.remove("lma_anim_priority");
        data.remove("lma_lock_move");
        data.remove("lma_anim_tick");
        data.remove("lma_anim_dur");
        // lmma_anim_seq 保留 — Provider 需要检测新请求
        // 恢复 AI 移动
        ctx.maid().getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        if (!oldMode.isEmpty()) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Pipeline] resetAnim maid={} oldMode={} cleared v7 keys", ctx.maid().getId(), oldMode);
        }
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private ActionPipeline() {}
}

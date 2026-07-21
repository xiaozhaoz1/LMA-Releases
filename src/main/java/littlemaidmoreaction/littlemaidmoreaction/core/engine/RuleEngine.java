package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.cache.ConditionCache;
import littlemaidmoreaction.littlemaidmoreaction.core.cache.MaidRuleIndex;
import littlemaidmoreaction.littlemaidmoreaction.core.cache.RuleIndex;
import littlemaidmoreaction.littlemaidmoreaction.core.debug.RuleTracer;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 规则引擎 v5 主入口。
 *
 * <p>事件处理流水线：
 * <ol>
 *   <li>RuleIndex.getByEvent(eventId) — O(1) 查找候选规则</li>
 *   <li>ConditionCache — 同事件内条件值缓存</li>
 *   <li>遍历候选规则（已按优先级排序）:</li>
 *   <ol>
 *     <li>enabled 检查</li>
 *     <li>chance 概率检查</li>
 *     <li>CooldownManager.isOnCooldown() 冷却检查</li>
 *     <li>ConditionMatcher.matches() 条件匹配</li>
 *   </ol>
 *   <li>命中 → CooldownManager.newCooldownStamp() + PersistentData → ActionPipeline.execute()</li>
 * </ol>
 */
public final class RuleEngine {

    /**
     * 处理事件 — 统一入口。
     *
     * @param eventId 事件 ID（如 "maid_attack"）
     * @param ctx     规则上下文
     * @return true 表示事件应被取消（规则中有 cancel_event 步骤）
     */
    public static boolean handleEvent(String eventId, RuleContext ctx) {
        // 快速路径：规则引擎未启用
        if (!MoreActionConfig.CUSTOM_RULES_ENABLED.get()) return false;

        // 快速路径：仅在服务端处理
        if (ctx.maid().level().isClientSide()) return false;

        int maidId = ctx.maid().getId();
        LittleMaidMoreAction.LOGGER.debug("[LMA/Engine] >>> ENTER event={} maid={}", eventId, maidId);

        // 快速路径：LMA 动画正在播放（不打断当前动画）
        String currentAnimMode = ctx.maid().getPersistentData().getString("lma_anim_mode");
        if (!currentAnimMode.isEmpty()) {
            long animTick = ctx.maid().getPersistentData().getLong("lma_anim_tick");
            long currentTick = ctx.maid().level().getGameTime();
            long elapsed = currentTick - animTick;
            // 超时阈值按动画实际时长
            long timeout = switch (currentAnimMode) {
                case "INSTANT", "YSM_ROULETTE" -> {
                    int dur = ctx.maid().getPersistentData().getInt("lma_anim_dur");
                    yield (dur > 0 ? dur : 40) + 5;  // PlayAnimAction 写入时已查好时长
                }
                case "FULL" -> {
                    var d = ctx.maid().getPersistentData();
                    yield d.getInt("lma_dur_start") + d.getInt("lma_dur_casting")
                        + d.getInt("lma_dur_end") + 33;
                }
                default -> 600;
            };
            if (timeout < 4) timeout = 4;
            if (elapsed > timeout || animTick > currentTick || animTick == 0) {
                cleanStaleAnimationData(ctx);
                LittleMaidMoreAction.LOGGER.info("[LMA/Engine] cleaned stale: maid={} mode={} elapsed={} timeout={}",
                    maidId, currentAnimMode, elapsed, timeout);
            } else {
                return false;
            }
        }

        // 1. O(1) 索引查找候选规则（全局）
        List<littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef> dataCandidates = RuleIndex.getByEvent(eventId);

        // ★ v8.7: 合并该女仆的独立规则
        UUID maidUuid = ctx.maid().getUUID();
        List<littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef> maidCandidates =
                MaidRuleIndex.getByEvent(maidUuid, eventId);
        if (!maidCandidates.isEmpty()) {
            List<littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef> combined =
                    new ArrayList<>(dataCandidates);
            combined.addAll(maidCandidates);
            combined.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
            dataCandidates = combined;
        }

        if (dataCandidates.isEmpty()) return false;

        LittleMaidMoreAction.LOGGER.debug("[LMA/Engine] event={} candidates={} (global={} maid={})",
                eventId, dataCandidates.size(),
                dataCandidates.size() - maidCandidates.size(), maidCandidates.size());

        // 2. 创建条件缓存（同一事件中复用）
        ConditionCache cache = new ConditionCache(ctx);

        try {
            // 3. 按优先级匹配（索引中已排序）
            for (RuleDef rule : dataCandidates) {
                // 启用检查
                if (!rule.enabled()) continue;

                // 冷却检查（先于概率 — 更轻量，更可能拦住）
                // ★ 调用方负责从 PersistentData 读取 lastUsed，core/ 零 MC 依赖
                long lastUsed = ctx.maid().getPersistentData().getLong(CooldownManager.COOLDOWN_KEY_PREFIX + rule.id());
                long gameTime = ctx.maid().level().getGameTime();
                if (CooldownManager.isOnCooldown(rule, lastUsed, gameTime)) continue;

                // 概率检查
                if (rule.chance() < 1.0
                    && ctx.maid().getRandom().nextDouble() > rule.chance()) continue;

                // 条件匹配
                if (!ConditionMatcher.matches(rule, cache)) continue;

                // 命中！应用冷却 + 执行动作序列
                LittleMaidMoreAction.LOGGER.info("[LMA/Engine] HIT rule='{}' event={} maid={} actions={}",
                    rule.name(), eventId, maidId, rule.actions().size());
                RuleTracer.TraceRecord trace = RuleTracer.start(rule, eventId, ctx.maid());
                trace.matched = true;
                ctx.maid().getPersistentData().putLong(
                    CooldownManager.COOLDOWN_KEY_PREFIX + rule.id(),
                    CooldownManager.newCooldownStamp(gameTime));
                boolean result = ActionPipeline.execute(rule, ctx);
                RuleTracer.finish();
                // ★ break 检查: 规则内 break 动作会设置 _break 属性，中断后续候选规则
                if (result || "true".equals(ctx.getAttribute("_break"))) {
                    LittleMaidMoreAction.LOGGER.debug("[LMA/Engine] <<< EXIT event={} maid={} result={} break=true", eventId, maidId, result);
                    return result;
                }
                LittleMaidMoreAction.LOGGER.debug("[LMA/Engine] <<< CONTINUE event={} maid={} after rule={}", eventId, maidId, rule.name());
            }
            return false;
        } finally {
            cache.clear();
        }
    }

    /** 清理残留/超时的动画 PersistentData，防止旧版数据永久阻塞引擎。 */
    private static void cleanStaleAnimationData(RuleContext ctx) {
        var data = ctx.maid().getPersistentData();
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
        // 保留 lmma_anim_seq — Provider 需要它检测新请求
    }

    private RuleEngine() {}
}

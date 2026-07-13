package littlemaidmoreaction.littlemaidmoreaction.engine;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.ActionPipeline;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tick 调度器 — 管理 WAIT/REPEAT 挂起序列的恢复。
 *
 * <p>v7: {@link ActionPipeline} 遇到 WAIT 或 REPEAT 时通过 {@link #schedule} 挂起上下文。
 * tick 轮询递减等待计数器，归零后通过 {@link ActionPipeline#resumeFrom} 恢复执行。
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class TickScheduler {

    /** 持久化数据中的等待 tick 键。 */
    public static final String WAIT_KEY = "lma_wait_ticks";

    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    /**
     * 挂起执行序列。
     *
     * @param rule        当前规则 (core.model.RuleDef)
     * @param maid        当前女仆
     * @param target      当前目标
     * @param resumeIdx   恢复时的步骤索引
     * @param repeatIdx   REPEAT 回跳位置（-1 表示普通 WAIT）
     * @param repeatCount 剩余循环次数
     */
    public static void schedule(RuleDef rule, EntityMaid maid, LivingEntity target,
                                 int resumeIdx, int repeatIdx, int repeatCount) {
        PENDING.put(maid.getId(),
                new Pending(rule, maid, target, resumeIdx, repeatIdx, repeatCount));
    }

    /** 挂起 WAIT 序列（无循环上下文）。 */
    public static void schedule(RuleDef rule, EntityMaid maid, LivingEntity target, int resumeIdx) {
        schedule(rule, maid, target, resumeIdx, -1, 0);
    }

    /**
     * 服务端 tick 轮询 — 递减等待计数，归零后通过 ActionPipeline.resumeFrom 恢复。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!MoreActionConfig.CUSTOM_RULES_ENABLED.get()) return;

        Iterator<Map.Entry<Integer, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> e = it.next();
            Pending c = e.getValue();
            if (c.maid == null || !c.maid.isAlive() || c.maid.level().isClientSide()) { it.remove(); continue; }

            var data = c.maid.getPersistentData();

            // v11: 条件等待模式 — 检查 wait_until 条件是否满足
            String waitUntilCond = data.getString("lma_wait_until_cond");
            if (!waitUntilCond.isEmpty()) {
                long start = data.getLong("lma_wait_until_start");
                int timeout = data.getInt("lma_wait_until_timeout");
                long elapsed = c.maid.level().getGameTime() - start;

                // 超时检测
                if (timeout > 0 && elapsed > timeout) {
                    data.remove("lma_wait_until_cond");
                    data.remove("lma_wait_until_val");
                    data.remove("lma_wait_until_timeout");
                    data.remove("lma_wait_until_start");
                    data.remove(WAIT_KEY);
                    it.remove();
                    // 超时也恢复管道（条件视为失败，跳过）
                    ActionPipeline.resumeFrom(
                        c.rule, new RuleContext(c.maid, c.target), c.resumeIdx, c.repeatIdx, c.repeatCount);
                    continue;
                }

                // 评估条件
                String expected = data.getString("lma_wait_until_val");
                if (evaluateCondition(waitUntilCond, expected, c.maid)) {
                    data.remove("lma_wait_until_cond");
                    data.remove("lma_wait_until_val");
                    data.remove("lma_wait_until_timeout");
                    data.remove("lma_wait_until_start");
                    data.remove(WAIT_KEY);
                    it.remove();
                    ActionPipeline.resumeFrom(
                        c.rule, new RuleContext(c.maid, c.target), c.resumeIdx, c.repeatIdx, c.repeatCount);
                }
                continue;
            }

            // 普通计数等待模式
            int rem = data.getInt(WAIT_KEY);
            if (rem > 0) { data.putInt(WAIT_KEY, rem - 1); continue; }
            data.remove(WAIT_KEY);
            it.remove();
            ActionPipeline.resumeFrom(
                    c.rule, new RuleContext(c.maid, c.target), c.resumeIdx, c.repeatIdx, c.repeatCount);
        }
    }

    /** 评估单个条件是否满足 */
    private static boolean evaluateCondition(String condKey, String expectedVal, EntityMaid maid) {
        try {
            var cond = ConditionRegistry.get(condKey);
            if (cond == null) return false;
            RuleContext ctx = new RuleContext(maid, null);
            String actual = cond.evaluate(ctx, java.util.Map.of());
            return actual.equals(expectedVal);
        } catch (Exception e) {
            return false;
        }
    }

    /** 取消指定女仆的所有挂起序列。 */
    public static void cancel(int maidId) { PENDING.remove(maidId); }

    // ---- 内部类型 ----

    /** 挂起的执行上下文 (v7: 使用 core.model.RuleDef) */
    static final class Pending {
        final RuleDef rule;
        final EntityMaid maid;
        final LivingEntity target;
        final int resumeIdx;
        final int repeatIdx;
        final int repeatCount;

        Pending(RuleDef rule, EntityMaid maid, LivingEntity target,
                int resumeIdx, int repeatIdx, int repeatCount) {
            this.rule = rule; this.maid = maid; this.target = target;
            this.resumeIdx = resumeIdx; this.repeatIdx = repeatIdx;
            this.repeatCount = repeatCount;
        }
    }
}

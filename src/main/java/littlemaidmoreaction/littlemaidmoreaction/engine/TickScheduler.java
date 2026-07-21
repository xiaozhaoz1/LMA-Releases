package littlemaidmoreaction.littlemaidmoreaction.engine;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.ITickScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tick 调度器 — 管理 WAIT/REPEAT 挂起序列的恢复。
 *
 * <p>v7: {@code ActionPipeline} 遇到 WAIT 或 REPEAT 时通过 {@link #schedule} 挂起上下文。
 * tick 轮询递减等待计数器，归零后通过回调恢复执行。
 *
 * <p>v41: 实现 {@link ITickScheduler} 接口，解除与 {@code ActionPipeline} 的循环依赖。
 * 存储 {@link Runnable} 回调替代直接调用 {@code ActionPipeline.resumeFrom}。
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class TickScheduler implements ITickScheduler {

    /** 持久化数据中的等待 tick 键。 */
    public static final String WAIT_KEY = "lma_wait_ticks";

    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    // ── ITickScheduler 实现 ──

    @Override
    public void schedule(int delayTicks, Runnable callback, int maidId) {
        PENDING.put(maidId, new Pending(callback, delayTicks));
    }

    @Override
    public void cancel(int maidId) {
        PENDING.remove(maidId);
    }

    // ── 事件处理 ──

    /**
     * 服务端 tick 轮询 — 递减等待计数，归零后执行回调。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!MoreActionConfig.CUSTOM_RULES_ENABLED.get()) return;

        MinecraftServer server = event.getServer();

        Iterator<Map.Entry<Integer, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> e = it.next();
            int maidId = e.getKey();
            Pending p = e.getValue();

            EntityMaid maid = resolveMaid(maidId, server);
            if (maid == null || !maid.isAlive() || maid.level().isClientSide()) { it.remove(); continue; }

            // 简单 tick 延迟模式（delayTicks > 0 时的 ITickScheduler 契约）
            if (p.remainingTicks > 0) {
                p.remainingTicks--;
                continue;
            }

            var data = maid.getPersistentData();

            // v11: 条件等待模式 — 检查 wait_until 条件是否满足
            String waitUntilCond = data.getString("lma_wait_until_cond");
            if (!waitUntilCond.isEmpty()) {
                long start = data.getLong("lma_wait_until_start");
                int timeout = data.getInt("lma_wait_until_timeout");
                long elapsed = maid.level().getGameTime() - start;

                // 超时检测
                if (timeout > 0 && elapsed > timeout) {
                    data.remove("lma_wait_until_cond");
                    data.remove("lma_wait_until_val");
                    data.remove("lma_wait_until_timeout");
                    data.remove("lma_wait_until_start");
                    data.remove(WAIT_KEY);
                    it.remove();
                    // 超时也恢复管道（条件视为失败，跳过）
                    p.callback.run();
                    continue;
                }

                // 评估条件
                String expected = data.getString("lma_wait_until_val");
                if (evaluateCondition(waitUntilCond, expected, maid)) {
                    data.remove("lma_wait_until_cond");
                    data.remove("lma_wait_until_val");
                    data.remove("lma_wait_until_timeout");
                    data.remove("lma_wait_until_start");
                    data.remove(WAIT_KEY);
                    it.remove();
                    p.callback.run();
                }
                continue;
            }

            // 普通计数等待模式
            int rem = data.getInt(WAIT_KEY);
            if (rem > 0) { data.putInt(WAIT_KEY, rem - 1); continue; }
            data.remove(WAIT_KEY);
            it.remove();
            p.callback.run();
        }
    }

    // ── 辅助方法 ──

    /**
     * 通过 maid ID 在所有服务器维度中查找实体实例。
     */
    private static EntityMaid resolveMaid(int maidId, MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(maidId);
            if (entity instanceof EntityMaid maid) return maid;
        }
        return null;
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

    // ---- 内部类型 ----

    /** 挂起的执行上下文 (v41: 存储 Runnable 回调 + tick 延迟)。 */
    static final class Pending {
        final Runnable callback;
        int remainingTicks;

        Pending(Runnable callback, int remainingTicks) {
            this.callback = callback;
            this.remainingTicks = remainingTicks;
        }
    }
}

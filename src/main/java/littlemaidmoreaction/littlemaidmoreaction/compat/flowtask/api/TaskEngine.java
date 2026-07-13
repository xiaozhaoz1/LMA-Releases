package littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.api;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.LmaFlowTask;
import littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.LmaTaskProgressDisplay;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 任务引擎 — 内置 tick 处理 (v11 重写)。
 *
 * <p>每隔 maid_tick 调用一次：
 * <ul>
 *   <li>任务开始时自动保存 home/pickup 状态 → 设置 home=当前位置 → 开启拾取</li>
 *   <li>检测状态变化 → 触发 task_changed</li>
 *   <li>completed/failed/stopped → 检查 max_count:
 *       count &lt; max_count 或 max_count=0 → 自动循环；
 *       count &gt;= max_count → 恢复 home/pickup → 清除任务</li>
 *   <li>检测超时 → 自动标记 failed</li>
 * </ul>
 */
public final class TaskEngine {

    static final int DEFAULT_TIMEOUT = 1200;
    private static final String CACHE_KEY = "lma_flow_cached";

    // 保存的原始状态 key
    private static final String SAVED_HOME = "lma_saved_home";
    private static final String SAVED_PICKUP = "lma_saved_pickup";
    private static final String SAVED_HOME_POS = "lma_saved_home_pos";

    private TaskEngine() {}

    public static void tick(RuleContext ctx) {
        // ★ Bug #68 fix: 移除 typed 任务绕过守卫。
        // 原守卫阻止 TaskEngine 对 typed 任务 (UID ≠ LmaFlowTask.UID) 的超时检测，
        // 导致 furnace 等 typed 任务跨session残留后永久循环无超时保护。
        // 现所有 LMA 任务统一走 TaskEngine 超时检测 + 生命周期管理。
        CompoundTag data = ctx.maid().getPersistentData();
        String task = data.getString("lma_flow_task");
        if (task.isEmpty()) return;

        String state = data.getString("lma_flow_state");
        int step = data.getInt("lma_flow_step");
        long lastTick = data.getLong("lma_flow_tick");
        long now = ctx.maid().level().getGameTime();

        // ── 0. 任务首次激活时保存状态 ──
        if ("in_progress".equals(state) && !data.contains(SAVED_HOME)) {
            var maid = ctx.maid();
            data.putBoolean(SAVED_HOME, maid.isHomeModeEnable());
            data.putString(SAVED_PICKUP, maid.getConfigManager().getPickupType().name());
            BlockPos pos = maid.blockPosition();
            data.putLong(SAVED_HOME_POS, ((long) pos.getX() << 32) | (pos.getZ() & 0xFFFFFFFFL));
            LittleMaidMoreAction.LOGGER.debug("[TaskEngine] saved state for '{}': home={} pickup={}",
                task, data.getBoolean(SAVED_HOME), data.getString(SAVED_PICKUP));
        }

        // ── 1. 状态变化检测 ──
        String currentSig = task + "|" + data.getString("lma_flow_task_id") + "|" + state + "|" + step;
        String cachedSig = data.getString(CACHE_KEY);
        if (!currentSig.equals(cachedSig)) {
            data.putString(CACHE_KEY, currentSig);
            // ★ v18.1: Brain直接执行，不需要 RuleEngine 匹配规则
            return;
        }

        // ── 2. 完成/失败/停止 → count 检查 ──
        if ("completed".equals(state) || "failed".equals(state) || "stopped".equals(state)) {
            int counter = data.getInt("lma_flow_counter");
            int maxCount = data.getInt("lma_flow_max_count");

            // ★ v12.6 fix: counter + 1 = 本次完成后已执行次数
            if (maxCount > 0 && counter + 1 >= maxCount) {
                // 达到最大次数 → 停止
                restoreState(ctx.maid(), data);
                // ★ v12.5: 记录完成通知 — AI 下次对话时读取
                data.putString("lma_task_completed", task);
                LmaTaskProgressDisplay.showComplete(ctx.maid(), task, counter, maxCount);
                data.remove("lma_flow_task");
                data.remove("lma_flow_task_id");
                data.remove("lma_flow_state");
                data.remove("lma_flow_step");
                data.remove("lma_flow_counter");
                data.remove("lma_flow_max_count");
                data.remove("lma_flow_tick");
                data.remove("lma_flow_timeout");
                data.remove("lma_flow_data");
                data.remove(CACHE_KEY);
                LittleMaidMoreAction.LOGGER.info("[TaskEngine] task '{}' stopped after {} runs", task, counter);
            } else {
                // 未达次数 → 循环 (递增计数器)
                counter++;
                data.putInt("lma_flow_counter", counter);
                data.putString("lma_flow_state", "in_progress");
                data.putInt("lma_flow_step", 0);
                data.putLong("lma_flow_tick", now);
                data.remove(CACHE_KEY);
                LittleMaidMoreAction.LOGGER.info("[TaskEngine] task '{}' auto-restart (run {}/{})",
                    task, counter, maxCount > 0 ? String.valueOf(maxCount) : "∞");
            }
            return;
        }

        // ── 3. 超时检测 ──
        if (!"in_progress".equals(state)) return;
        int timeout = data.getInt("lma_flow_timeout");
        if (timeout <= 0) timeout = DEFAULT_TIMEOUT;
        // ★ lastTick > now: 区分跨session残留(差值>1天=1728000tick) vs 时钟溢出
        if (lastTick == 0) return;
        if (lastTick > now) {
            long skew = lastTick - now;
            if (skew > 1_728_000L) {
                // 跨session残留 — 上次tick远大于当前时间，清理
                LittleMaidMoreAction.LOGGER.warn("[TaskEngine] task '{}' stale (lastTick={} >> now={}, skew={}), cleaning",
                    task, lastTick, now, skew);
                restoreState(ctx.maid(), data);
                data.remove("lma_flow_task"); data.remove("lma_flow_task_id");
                data.remove("lma_flow_state"); data.remove("lma_flow_step");
                data.remove("lma_flow_counter"); data.remove("lma_flow_max_count");
                data.remove("lma_flow_tick"); data.remove("lma_flow_timeout");
                data.remove("lma_flow_data"); data.remove(CACHE_KEY);
            }
            return;
        }
        if (now - lastTick > timeout) {
            data.putString("lma_flow_state", "failed");
            data.putLong("lma_flow_tick", now);
            data.putString(CACHE_KEY, task + "|" + data.getString("lma_flow_task_id") + "|failed|" + step);
            LittleMaidMoreAction.LOGGER.info("[TaskEngine] task '{}' timed out", task);
        }
    }

    // ── 状态恢复 ──

    private static void restoreState(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
                                      CompoundTag data) {
        if (data.contains(SAVED_HOME)) {
            boolean wasHome = data.getBoolean(SAVED_HOME);
            maid.setHomeModeEnable(wasHome);
            data.remove(SAVED_HOME);
        }
        if (data.contains(SAVED_PICKUP)) {
            String wasPickup = data.getString(SAVED_PICKUP);
            try {
                var type = com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.valueOf(wasPickup);
                maid.getConfigManager().setPickupType(type);
            } catch (IllegalArgumentException ignored) {}
            data.remove(SAVED_PICKUP);
        }
        data.remove(SAVED_HOME_POS);
        LittleMaidMoreAction.LOGGER.debug("[TaskEngine] restored state after task");

        // ★ v12.5: 恢复原始 TLM 任务 → brain 回到 IDLE/原有模式
        LmaFlowTask.restorePreviousTask(maid);
    }
}

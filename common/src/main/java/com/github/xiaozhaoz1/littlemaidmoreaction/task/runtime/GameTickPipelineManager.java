package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameTick 集中管理层 (v79) — 主动流程 + 被动流程的每 tick 驱动。
 *
 * <p>从 TaskTickHandler 内联逻辑提取: 心跳节流 (FLOW_TICK 每
 * {@link #HEARTBEAT_INTERVAL} tick 一写, 原每 tick) + 看门狗
 * ({@link WatchdogMath}, 容忍度 = 心跳间隔的精确数学)。
 *
 * <p>语义与原 TaskTickHandler 逐项等价 — 原 continue 语义映射为本方法 return。
 * 日志前缀沿用 [TaskTickHandler] 保持连续性。
 *
 * <p>v79.27: 被动检查 10t 节流 (位掩码缓存) — eligible 判定 (遍历 passives × PD 读取)
 * 每 {@link #PASSIVE_CHECK_INTERVAL} tick 重算, 中间 9t 位操作过滤; tick 本身仍每 tick
 * (Haqi TIMER / SnowShovel Cd 倒计时依赖每 tick 驱动)。
 */
public final class GameTickPipelineManager {

    /** v79: 心跳节流间隔 (1 秒) — FLOW_TICK 每 20t 一写 (原每 tick) */
    public static final int HEARTBEAT_INTERVAL = 20;
    /** v79: 看门狗容忍度 = 心跳间隔 — 有效超时区间 [timeout, timeout+20] */
    public static final int WATCHDOG_TOLERANCE = HEARTBEAT_INTERVAL;
    private static final int DEFAULT_TIMEOUT = 1200;

    /** v79.27: 被动检查节流 (tick) — eligible 判定 (遍历 passives × PD 读取) 每 10t 重算,
     *  中间 9t 用位掩码位操作过滤; tick 本身仍每 tick (Haqi TIMER / SnowShovel Cd 倒计时
     *  依赖每 tick 驱动, 节流 tick 会把计时拖慢 10 倍)。 */
    public static final int PASSIVE_CHECK_INTERVAL = 10;

    /** v79.27: 被动活跃位掩码缓存 (maidId → MaskEntry) — EntityCleanupListener 实体卸载时清理
     *  (同 ChainHarvestExecute 缓存管理: 防 maidId 泄漏 + 实体 ID 复用串扰) */
    private static final ConcurrentHashMap<Integer, MaskEntry> PASSIVE_ACTIVE_CACHE = new ConcurrentHashMap<>();
    private record MaskEntry(long mask, List<TaskRegistry.TaskHandler> source) {}

    private GameTickPipelineManager() {}

    /**
     * 主动流程每 tick — 旗标消费 (TLM_SWITCH/GUI_INIT) → 状态判定 → 看门狗 →
     * 心跳节流 + 管线 tick。原 TaskTickHandler 主循环体。
     */
    public static void tickActive(ServerLevel sl, EntityMaid maid, long now) {
        var data = maid.getPersistentData();
        String state = FlowTaskData.getState(maid);
        String task = FlowTaskData.getTask(maid);

        // ── v64: TLM_SWITCH/GUI_INIT 每 tick 处理 (从 TaskEngine 迁入) ──
        String tlmSwitch = data.getString(TaskKeys.TLM_SWITCH);
        if (!tlmSwitch.isEmpty()) {
            data.remove(TaskKeys.TLM_SWITCH);
            // 清除 GUI_INIT 防双 submit (v63.2)
            data.remove(TaskKeys.GUI_INIT);
            ResourceLocation uid = ResourceLocation.tryParse(tlmSwitch);
            if (uid != null && LittleMaidMoreAction.MOD_ID.equals(uid.getNamespace())) {
                String newType = LmaTaskTypeRegistry.extractTaskType(uid.getPath());
                // v64: 如果FLOW_TASK已匹配且运行中, 跳过 (AI已通过StartTaskTool提交)
                if (newType != null && newType.equals(task) && TaskKeys.STATE_IN_PROGRESS.equals(state)
                        && TaskRegistry.get(newType) != null) {
                    LittleMaidMoreAction.LOGGER.debug("[TaskTickHandler] TLM_SWITCH '{}' matches current flow task, skip", newType);
                    return;
                }
                if (newType != null && TaskRegistry.get(newType) != null) {
                    // v64: 保存target再cancel (clearAll会清除TASK_TARGET)
                    String savedTarget = data.getString(TaskKeys.TASK_TARGET);
                    TaskDispatcher.cancel(maid);
                    TaskDispatcher.submit(maid, newType, savedTarget, 0);
                    return;
                }
            }
            TaskDispatcher.cancel(maid);
            return;
        }

        String guiInit = data.getString(TaskKeys.GUI_INIT);
        if (!guiInit.isEmpty()) {
            data.remove(TaskKeys.GUI_INIT);
            TaskDispatcher.submit(maid, guiInit, null, 0);
            return;
        }

        // ── 非活跃状态 或 CANCELLED ──
        if (!TaskKeys.STATE_IN_PROGRESS.equals(state)) {
            if (TaskKeys.STATE_CANCELLED.equals(state)) cleanupMaid(maid);
            return;
        }
        if (task.isEmpty()) return;

        // ── 超时看门狗 (v64: 从 TaskEngine 迁入, 仅 isLongRunning; v79: WatchdogMath + 容忍度) ──
        var h = TaskRegistry.get(task);
        if (h != null && h.pipeline().isLongRunning()) {
            long lastTick = FlowTaskData.getTick(maid);
            if (lastTick != 0) {
                int timeout = data.getInt(TaskKeys.FLOW_TIMEOUT);
                if (timeout <= 0) timeout = DEFAULT_TIMEOUT;
                if (lastTick > now) {
                    // 防溢出: 时钟回绕/跨 session 残留 — 原样保留; 原语义: 本 tick 跳过心跳+tick
                    if (WatchdogMath.isStale(now, lastTick)) {
                        LittleMaidMoreAction.LOGGER.warn("[TaskTickHandler] task '{}' stale (skew={}), cleaning via Dispatcher", task, lastTick - now);
                        TaskDispatcher.timeout(maid);
                    }
                    return;
                }
                if (WatchdogMath.isTimedOut(now, lastTick, timeout, WATCHDOG_TOLERANCE)) {
                    LittleMaidMoreAction.LOGGER.info("[TaskTickHandler] task '{}' timed out ({}t > {}t)", task, now - lastTick, timeout);
                    TaskDispatcher.timeout(maid);
                    // v79.20.5: timeout → clearAll 已删 lma_flow_task — 必须 return!
                    // 原继续掉到下方 tick → ChainHarvestExecute.allowed 重读 getTask → ""
                    // → TaskRegistry.get("") → null → NPE (错题 #124)。stale 分支 L98 有 return,
                    // 此分支漏写。触发: 任务 in_progress 但 60s 无 tick (收石板/卸载/暂停) → 重载首 tick 即崩
                    return;
                }
            }
        }

        // ── GameTick 管线驱动 (v79: 心跳节流 — FLOW_TICK 每 HEARTBEAT_INTERVAL tick 一写) ──
        // v79.20.5: 防御 — 重读 task 与局部变量比对: 看门狗超时等终结路径已 clearAll
        // (错题 #124 NPE), 任何"终结后继续"路径在此被挡 — 本 tick 不再 tick 旧管线
        if (h != null && h.pipeline().needsGameTick() && task.equals(FlowTaskData.getTask(maid))) {
            if (h.pipeline().isLongRunning() && now % HEARTBEAT_INTERVAL == 0) {
                TaskStateManager.heartbeat(maid, now);
            }
            h.pipeline().tick(sl, maid);
        }
    }

    /**
     * 被动流程每 tick (原 tickPassive 单女仆体) — passives 每 level hoist 一次。
     * v79: 预算轮转 (PassiveTaskConfig.PASSIVE_TICK_BUDGET, 0=不限) — 每女仆每 tick
     * 最多执行 budget 个被动管线; 超预算时环形轮转 (确定性, 零 per-maid 状态)。
     * v79.27: eligible 判定 (遍历 passives × PD 读取) 10t 节流 — 位掩码缓存,
     * 中间 9t 位操作过滤; tick 本身仍每 tick (计时管线不受影响)。
     */
    public static void tickPassiveFor(ServerLevel sl, EntityMaid maid,
                                      List<TaskRegistry.TaskHandler> passives, long now) {
        int budget = com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig.PASSIVE_TICK_BUDGET.get();
        if (budget <= 0 || passives.size() <= budget) {
            // 不限/未超预算 → 全量 tick (现状语义)
            tickAll(sl, maid, passives);
            return;
        }
        // 预算轮转: 收集 eligible (IN_PROGRESS + toggle), 环形取 budget 个
        long mask = passiveMask(maid, passives, now);
        if (mask == 0) return;
        int active = Long.bitCount(mask);
        if (active <= budget) {
            for (int i = 0; i < passives.size(); i++) {
                if ((mask & (1L << i)) != 0) {
                    passives.get(i).pipeline().tick(sl, maid);
                }
            }
            return;
        }
        // 超预算 → 环形轮转: 收集活跃索引, 从 PassiveRotation.startIndex 起取 budget 个
        int[] idx = new int[active];
        int k = 0;
        for (int i = 0; i < passives.size(); i++) {
            if ((mask & (1L << i)) != 0) idx[k++] = i;
        }
        int start = PassiveRotation.startIndex(now, maid.getId(), active);
        for (int i = 0; i < budget; i++) {
            passives.get(idx[(start + i) % active]).pipeline().tick(sl, maid);
        }
    }

    /**
     * v79.27: eligible 位掩码 — 每 {@link #PASSIVE_CHECK_INTERVAL} tick 重算 (遍历判定),
     * 中间 9t 用缓存掩码 (位操作); passives 列表重建 (引用变化) 立即重算。
     */
    private static long passiveMask(EntityMaid maid, List<TaskRegistry.TaskHandler> passives, long now) {
        int id = maid.getId();
        MaskEntry cached = PASSIVE_ACTIVE_CACHE.get(id);
        if (cached == null || cached.source() != passives || now % PASSIVE_CHECK_INTERVAL == 0) {
            long mask = 0;
            for (int i = 0; i < passives.size(); i++) {
                TaskRegistry.TaskHandler h = passives.get(i);
                String key = TaskKeys.passiveKey(h.taskType());
                if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(key))
                        && TaskToggle.isEnabledFor(maid, h.taskType())) {
                    mask |= 1L << i;
                }
            }
            PASSIVE_ACTIVE_CACHE.put(id, new MaskEntry(mask, passives));
            return mask;
        }
        return cached.mask();
    }

    /** v79.27: 实体卸载清理 (EntityCleanupListener 调用) — 被动位掩码缓存, 防 maidId 泄漏/串扰 */
    public static void clearMaidCaches(EntityMaid maid) {
        PASSIVE_ACTIVE_CACHE.remove(maid.getId());
    }

    private static void tickAll(ServerLevel sl, EntityMaid maid,
                                List<TaskRegistry.TaskHandler> passives) {
        for (TaskRegistry.TaskHandler h : passives) {
            String key = TaskKeys.passiveKey(h.taskType());
            if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(key))
                    && TaskToggle.isEnabledFor(maid, h.taskType())) {
                h.pipeline().tick(sl, maid);
            }
        }
    }

    /** v75.4: 先走 pipeline.onCleanup 闭合游标, 再 clearAll (原直调 clearAll 绕过 onCleanup) */
    private static void cleanupMaid(EntityMaid maid) {
        String task = FlowTaskData.getTask(maid);
        if (task.isEmpty()) return;
        var handler = TaskRegistry.get(task);
        if (handler != null) {
            handler.pipeline().onCleanup(maid);
        }
        TaskStateManager.clearAll(maid);
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskTypeUid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

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
 * (Haqi TIMER 倒计时依赖每 tick 驱动)。
 *
 * <p>v79.61x 被动脱管线 (用户批准): 被动不再共用单一 tick 通道 —
 * <ul>
 *   <li>GMPM 真管线 ({@link #GMPM_PIPELINES}: haqi FSM+底层覆盖 / self_rescue 时间关键) 仍由
 *       {@link #tickPassiveFor} 驱动 (haqi 运行中 self_rescue 停 tick — 底层覆盖)</li>
 *   <li>跨 tick 动作型 ({@link #STANDALONE}: temp_adapt/torch_light) 由
 *       {@link #tickStandalonePassives} 独立心跳驱动 — 每 tick 调用, 动作频率由管线内
 *       ThrottleUtil 节流自持 (100/200/300t), 不再共享 budget 轮转/位掩码缓存</li>
 *   <li>纯触发型 (structure_sense/festival) 无 tick — 经 PassiveDispatcher</li>
 * </ul>
 * 删除: PASSIVE_TICK_BUDGET 配置 / PassiveRotation 轮转 / 位掩码缓存 (PASSIVE_ACTIVE_CACHE) /
 * clearMaidCaches (mask 缓存消失, 冷却表走 MaidUnloadRegistry 声明式清理)。
 */
public final class GameTickPipelineManager {

    /** 心跳节流间隔 (1 秒) — FLOW_TICK 每 20t 一写 (原每 tick) */
    public static final int HEARTBEAT_INTERVAL = 20;
    /** 看门狗容忍度 = 心跳间隔 — 有效超时区间 [timeout, timeout+20] */
    public static final int WATCHDOG_TOLERANCE = HEARTBEAT_INTERVAL;
    /** 超时默认值 — v79.55: FLOW_TIMEOUT 键无写方已删 (错题 #181), 恒用默认 1200t */
    private static final int DEFAULT_TIMEOUT = 1200;

    /** 被动检查节流 (tick) — 运行中关开关清理遍历每 10t 一次 (摊薄 PD 读取) */
    public static final int PASSIVE_CHECK_INTERVAL = 10;

    /** GMPM 真管线 (保留本通道) — haqi (FSM + 底层覆盖) / self_rescue (时间关键并行) */
    private static final java.util.Set<String> GMPM_PIPELINES = java.util.Set.of("haqi", "self_rescue");

    /** 跨 tick 动作型 (v79.61x 脱管线 — 独立心跳, 管线内节流自持) */
    private static final java.util.Set<String> STANDALONE = java.util.Set.of(
            "temp_adapt", "torch_light");

    private GameTickPipelineManager() {}

    /**
     * 主动流程每 tick — 旗标消费 (TLM_SWITCH/GUI_INIT) → 状态判定 → 看门狗 →
     * 心跳节流 + 管线 tick。原 TaskTickHandler 主循环体。
     */
    public static void tickActive(ServerLevel sl, EntityMaid maid, long now) {
        var data = maid.getPersistentData();
        String state = FlowTaskData.getState(maid);
        String task = FlowTaskData.getTask(maid);

        // ── TLM_SWITCH/GUI_INIT 每 tick 处理 (从 TaskEngine 迁入) ──
        String tlmSwitch = TaskMetaData.getTlmSwitch(maid);
        if (!tlmSwitch.isEmpty()) {
            TaskMetaData.clearTlmSwitch(maid);
            // 清除 GUI_INIT 防双 submit
            TaskMetaData.clearGuiInit(maid);
            ResourceLocation uid = ResourceLocation.tryParse(tlmSwitch);
            if (uid == null) {
                // 哨兵 (错题 #179 契约): 值应为完整 RL (如 "lma:task/craft_chain"); 裸 taskType 解析
                // 失败 → 下方 cancel 兜底。新写方再写裸格式立即在此暴露
                LittleMaidMoreAction.LOGGER.warn("[LMA/TaskTickHandler] TLM_SWITCH 非完整 RL 格式: '{}' (预期 lma:task/<type>)", tlmSwitch);
            } else if (LittleMaidMoreAction.MOD_ID.equals(uid.getNamespace())) {
                String newType = TaskTypeUid.extractTaskType(uid.getPath());
                // 如果FLOW_TASK已匹配且运行中, 跳过 (AI已通过StartTaskTool提交)
                if (newType != null && newType.equals(task) && TaskKeys.STATE_IN_PROGRESS.equals(state)
                        && TaskRegistry.get(newType) != null) {
                    LittleMaidMoreAction.LOGGER.debug("[TaskTickHandler] TLM_SWITCH '{}' matches current flow task, skip", newType);
                    return;
                }
                if (newType != null && TaskRegistry.get(newType) != null) {
                    // 保存target再cancel (clearAll会清除TASK_TARGET)
                    String savedTarget = data.getString(TaskKeys.TASK_TARGET);
                    TaskDispatcher.cancel(maid);
                    TaskDispatcher.submit(maid, newType, savedTarget, 0);
                    return;
                }
            }
            TaskDispatcher.cancel(maid);
            return;
        }

        String guiInit = TaskMetaData.getGuiInit(maid);
        if (!guiInit.isEmpty()) {
            TaskMetaData.clearGuiInit(maid);
            TaskDispatcher.submit(maid, guiInit, null, 0);
            return;
        }

        // ── 非活跃状态 ──
        // (CANCELLED 分支已删 — 2026-08-16 实证: TaskDispatcher.cancel 同帧 clearAll,
        // FLOW_STATE 在 CLEAR_ALL_KEYS 内, 状态零残留不跨 tick, 原 cleanupMaid 兜底不可达)
        if (!TaskKeys.STATE_IN_PROGRESS.equals(state)) {
            return;
        }
        if (task.isEmpty()) return;

        // ── 超时看门狗 (从 TaskEngine 迁入, 仅 isLongRunning; WatchdogMath + 容忍度) ──
        // 2026-08-11c 文档化: 看门狗+心跳均仅 isLongRunning 生效 — 非长任务 = 自终结语义
        // (设计约定: 任务自行 complete; 忘终结 = 永久运行无兜底 — GMPM 不干预)
        var h = TaskRegistry.get(task);
        if (h != null && h.pipeline().isLongRunning()) {
            long lastTick = FlowTaskData.getTick(maid);
            if (lastTick != 0) {
                // v79.55: FLOW_TIMEOUT 键无写方恒默认 (错题 #181) — 删键后恒 DEFAULT_TIMEOUT
                if (lastTick > now) {
                    // 防溢出: 时钟回绕/跨 session 残留 — 原样保留; 原语义: 本 tick 跳过心跳+tick
                    if (WatchdogMath.isStale(now, lastTick)) {
                        LittleMaidMoreAction.LOGGER.warn("[TaskTickHandler] task '{}' stale (skew={}), cleaning via Dispatcher", task, lastTick - now);
                        TaskDispatcher.timeout(maid);
                    }
                    return;
                }
                if (WatchdogMath.isTimedOut(now, lastTick, DEFAULT_TIMEOUT, WATCHDOG_TOLERANCE)) {
                    LittleMaidMoreAction.LOGGER.info("[TaskTickHandler] task '{}' timed out ({}t > {}t)", task, now - lastTick, DEFAULT_TIMEOUT);
                    TaskDispatcher.timeout(maid);
                    // timeout → clearAll 已删 lma_flow_task — 必须 return!
                    // 原继续掉到下方 tick → ChainHarvestExecute.allowed 重读 getTask → ""
                    // → TaskRegistry.get("") → null → NPE (错题 #124)。stale 分支 L98 有 return,
                    // 此分支漏写。触发: 任务 in_progress 但 60s 无 tick (收石板/卸载/暂停) → 重载首 tick 即崩
                    return;
                }
            }
        }

        // ── GameTick 管线驱动 (心跳节流 — FLOW_TICK 每 HEARTBEAT_INTERVAL tick 一写) ──
        // 防御 — 重读 task 与局部变量比对: 看门狗超时等终结路径已 clearAll
        // (错题 #124 NPE), 任何"终结后继续"路径在此被挡 — 本 tick 不再 tick 旧管线
        // v79.46b: needsGameTick 字段删 — 驱动所有 in_progress 主动管线 (被动由 tickPassiveFor 驱动, FLOW_STATE 检查在此之上挡住)
        if (h != null && task.equals(FlowTaskData.getTask(maid))) {
            if (h.pipeline().isLongRunning() && now % HEARTBEAT_INTERVAL == 0) {
                TaskStateManager.heartbeat(maid, now);
                // PL 内存态落盘 (心跳 20t — NBT 最多旧 20t)
                // flushAllPl 覆盖 FSM 状态键 (lma_pl_<type>.fsm) — 单键 flush 漏 FSM
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.flushAllPl(maid);
            }
            // v79.58 裁定修订: 自救不暂停主动任务 — 并行执行 (被动标准语义),
            // 被埋瞬破与主动任务互不干预; 原暂停守卫 (方案 A) 已删
            h.pipeline().tick(sl, maid);
        }
    }

    /**
     * 被动流程每 tick (v79.61x 脱管线版) — 只驱动 GMPM 真管线
     * ({@link #GMPM_PIPELINES}: haqi/self_rescue); 跨 tick 型走
     * {@link #tickStandalonePassives} 独立心跳; 纯触发型 (pipeline null) 占位跳过。
     *
     * <p>保留: 运行中关开关 → cancelPassive 清理 (10t 节流遍历, 防旧状态复活 — v79.61x #6)。
     * haqi 底层覆盖 (用户裁定): haqi 运行中 self_rescue 停 tick (恢复由 haqi 结束下轮自然继续)。
     * 删除: PASSIVE_TICK_BUDGET 轮转 / 位掩码缓存 — 每 tick 全量判定 (遍历 7 条目 × PD 读,
     * 与 passiveMask 重算同量级)。
     */
    public static void tickPassiveFor(ServerLevel sl, EntityMaid maid,
                                      List<TaskRegistry.TaskHandler> passives, long now) {
        // 运行中关开关 → cancelPassive 清理 (火把回背包/pipelineData 清/哈气 pin 恢复
        // — 原禁用只停 tick, in_progress 键+残留状态挂着, 重开开关后旧状态复活)。
        if (now % PASSIVE_CHECK_INTERVAL == 0) {
            for (TaskRegistry.TaskHandler h : passives) {
                String key = TaskKeys.passiveKey(h.taskType());
                if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(key))
                        && !TaskToggle.isEnabled(h.taskType())) {
                    TaskDispatcher.cancelPassive(maid, h.taskType());
                }
            }
        }
        for (TaskRegistry.TaskHandler h : passives) {
            if (h.pipeline() == null) continue;                    // 纯触发型占位 — Dispatcher 通道
            if (!GMPM_PIPELINES.contains(h.taskType())) continue;  // 跨 tick 型 — 独立心跳
            if (!running(maid, h)) continue;
            // haqi 底层覆盖 — haqi 运行中 self_rescue 停 tick (用户裁定: 哈气启动不结束不做其他事)
            if (!"haqi".equals(h.taskType())
                    && com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveDispatcher.haqiRunning(maid)) {
                continue;
            }
            h.pipeline().tick(sl, maid);
        }
    }

    /**
     * 跨 tick 动作型独立心跳 (v79.61x) — temp_adapt/torch_light 移出共享通道
     * (v79.62: snow_shovel 已删 — TLM 原版清雪任务覆盖, 用户裁定)。
     * 每 tick 调用 (动作频率由管线内 ThrottleUtil 节流自持 — temp 100/600t,
     * torch 20/300t, 行为等价); haqi 底层覆盖统一检查 (PassiveDispatcher 同源判定)。
     */
    public static void tickStandalonePassives(ServerLevel sl, EntityMaid maid,
                                              List<TaskRegistry.TaskHandler> passives) {
        for (TaskRegistry.TaskHandler h : passives) {
            if (h.pipeline() == null) continue;
            if (!STANDALONE.contains(h.taskType())) continue;
            if (!running(maid, h)) continue;
            // haqi 底层覆盖 — 哈气运行中跨 tick 型停 tick (统一入口)
            if (com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveDispatcher.haqiRunning(maid)) {
                continue;
            }
            // 2026-08-16 用户实测「坐下还往水边走」: 独立心跳被动 (温度/火把) 自己 moveTo,
            // 不走 Brain 导航行为 — 坐下判断必须在心跳入口统一拦 (Brain 侧拦不到)
            if (maid.isMaidInSittingPose()) {
                continue;
            }
            h.pipeline().tick(sl, maid);
        }
    }

    /** in_progress + 开关 (两通道共用判定) */
    private static boolean running(EntityMaid maid, TaskRegistry.TaskHandler h) {
        String key = TaskKeys.passiveKey(h.taskType());
        return TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(key))
                && TaskToggle.isEnabled(h.taskType());
    }

    /** 先走 pipeline.onCleanup 闭合游标, 再 clearAll (原直调 clearAll 绕过 onCleanup) */
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

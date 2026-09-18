package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.DataKey;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import net.minecraft.server.level.ServerLevel;

/**
 * 任务中央调度器 (v43) — 替代分散的任务启动/停止入口。
 *
 * <p>所有任务生命周期操作 (提交/取消/完成/失败) 必须通过本类。
 * 禁止直接操作 PersistentData 中的 lma_flow_* 字段。
 *
 * <p>v79.21: 失败/超时气泡统一走 {@link MaidChatBubbleApi} (节流内置于 API, 600t) —
 * 删除本地 FAIL_BUBBLE 节流表; 顺带补上超时气泡缺失的节流 (错题: 同类刷屏 bug)。
 */
public final class TaskDispatcher {

    private TaskDispatcher() {}

    /**
     * 提交任务 — 冲突检测 → 验证 → 写入状态。
     *
     * @param maid     女仆实体
     * @param taskType 任务类型 (如 "craft_chain", "furnace")
     * @param target   目标物品/方块 (可为null或空)
     * @param count    目标数量 (0=默认)
     * @return true 表示任务已启动
     */
    /**
     * 提交任务 — 验证 → 冲突检测 → 写入状态 (先验证再取消, 防止验证失败导致旧任务丢失)
     */
    public static boolean submit(EntityMaid maid, String taskType, String target, int count) {
        if (!(maid.level() instanceof ServerLevel)) return false;

        // 1. 先验证 (失败则旧任务不受影响)
        PipelineResult result = TaskRegistry.validate(maid, taskType,
            "", target != null ? target : "", count);
        if (!result.completed()) {
            // 失败气泡 (红色 ✘, API 内置 30 秒节流防无限重试刷屏)
            if (!result.feedback().isEmpty()) {
                MaidChatBubbleApi.showFail(maid, result.feedback());
            }
            return false;
        }

        // 哈气底层覆盖 — 哈气运行中拒绝主动任务提交 (v79.61x 收敛: PassiveDispatcher.haqiRunning 统一判定)
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveDispatcher.haqiRunning(maid)) {
            return false;
        }

        // 2. 冲突检测: 验证通过后再取消旧任务
        // v79.62.1 幂等 (用户裁定: 右键提示两遍 — onEntityJoin 恢复 + handler 重复 submit):
        // 同任务已在运行 (STATE_IN_PROGRESS) → 不重复提交 (防重复启动/重复提示)
        String current = FlowTaskData.getTask(maid);
        if (current.equals(taskType)
                && TaskKeys.STATE_IN_PROGRESS.equals(FlowTaskData.getState(maid))) {
            return true;   // 已在运行 → 幂等跳过 (不重复 submit/提示)
        }
        // 优先级策略 — 新任务严格更低 → 拒绝 (失败气泡节流); 等/高 → 抢占 (既有行为)
        if (!current.isEmpty() && !current.equals(taskType)) {
            if (!shouldPreempt(priorityOf(current), priorityOf(taskType))) {
                MaidChatBubbleApi.showFail(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.dispatch.busy", current));
                return false;
            }
            cancel(maid);
        }

        // 3. 统一写入
        long now = maid.level().getGameTime();
        TaskStateManager.init(maid, taskType, now);
        // 存储 target 到 NBT
        if (target != null && !target.isEmpty()) {
            MaidData.put(maid, DataKey.TASK_TARGET, target);
        }
        // 存储数量 — 0=无限, >0=指定数量
        if (count > 0) {
            FlowTaskData.setMaxCount(maid, count);
        }
        // 重试机制删除 (RetryPolicy) — 主动任务靠 TLM 任务栏自动重启, 被动靠信号重触发
        // 任务开始气泡 (信息型, 无节流 — 任务生命周期天然限频)
        LmaTaskProgressDisplay.showTaskStart(maid, taskType);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] submit maid={} task={} target={} count={}",
            maid.getStringUUID(), taskType, target, count);
        return true;
    }

    /** 取消任务 — 通知管线中断 (→onCleanup) + 设取消标记 + 清理 */
    public static void cancel(EntityMaid maid) {
        // 堆栈日志降级 debug — cancel 高频路径 (任务切换/取消), 生产每次抓 5 层
        // 堆栈 = 开销 + 日志噪音; 调试需求保留在 debug 层
        if (LittleMaidMoreAction.LOGGER.isDebugEnabled()) {
            LittleMaidMoreAction.LOGGER.debug("[LMA/Task] cancel CALLED from: {}",
                java.util.Arrays.stream(Thread.currentThread().getStackTrace()).skip(1).limit(5)
                    .map(StackTraceElement::toString).reduce((a,b) -> a + "\n  <- " + b).orElse("?"));
        }
        // interrupt→onCleanup
        String task = FlowTaskData.getTask(maid);
        if (!task.isEmpty()) {
            var h = TaskRegistry.get(task);
            if (h != null) {
                h.pipeline().interrupt(maid);
            }
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_CANCELLED);
        TaskStateManager.clearAll(maid); // cancel 后清除残留 NBT (同 complete/fail)
        // 2026-08-16 修: 任务切换 (装填→敲钟等) 旧 brain 导航目标残留 — 女仆仍盯着旧目标 (炮台) 不去新任务
        // 目标; cancel 即旧目标作废, 擦 TARGET_POS/WALK_TARGET/LOOK_TARGET (LOOK_TARGET 残留=女仆
        // 朝向旧目标站着看 — Brain behavior 只有 stop 才擦, 任务切换不触发 stop), 新任务重新搜索
        maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] cancel maid={} task={}",
            maid.getStringUUID(), task);
    }

    /** 超时 — 由 TaskTickHandler 超时看门狗调用。编排 onTimeout→interrupt(→onCleanup)→retry */
    public static void timeout(EntityMaid maid) {
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {
            // 统一失败气泡 (红色 ✘ + API 内置 600t 节流 — 补超时气泡缺失的节流, 错题: 同类刷屏 bug)
            MaidChatBubbleApi.showFail(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.dispatch.timeout", task));
            h.pipeline().interrupt(maid);  // interrupt→onCleanup
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_FAILED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] timeout maid={} task={}",
            maid.getStringUUID(), task);
        // 重试机制删除 — 主动任务 TLM 任务栏自动重启, 被动信号重触发
    }

    /** 标记任务完成 — onCleanup→STATE_COMPLETED→clearAll */
    public static void complete(EntityMaid maid) {
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {
            h.pipeline().onCleanup(maid);
        }
        // 完成气泡 (绿色 ✔) — clearAll 前读 counter/max (clearAll 清 FLOW_COUNTER/FLOW_MAX_COUNT)
        int counter = (int) (long) MaidData.get(maid, DataKey.FLOW_COUNTER);
        int maxCount = (int) (long) MaidData.get(maid, DataKey.FLOW_MAX_COUNT);
        FlowTaskData.setState(maid, TaskKeys.STATE_COMPLETED);
        TaskStateManager.clearAll(maid);
        if (!task.isEmpty()) {
            LmaTaskProgressDisplay.showComplete(maid, task, counter, maxCount);
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] complete maid={} task={}",
            maid.getStringUUID(), task);
    }

    /** 标记任务失败 — interrupt(→onCleanup)→STATE_FAILED→clearAll→retry? */
    public static void fail(EntityMaid maid, String reason) {
        // FAIL_REASON 死写已删 (v79.55, 错题 #181): put 后同方法 clearAll 立即删, 零读方 — reason 仅用于日志
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {            h.pipeline().interrupt(maid);  // interrupt→onCleanup
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_FAILED);
        TaskStateManager.clearAll(maid);
        // 2026-08-16: 同 cancel — 失败后旧导航目标作废, 擦 brain TARGET_POS/WALK_TARGET
        maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] fail maid={} task={} reason={}",
            maid.getStringUUID(), task, reason);
    }

    private static TaskRegistry.TaskHandler getHandler(String taskType) {
        return TaskRegistry.get(taskType);
    }

    /**
     * 优先级抢占策略 (纯函数, 可 JVM 测) — 等优先级 = 抢占.
     * 裁定依据: 树内 12 任务全默认 0; GUI_INIT/TLM_SWITCH/绑定提交均依赖"等优先级可抢占",
     * "保持"语义会破坏既有切换 — 仅严格更低拒绝.
     */
    static boolean shouldPreempt(int currentPriority, int newPriority) {
        return newPriority >= currentPriority;
    }

    private static int priorityOf(String taskType) {
        TaskRegistry.TaskHandler h = TaskRegistry.get(taskType);
        // 无 pipeline 占位条目 (纯触发型被动) — 优先级 0 (不进主动抢占面)
        return h == null || h.pipeline() == null ? 0 : h.pipeline().priority();
    }

    // ── 被动任务 — 与主动任务隔离, 可并行运行 ──

    /** 提交被动任务 (与 lma_flow_task 不冲突) — 哈气运行中拒绝其他被动 (底层覆盖, 统一入口) */
    public static void submitPassive(EntityMaid maid, String taskType) {
        if (TaskRegistry.get(taskType) == null) return;
        // v79.63 统一开关判定 (原硬编码 if "haqi"||"jiuhu_milk"): 提交门 / 运行判定 / 关开关清理
        // 三处共用 PassiveConfigUtil.isPassiveEnabled — 开关归属由任务自己声明
        // (TaskConfigurable.switchScope), 消灭"提交看 per-maid, 清理看全局"的双轨漂移
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveConfigUtil
                .isPassiveEnabled(maid, taskType)) {
            return;
        }
        // haqi 底层覆盖 — 哈气运行中其他被动不启动 (v79.61x 收敛: PassiveDispatcher.haqiRunning 统一判定)
        if (!"haqi".equals(taskType) && com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveDispatcher.haqiRunning(maid)) {
            return;
        }
        maid.getPersistentData().putString(TaskKeys.passiveKey(taskType), TaskKeys.STATE_IN_PROGRESS);
        // 2026-08-16 降噪: 被动提交/取消周期高频 (温度抖动成对刷屏) INFO→DEBUG
        LittleMaidMoreAction.LOGGER.debug("[LMA/Task] submitPassive maid={} task={}", maid.getStringUUID(), taskType);
    }

    /** 取消被动任务 */
    public static void cancelPassive(EntityMaid maid, String taskType) {
        var h = TaskRegistry.get(taskType);
        if (h != null && h.pipeline() != null) h.pipeline().onCleanup(maid);
        maid.getPersistentData().remove(TaskKeys.passiveKey(taskType));
        // (v79.61x: mask 缓存已删, 无需 clearMaidCaches; PassiveDispatcher 冷却表经
        // MaidUnloadRegistry 声明式清理)
        LittleMaidMoreAction.LOGGER.debug("[LMA/Task] cancelPassive maid={} task={}", maid.getStringUUID(), taskType);
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.MaidUnloadRegistry;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纯触发型被动任务调度器 (v79.61x 被动脱管线改造) — 信号/事件 → 原子动作的统一入口。
 *
 * <p>与 GMPM tick 通道完全平行: 不写 in_progress 键, 不经 submitPassive/cancelPassive,
 * 无状态机无持久化。统一四道闸 (顺序固定):
 * <ol>
 *   <li>注册表命中 (未注册静默跳过)</li>
 *   <li>{@link TaskToggle} 开关 (任务树/JSON 开关, 与管线被动同语义)</li>
 *   <li><b>haqi 底层覆盖</b> ({@link #haqiRunning} — 哈气运行中其他被动一律不执行;
 *       用户裁定: haqi 启动不结束就不去做其他事, 状态判定驱动, 无互斥锁)</li>
 *   <li>per-maid 冷却表 ({@link PassiveTask#cooldown()}; 卸载经 MaidUnloadRegistry 声明式清理)</li>
 * </ol>
 */
public final class PassiveDispatcher {

    private PassiveDispatcher() {}

    /** 纯触发型注册表 (taskType → 任务) */
    private static final Map<String, PassiveTask> TASKS = new LinkedHashMap<>();

    /** 冷却表 (maidId → taskType → 放行 tick) — 仅服务端写; 卸载清理闭环 */
    private static final Map<Integer, Map<String, Long>> COOLDOWNS = new HashMap<>();

    static {
        MaidUnloadRegistry.registerCache(COOLDOWNS, maid -> maid.getId());
    }

    /** 注册纯触发型被动 (防重 — 与 TaskRegistry 同款 fail-fast) */
    public static void register(PassiveTask task) {
        if (TASKS.containsKey(task.taskType())) {
            throw new IllegalStateException("[LMA] 纯触发被动重复注册: " + task.taskType());
        }
        TASKS.put(task.taskType(), task);
    }

    public static PassiveTask get(String taskType) {
        return TASKS.get(taskType);
    }

    public static Collection<PassiveTask> all() {
        return TASKS.values();
    }

    /**
     * haqi 底层覆盖判定 — 哈气 in_progress 即 true。
     * 统一入口: submitPassive (TaskDispatcher) + 独立心跳 + 本类 exec 全走这里。
     */
    public static boolean haqiRunning(EntityMaid maid) {
        return TaskKeys.STATE_IN_PROGRESS.equals(
                maid.getPersistentData().getString(TaskKeys.passiveKey("haqi")));
    }

    /** 执行入口 — 四道闸全过后 trigger (冷却写戳在放行时, 与节流先例一致) */
    public static void exec(EntityMaid maid, String taskType, String signalId) {
        PassiveTask task = TASKS.get(taskType);
        if (task == null) return;
        if (!TaskToggle.isEnabled(taskType)) return;
        if (haqiRunning(maid)) return;
        if (!(maid.level() instanceof ServerLevel level)) return;

        int cd = task.cooldown();
        if (cd > 0) {
            long now = level.getGameTime();
            Map<String, Long> per = COOLDOWNS.computeIfAbsent(maid.getId(), k -> new HashMap<>());
            Long last = per.get(taskType);
            if (last != null && now >= last && now - last < cd) return;
            per.put(taskType, now);
        }
        task.trigger(level, maid, signalId);
    }
}

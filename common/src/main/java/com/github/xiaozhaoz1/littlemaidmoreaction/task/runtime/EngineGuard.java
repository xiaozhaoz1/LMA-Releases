package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.TaskRegistryManifest;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管线 tick 异常护栏 (v79.63) — 引擎级容错: **单个管线抛异常不再崩服**。
 *
 * <p><b>为什么需要</b>: 三个 tick 调用点原为裸调用 ({@code h.pipeline().tick(sl, maid)}), 异常沿
 * {@code GMPM → TaskTickHandler.onServerTick → EventBus → MinecraftServer.tickServer} 上传 ——
 * Forge/NeoForge 事件总线对 listener 异常是「**记录后重抛**」(`catch Throwable →
 * handleException → athrow`, eventbus-6.2.33 字节码实证), 故等于**崩服**。
 * 而 {@code LMAT} 是对外扩展 API — 第三方 mod 写错一个管线即可崩掉所有玩家, 且无任何隔离信息。
 *
 * <p><b>错误边界 (用户裁定 2026-09-11)</b>: 捕获 {@link Throwable} 以覆盖 NPE/CME/ISE/SOE 与
 * {@link LinkageError}/{@code NoSuchMethodError} (mod 环境版本错配常见), **但
 * {@link OutOfMemoryError} 直接重抛** — OOM 后引擎状态不可信, 继续吞掉等于掩盖灾难。
 *
 * <p><b>降级策略 (自愈)</b>: 同一 (女仆, 任务) 连续失败达
 * {@link #MAX_CONSECUTIVE_FAILURES} 次 → 主动任务 {@code TaskDispatcher.fail} /
 * 被动 {@code cancelPassive} + 失败气泡, 防「每 tick 重复抛」刷屏与永久僵尸任务;
 * 一旦成功即清零计数 (瞬时抖动不累积)。
 *
 * <p><b>日志节流</b>: 首次失败打完整堆栈 (ERROR), 重复失败降为压缩单行 (前
 * {@link #MAX_CONSECUTIVE_FAILURES} 次) — 定位需要堆栈, 但不需要每 tick 一份。
 *
 * <p>计数表 per-maid 键控 + {@link MaidUnloadRegistry} 声明式清理 (id 复用串扰防护)。
 */
public final class EngineGuard {

    /** 连续失败上限 — 达到即降级终结任务 (防每 tick 重抛) */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    /** 连续失败计数: maidUuid + "|" + taskType → 次数 (仅服务端 tick 线程写; 卸载与成功即清) */
    private static final Map<String, Integer> FAILURES = new ConcurrentHashMap<>();

    static {
        // 卸载清理: 按女仆 uuid 前缀摘除本女仆全部任务计数 (防 uuid 泄漏 / 新女仆继承旧计数)
        MaidUnloadRegistry.register(maid -> clearMaid(maid.getStringUUID()));
    }

    private EngineGuard() {}

    /** 主动管线 tick (GMPM.tickActive) — 失败达阈值 → fail (含失败气泡 + 状态清理) */
    public static void tickActive(TaskPipeline pipeline, String taskType, ServerLevel sl, EntityMaid maid) {
        guard(pipeline, taskType, sl, maid, false);
    }

    /** 被动管线 tick (tickPassiveFor / tickStandalonePassives) — 失败达阈值 → cancelPassive */
    public static void tickPassive(TaskPipeline pipeline, String taskType, ServerLevel sl, EntityMaid maid) {
        guard(pipeline, taskType, sl, maid, true);
    }

    private static void guard(TaskPipeline pipeline, String taskType, ServerLevel sl, EntityMaid maid,
                              boolean passive) {
        String key = maid.getStringUUID() + "|" + taskType;
        try {
            pipeline.tick(sl, maid);
            if (FAILURES.remove(key) != null) {
                LittleMaidMoreAction.LOGGER.info("[LMA/Engine] 管线恢复正常 task={} maid={}", taskType,
                        maid.getStringUUID());
            }
        } catch (OutOfMemoryError oom) {
            // 用户裁定: OOM 不吞 — 引擎状态已不可信, 交给上层崩溃处理 (掩盖 = 灾难)
            throw oom;
        } catch (Throwable t) {
            int n = FAILURES.merge(key, 1, Integer::sum);
            if (n <= MAX_CONSECUTIVE_FAILURES) {
                LittleMaidMoreAction.LOGGER.error("[LMA/Engine] 管线异常已隔离 (第 {} 次) task={} maid={} drive={}",
                        n, taskType, maid.getStringUUID(), passive ? "PASSIVE" : "ACTIVE", t);
            }
            if (n == MAX_CONSECUTIVE_FAILURES) {
                LittleMaidMoreAction.LOGGER.error("[LMA/Engine] 管线连续 {} 次异常 → 降级终结 task={} maid={}",
                        n, taskType, maid.getStringUUID());
                degrade(maid, taskType, passive);
                FAILURES.remove(key);
            }
        }
    }

    /** 降级: 终结异常任务 (主动 fail / 被动 cancelPassive) — 自身再抛则只记日志 (护栏不得成为新崩溃点) */
    private static void degrade(EntityMaid maid, String taskType, boolean passive) {
        try {
            if (passive) {
                TaskDispatcher.cancelPassive(maid, taskType);
            } else {
                TaskDispatcher.fail(maid, "管线连续异常 (" + MAX_CONSECUTIVE_FAILURES + " 次) — 引擎隔离");
            }
        } catch (Throwable t) {
            LittleMaidMoreAction.LOGGER.error("[LMA/Engine] 降级终结失败 task={} maid={} — 强制清状态",
                    taskType, maid.getStringUUID(), t);
            try {
                TaskStateManager.clearAll(maid);
                maid.getPersistentData().remove(
                        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.passiveKey(taskType));
            } catch (Throwable ignored) {
                // 兜底再失败: 不再上抛 (护栏兜底路径)
            }
        }
    }

    /** 卸载清理 — 摘除该女仆全部任务的失败计数 (前缀匹配) */
    private static void clearMaid(String maidUuid) {
        FAILURES.keySet().removeIf(k -> k.startsWith(maidUuid + "|"));
    }

    /** 当前是否处于异常计数状态 (诊断/测试用) */
    public static int failureCount(String maidUuid, String taskType) {
        return FAILURES.getOrDefault(maidUuid + "|" + taskType, 0);
    }

    /** 驱动模式 → 护栏通道 (供引擎分派时选主动/被动语义) */
    public static void tickByDrive(TaskPipeline pipeline, String taskType,
                                   TaskRegistryManifest.Drive drive, ServerLevel sl, EntityMaid maid) {
        if (drive == TaskRegistryManifest.Drive.ACTIVE) {
            tickActive(pipeline, taskType, sl, maid);
        } else {
            tickPassive(pipeline, taskType, sl, maid);
        }
    }
}

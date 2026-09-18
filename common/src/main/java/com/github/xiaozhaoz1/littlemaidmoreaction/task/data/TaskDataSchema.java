package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;

/**
 * 女仆任务数据 schema 版本 + 迁移入口 (v79.63 架构评审 P1-4)。
 *
 * <p><b>为什么需要</b>: v79.63 整体删除了 `smithing` 锻造任务、v79.62 删了 `snow_shovel`、
 * v79.62.5 删了 `temp_adapt` —— **旧存档里这些任务留下的 PD 键没有任何东西会去清**:
 * <ul>
 *   <li>`lma_flow_task = "smithing"` + `in_progress`: 引擎既不看门狗(tick 不存在)也不清理
 *       → 女仆带着一个**永远无法完成、也不报错**的状态; 排查时"女仆怪怪的但日志干净"。</li>
 *   <li>`lma_passive_snow_shovel` / `lma_passive_temp_adapt`: 无注册条目 → 永不读取, 永久驻留。</li>
 * </ul>
 * 此前**没有任何迁移门** (全项目零 schema/data_version 键)。现在: 女仆加入世界时按版本号补跑迁移。
 *
 * <p><b>用法</b>: {@code TlmEventAdapter.onEntityJoin} 调 {@link #ensure(EntityMaid)} (幂等)。
 * 新增迁移 = {@link #CURRENT_VERSION} +1 并在 {@link #migrate} 追加分支 (老版本逐步跑到最新)。
 *
 * <p><b>纪律</b>: 迁移只做「清/改键」, 不改语义; 崩了不能连带崩服 (每个迁移步 try/catch)。
 * 纯逻辑 (只碰 NBT 键) — 但入口收 EntityMaid, 故不做纯 JVM 单测 (键表由 DataKeyConsistencyTest 守)。
 */
public final class TaskDataSchema {

    /** 当前 schema 版本 — 每次"键结构变化/任务删除"递增。 */
    public static final int CURRENT_VERSION = 1;

    /** v1 迁移要清的已删任务被动键 (任务类型已从注册表移除, 键永不读取)。
     *  snow_shovel (v79.62 删, TLM 原版清雪覆盖) / temp_adapt (v79.62.5 删, TLM getAtBiomeTemp 覆盖) /
     *  monster_log / light_control (v79.58 删)。 */
    private static final String[] REMOVED_PASSIVE_TYPES = {
            "snow_shovel", "temp_adapt", "monster_log", "light_control"
    };

    private TaskDataSchema() {}

    /**
     * 确保该女仆的数据处于当前 schema (幂等; 加入世界时调用)。
     *
     * @return true = 执行过迁移 (调用方可记日志)
     */
    public static boolean ensure(EntityMaid maid) {
        var pd = maid.getPersistentData();
        int from = pd.getInt(TaskKeys.SCHEMA_VERSION);
        if (from >= CURRENT_VERSION) return false;
        try {
            migrate(maid, from);
        } catch (Throwable t) {
            // 迁移失败不得连带崩服 — 记日志 + 打上版本号 (避免每次 join 重试刷屏);
            // 数据保持原样, 由孤儿收容 (GMPM) / 键级容错兜底
            LittleMaidMoreAction.LOGGER.error("[LMA/Schema] 迁移异常 maid={} from={} — 数据保留, 版本已打标",
                    maid.getStringUUID(), from, t);
        }
        pd.putInt(TaskKeys.SCHEMA_VERSION, CURRENT_VERSION);
        LittleMaidMoreAction.LOGGER.info("[LMA/Schema] 数据迁移完成 maid={} {} → {}",
                maid.getStringUUID(), from, CURRENT_VERSION);
        return true;
    }

    /** 逐步迁移 (老 → 新); 每个版本一个分支, 顺序执行不跳版 */
    private static void migrate(EntityMaid maid, int from) {
        var pd = maid.getPersistentData();
        // ── v0 → v1: 清已删任务的被动键 + 孤儿流程任务状态 ──
        if (from < 1) {
            for (String type : REMOVED_PASSIVE_TYPES) {
                pd.remove(TaskKeys.passiveKey(type));
                // 管线临时数据/配置也一并清 (任务已不存在, 数据无消费方)
                pd.remove(TaskKeys.PL_PREFIX + type);
                pd.remove(TaskKeys.CFG_PREFIX + type);
            }
            // 孤儿流程任务: in_progress 但注册表里没有该类型 → 归零 (与 GMPM 孤儿收容同语义,
            // 这里覆盖"女仆下次 join 时才被发现"的场景)
            String task = pd.getString(TaskKeys.FLOW_TASK);
            if (!task.isEmpty()
                    && com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get(task) == null) {
                LittleMaidMoreAction.LOGGER.info("[LMA/Schema] v1 清理孤儿流程任务 '{}' maid={}",
                        task, maid.getStringUUID());
                for (DataKey<?> key : DataKey.CLEAR_ALL_KEYS) {
                    pd.remove(key.key());
                }
                pd.remove(TaskKeys.FLOW_TASK);
            }
        }
        // 未来: if (from < 2) { ... }
    }
}

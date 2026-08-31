package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Set;

/**
 * 纯触发型被动任务 (v79.61x 被动脱管线改造, 用户批准) — 「信号 → 原子动作」零管线形态。
 *
 * <p>适用: 无 tick 工作的被动 (structure_sense / festival / 未来 PatPat 同款) —
 * 不实现 TaskPipeline, 不经过 GMPM tick 通道, 无状态机无持久化。
 * 由 {@link PassiveDispatcher} 驱动: 信号匹配 → 开关检查 → haqi 底层覆盖检查 → 冷却 → {@link #trigger}。
 *
 * <p>任务树可见性与开关不变: 条目仍注册进 {@code TaskRegistry} (无 pipeline 占位),
 * TaskTree 遍历 HANDLERS 展示, TaskToggle 开关照旧 (标签兜底 taskType, 用户裁定)。
 */
public interface PassiveTask {

    /** 任务 ID (与 TaskRegistry 条目一致) */
    String taskType();

    /** 触发冷却 (tick, 0 = 无冷却) — Dispatcher 内存表, 断线/卸载清理 */
    int cooldown();

    /** 信号声明 (needsSignals — 镜像管线 validate 声明面, 含通配前缀支持) */
    Set<String> needsSignals();

    /** 信号匹配后执行原子动作 (服务端) */
    void trigger(ServerLevel level, EntityMaid maid, String signalId);

    /** 任务树展示步骤 (默认空) */
    default List<TaskPipeline.TaskStep> steps() {
        return List.of();
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.UUID;

/**
 * 女仆缓存键规范 (v79.63 统一) — LMA 自有 per-maid 缓存的键**一律用 {@link #uuid}**。
 *
 * <p><b>为什么需要规范</b>: 评审 2026-09-11 发现同一概念存在三种键 — {@code maid.getId()}(int) /
 * {@code maid.getUUID()} / {@code maid.getStringUUID()} — 分散在 8 张静态表里。风险不是美观:
 * <ul>
 *   <li><b>MC 实体 ID 会被复用</b> — 项目自己在 {@code EntityCleanupListener} javadoc 记过
 *       「防实体 ID 复用串扰」(新女仆继承旧状态: 跳过集残留 / 气泡节流错乱 / 模式跨任务残留)。
 *       若某条卸载路径漏触发 (非标准移除 / 停服 / 维度卸载), int 键会让新女仆**静默继承**旧女仆状态。</li>
 *   <li>UUID 全局唯一且随存档持久 → 即使漏清理也**不会串扰**(只是泄漏), 故障模式从"数据错乱"降级为"内存占用"。</li>
 * </ul>
 *
 * <p><b>用法</b>: 新静态缓存/键控表用 {@link #uuid}。{@link #entityId} 仅限下列
 * 「MC API 在热路径上要求实体 ID」的例外 (两类都必须登记卸载清理):
 * <ol>
 *   <li>{@code EntityScanCache.queryCache} — 扫描路径本身以 {@code entity.getId()} 取女仆做排除,
 *       键必须同源</li>
 *   <li>{@code FakePlayerManager.TASKS} — tick 回投需要 {@code level.getEntity(id)} 重新解析女仆
 *       (避免长期持有实体强引用)</li>
 *   <li>{@code ScanScheduler}/ScanJob 的 {@code ownerId} — 归属者标识随任务句柄一起传递
 *       (与上同理: 延迟解析)</li>
 * </ol>
 */
public final class MaidKey {

    private MaidKey() {}

    /** 规范缓存键 — 女仆 UUID (全局唯一 + 存档持久; 漏清理只会泄漏不会串扰) */
    public static UUID uuid(EntityMaid maid) {
        return maid.getUUID();
    }

    /** 规范缓存键的字符串形式 (需要 String 键的 map 用这个, 如 config JSON 按 uuid 索引) */
    public static String uuidString(EntityMaid maid) {
        return maid.getStringUUID();
    }

    /**
     * 实体 ID — **仅限 MC API 热路径要求实体的例外** (见类 javadoc 三项)。
     * 新代码不要用它做缓存键。
     */
    public static int entityId(EntityMaid maid) {
        return maid.getId();
    }
}

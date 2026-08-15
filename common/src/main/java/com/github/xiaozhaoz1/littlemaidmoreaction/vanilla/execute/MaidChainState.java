package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 女仆采集运行时状态 — per-maid 实例 (v79.52: 原 6 张跨女仆静态 map 收编)。
 *
 * <p>原 ChainHarvestExecute 静态表缺陷: SKIP_AT 全局共享 (跨女仆 pos→time —
 * 女仆 A 清理误删女仆 B 的 TTL)、int 实体 ID key (ID 复用串扰)、清理散落 3 处。
 * 收编后: 数据归属正确 (每女仆一份)、清理一行化 (STATES.remove(uuid))、
 * UUID 稳定、ConcurrentHashMap 线程安全。
 *
 * <p>状态 + 跳过集行为 (tier 分组/容量淘汰/过期) 内聚本类 — 纯 JVM 可测
 * (无 MC 类依赖; 仅 lastMode 字段类型引用 MC 枚举, 测试不触碰即可);
 * <b>不持有 EntityMaid 引用</b> (防强引用表阻止实体 GC)。
 */
final class MaidChainState {
    /** 全局扫描节流 (原 LAST_SCAN) — 0=未扫过 */
    long lastScan;
    /** nearPass 3 格近扫节流 (原 LAST_NEAR_SCAN) — 0=未扫过 */
    long lastNearScan;
    /** 上次执行模式 (原 LAST_MODE — 模式切换时重建对象) */
    ChainHarvestExecute.Mode lastMode;
    /** 背包满检查节流 (v79.53) — 全背包遍历每 20t 一次 (原每 tick 32 槽遍历) */
    long invCheckTick;
    /** 背包空间缓存 (v79.53) — 满时暂停, 清包后 ≤20t 恢复 */
    boolean hasSpace = true;
    /** 跳过集分组 tier (原 SkipState.tierLevel — 换工具等级变化时清空) */
    int tierLevel = Integer.MIN_VALUE;
    /** 跳过集 (原 SkipState.positions) — 失败目标暂时跳过, TTL 过期重试 */
    final LinkedHashSet<Long> skipped = new LinkedHashSet<>();
    /** 跳过条目时间戳 (原全局 SKIP_AT → per-maid — 误删根治) — pos.asLong → gameTime */
    final Map<Long, Long> skipAt = new HashMap<>();
    /** 失败计数 (per-pos, 上限 {@link #MAX_FAIL}) — 连续失败升级 TTL (v79.56 用户裁定:
     *  第 1 次 60t / 第 3 次 600t = 30 秒封顶, 永久不可达不卡住)。计数绑定跳过条目:
     *  短 TTL 过期保留 (累积到 3 次), 长 TTL 过期重置 (新周期), 换工具 (maintainTier) 清空,
     *  卸载随 STATES 整清 — 无跨 session 残留 (矿可能被挖, 重启重新评估) */
    final Map<Long, Integer> failCounts = new HashMap<>();

    /** 失败计数升级阈值 — ≥3 次进入长 TTL */
    static final int MAX_FAIL = 3;
    /** 长 TTL (600t = 30 秒) — 连续失败 ≥3 次后跳过 30 秒 (用户裁定上限, 不永久卡住) */
    static final long TTL_LONG = 600;

    /** tier 分组维护 — 换工具等级变化时清空跳过集+时间戳+失败计数 (原 skippedFor 内联;
     *  per-maid 归属后无全局连带清, 孤儿时间戳根治) */
    void maintainTier(int tier) {
        if (tierLevel != tier) {
            skipped.clear();
            skipAt.clear();
            failCounts.clear();
            tierLevel = tier;
        }
    }

    /** 记跳过 — 容量上限淘汰最旧 (原 addSkip 内联; max = SKIP_MAX = 10) + 失败计数递增 (上限 MAX_FAIL) */
    void addSkip(long pos, long now, int max) {
        if (skipped.size() >= max) {
            long oldest = skipped.iterator().next();
            skipped.remove(oldest);
            skipAt.remove(oldest);
            failCounts.remove(oldest);
        }
        skipped.add(pos);
        skipAt.put(pos, now);
        failCounts.merge(pos, 1, (a, b) -> Math.min(MAX_FAIL, a + b));
    }

    /** TTL 按失败计数升级 — ≥3 次连续失败 → 长 TTL 600t (30 秒封顶, 用户裁定) */
    long ttlFor(long pos, long baseTtl) {
        return failCounts.getOrDefault(pos, 0) >= MAX_FAIL ? TTL_LONG : baseTtl;
    }

    /** 过期判定 (原 findNearestValid removeIf 内联) — TTL 过期 → 移除时间戳并返回 true (调用方从 skipped 移除)。
     *  失败计数: 短 TTL 过期保留 (累积到 3 次升级), 长 TTL 过期重置 (新周期重头计数) */
    boolean expire(long pos, long now, long baseTtl) {
        long t = skipAt.getOrDefault(pos, 0L);
        if (t != 0 && now - t > ttlFor(pos, baseTtl)) {
            skipAt.remove(pos);
            if (failCounts.getOrDefault(pos, 0) >= MAX_FAIL) {
                failCounts.remove(pos);
            }
            return true;
        }
        return false;
    }
}

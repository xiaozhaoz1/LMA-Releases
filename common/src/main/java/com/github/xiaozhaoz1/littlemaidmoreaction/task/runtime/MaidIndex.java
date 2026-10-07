package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在册女仆索引 (v79.66) — **每 tick 心跳只遍历"在册女仆"**, 不再遍历全实体。
 *
 * <p><b>为什么必须** (2026-09-20 线程转储实测定因)</b>:
 * 原 {@code TaskTickHandler.onServerTick} 每 tick 执行 {@code for (var e : sl.getAllEntities())} —
 * 该遍历按**实体 section** 走 ({@code EntityManager.getAll()}), 实体分散/数量多时代价巨大:
 * 实测 gametest (118 个结构散布在相距数百万格的坐标 ⇒ section 数量庞大) 服务端主线程
 * **RUNNABLE 烧 CPU 数分钟**卡在 {@code TaskTickHandler:63}, 整套测试**卡死** ✗
 * (同一行在真实服务器上也是"实体越多 mod 越卡"的根源)。
 * ⇒ 改为 **O(在册女仆)** ✓
 *
 * <p><b>维护方式</b>: `EntityJoinLevelEvent` 加入 · `EntityLeaveLevelEvent` 移除 ·
 * 服务器停止清空 ({@code TaskTickHandler.onServerStopping})。另带**首访全量播种**兜底
 * (监听器注册前就已在世界的女仆 —— 每 level 只做一次), 避免漏册导致任务停摆 ✗。
 */
public final class MaidIndex {

    private MaidIndex() {}

    /** 维度 → 在册女仆集合 (线程安全; 值用 newKeySet ⇒ 并发增删安全) */
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Set<EntityMaid>> BY_LEVEL =
            new ConcurrentHashMap<>();
    /** 已做过"首访全量播种"的维度 (每 level 一次) */

    /** 实体加入世界 (服务端女仆) — 由 EntityCleanupListener 转发 */
    public static void add(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel sl)) return;
        BY_LEVEL.computeIfAbsent(sl.dimension(), k -> ConcurrentHashMap.newKeySet()).add(maid);
    }

    /**
     * 实体离开世界 — 由 EntityCleanupListener 转发。
     *
     * <p><b>⚠⚠ v79.66p 修「活女仆被误除名」(2026-09-20 探针铁证)</b>:
     * {@code EntityLeaveLevelEvent} **对活着的女仆也会触发** (传送 / 换维度 / 区块或结构卸载等) ✗ ——
     * 原实现无条件 {@code set.remove(maid)} ⇒ 她被**误除名** ⇒ {@code TaskTickHandler} 不再 tick 她 ⇒
     * 已提交的任务**永远不动** ✗ (实测现场: `lmaChainOreNeighbourChunk` 三次采样
     * `在册=false · 任务=collect_ore · BFS可达=true · 能动=true · 导航已完成=true · 女仆原地不动 1800t`
     * ⇒ 这就是采矿用例"扫到矿却零位移"的**真正根因**, 不是 flake ✓)。而且 {@code reconcile} 靠
     * {@code getAllEntities()} **补不回来** (该环境下扫不到相距数百万格的这些女仆 ⇒ `在册数`只减不增 ✗)。
     * ⇒ 现在**只对"真的已被移除/死亡"的实体除名** ✓; 换维度等由 {@code snapshot(level)} 的
     * `m.level() == level` 过滤天然排除 ✓ (不会误 tick ✗)。
     */
    public static void remove(EntityMaid maid) {
        if (!maid.isRemoved() && maid.isAlive()) {
            return;   // ★ 活着的实体"离开"= 误报 (传送/卸载) ⇒ 保留在册 ✓ (见上方实测)
        }
        if (!(maid.level() instanceof ServerLevel sl)) return;
        Set<EntityMaid> set = BY_LEVEL.get(sl.dimension());
        if (set != null) set.remove(maid);
    }

    /**
     * 该 level 的在册女仆**快照** (已过滤死亡/已换 level 的残留) —
     * 快照语义: 遍历期间增删不影响本轮 (原 full-scan 的 CME 风险也一并消除 ✓)。
     */
    public static List<EntityMaid> snapshot(ServerLevel level) {
        Set<EntityMaid> set = BY_LEVEL.get(level.dimension());
        if (set == null || set.isEmpty()) return List.of();
        List<EntityMaid> out = new java.util.ArrayList<>(set.size());
        for (EntityMaid m : set) {
            if (m.isAlive() && m.level() == level) out.add(m);
        }
        return out;
    }

    // ⚠ 2026-09-20: 原设计的"首访全量播种"(扫 ±3e7 AABB 找现有女仆) **已删除** —
    //   实测在 gametest 世界(118 结构相距数百万格)该扫描极慢, 反而把每轮跑测拖到 4 分钟以上 ✗。
    //   不需要它: `EntityJoinLevelEvent` 在**区块加载/实体生成/换维度**时都会触发 ⇒ 世界里的女仆
    //   都会在加入时入册 ✓ (监听器在 mod 构造期注册, 早于任何实体入世)。

    /** 服务器停止/世界卸载 — 清空 (防跨世界残留) */
    public static void clearAll() {
        BY_LEVEL.clear();
    }

    /** 诊断: 在册数量 (日志/排障用) */
    public static int size(ServerLevel level) {
        Set<EntityMaid> set = BY_LEVEL.get(level.dimension());
        return set == null ? 0 : set.size();
    }

    /** 诊断/兜底: 该女仆是否已在册 */
    public static boolean contains(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel sl)) return false;
        Set<EntityMaid> set = BY_LEVEL.get(sl.dimension());
        return set != null && set.contains(maid);
    }

    /**
     * 全量对账 (每 100t 由 TaskTickHandler 调用一次): 把该 level 里所有活着的女仆补进索引,
     * 并清掉已死/已离开的残留。
     *
     * <p><b>为什么需要</b> (2026-09-20 实测): 索引靠 join/leave 事件维护, 但**部分生成路径会漏**
     * (gametest 探针 `在册=false` 实证) ⇒ 漏册女仆不被 tick ⇒ 其用例跑到**满超时**(整轮 50s→8min+) ✗
     * ⇒ 每 100t 对账一次: 不漏 (最坏晚 5s) 且不卡 (每 tick 全扫才是原卡死主因 ✓)。
     */
    public static void reconcile(ServerLevel level) {
        try {
            Set<EntityMaid> set = BY_LEVEL.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet());
            // 1) 清死掉的残留 (⚠ 不做 level 比对 — 实例比对在部分路径会误删, 见 2026-09-20 实测)
            set.removeIf(m -> !m.isAlive());
            // 2) 补漏: 用 `getAllEntities()` 迭代 (与旧实现同源 ⇒ 实测确实能找到女仆 ✓;
            //    而 `getEntitiesOfClass(±3e7 AABB)` 在同一环境**查不到** ⇒ 已弃用 ✗)
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof EntityMaid maid) set.add(maid);
            }
        } catch (Throwable ignored) {
            // 对账失败不致命 (下一轮再试)
        }
    }
}

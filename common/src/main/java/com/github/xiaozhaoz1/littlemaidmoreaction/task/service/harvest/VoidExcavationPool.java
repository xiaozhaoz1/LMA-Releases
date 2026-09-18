package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.MaidUnloadRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 空置域**区块认领池 + 扫描共享缓存** (v79.63 自 {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.VoidExcavationPipeline} 抽出)。
 *
 * <p><b>为什么抽</b>: 管线类 873 行里约 165 行是"多女仆共享的运行时状态 + 并发协调"
 * (认领池 / 认领归属 / 预载状态 / 扫描缓存 / 递归认领 / 超时自愈), 与"相位机"是两件事;
 * 按分层判据 (task/service = 管线共用的领域算法; task/pipeline = 相位机) 应归本层。
 * 抽出后管线只调本类 API, 不再直接摸静态表与锁 (锁封闭在池内)。
 *
 * <p><b>并发</b>: 多女仆同 tick 并发认领 — 认领/标记/释放全部在 {@code POOL_LOCK} 下原子完成
 * (用户实证: 第 4 女仆启动卡死 → 必须保证认领原子性)。
 *
 * <p><b>生命周期</b>: 认领是运行时状态 (不落盘) — 服务器启动 {@link #resetPool} 清空;
 * 女仆卸载/死亡/魂符 {@link #onMaidUnload} 释放其认领 (v79.62.2 用户裁定);
 * 另有 {@code CLAIM_TIMEOUT_TICKS} 心跳超时自愈兜底 (防 onMaidUnload 漏触发导致永久占坑)。
 */
public final class VoidExcavationPool {

    static {
        MaidUnloadRegistry.register(VoidExcavationPool::onMaidUnload);
    }

    private VoidExcavationPool() {}

    // ── 多女仆动态领取 (v79.62.1 用户裁定: 先到先挖, 挖完领下一块, 防同区块抢挖) ──
    // 维度 → (区块 key → 状态): 0=进行中, 1=已挖完. 女仆领取未挖区块, 挖完标记, 领下一个.
    private static final java.util.Map<String, java.util.Map<Long, Integer>> BLOCK_POOL =
            new java.util.concurrent.ConcurrentHashMap<>();
    // 认领归属 (v79.62.2): maid UUID → (region, cx, cz) — 卸载/死亡时按 uuid 释放认领,
    // 不依赖 PD/level 时序 (EntityLeaveLevelEvent 时 level 可能非 ServerLevel, PD 可能已被 flush). */
    private static final java.util.Map<String, ClaimInfo> CLAIM_OWNER =
            new java.util.concurrent.ConcurrentHashMap<>();

    // v79.62.2 方案 A: 空置域内置 3×3 预载状态 (per-maid, 认领区块为中心) —
    // 不复用 behavior 的 ChunkWorkArea.State (那是 farm 的), 用独立 map.
    private static final java.util.Map<String, com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.ChunkWorkArea.State> CHUNK_WORK_STATE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 取/建该女仆的预载状态 (静态 map, uuid 键, 卸载/清理时移除) */
    public static com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.ChunkWorkArea.State chunkWorkState(EntityMaid maid) {
        return CHUNK_WORK_STATE.computeIfAbsent(maid.getStringUUID(),
                k -> new com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.ChunkWorkArea.State());
    }

    /** 认领归属信息 — 含认领心跳 (超时自愈用: 距上次活跃超过 CLAIM_TIMEOUT_TICKS → 自动释放, 防永久占坑).
     *  <p>v79.62.2 心跳 = 每挖一格更新 (非认领开始时间) — 黑曜石/慢速工具永不误杀 (只要在挖就续心跳). */
    private static final class ClaimInfo {
        final String region; final int cx; final int cz;
        long lastActiveTick;
        ClaimInfo(String region, int cx, int cz, long now) { this.region = region; this.cx = cx; this.cz = cz; this.lastActiveTick = now; }
    }

    /** 认领超时 (tick) — 距上次心跳超过 600t (30秒) → 视为失联, 自动释放为未挖.
     *  <p>v79.62.2 修: onMaidUnload 可能漏触发 (女仆异常/收起时序), 认领池永久 1 → 死等 (cursor=0,0,0 + claimWait 循环).
     *  心跳式超时自愈让认领池对「认领后任务中断」也能收敛, 且不误杀慢速健康女仆. */
    private static final long CLAIM_TIMEOUT_TICKS = 600;

    /** 更新认领心跳 (每挖一格调) — 刷新 CLAIM_OWNER 里自己的 lastActiveTick. */
    public static void claimHeartbeat(EntityMaid maid, long now) {
        String uid = maid.getStringUUID();
        synchronized (POOL_LOCK) {
            ClaimInfo ci = CLAIM_OWNER.get(uid);
            if (ci != null) ci.lastActiveTick = now;
        }
    }

    /** v79.62.1 服务器启动清空认领池 (用户裁定: 认领是运行时状态, 未持久化 — 游戏重启后应空,
     *  女仆重新认领, 同区块缓存语义; 否则静态 Map 残留旧认领 → 新女仆挖旧光标/拿不到区块). */
    public static void resetPool() {
        synchronized (POOL_LOCK) {
            BLOCK_POOL.clear();
            SCAN_CACHE.clear();
            CLAIM_OWNER.clear();   // v79.62.2 认领归属一并清 (ServerStarting 重置, 防跨存档残留)
            CHUNK_WORK_STATE.clear();   // v79.62.2 预载状态 (重启后区块强载自然消失)
        }
    }

    /** 女仆卸载/死亡/收入魂符 → 释放其认领的区块为"未挖" (v79.62.2, MaidUnloadRegistry 登记).
     *  按 {@link #CLAIM_OWNER} 的 uuid 记录释放 — 不依赖 level/PD 时序 (EntityLeaveLevelEvent 时
     *  level 可能非 ServerLevel, PD 可能已被 flushAllPl 处理), 幂等.
     *  <p>★ v79.69.2: 同时**还原调度坐标劫持** — 原实现只放认领, 任务中卸载/退游戏时 SchedulePos
     *  三点 (会落盘!) 留在坑位 ⇒ 之后任何开 home 的任务 (跑步机等) 都被 TLM `restrictTo(workPos)`
     *  传送回挖空区 ✗ (用户实测「空置域已关闭仍被拉回」)。 */
    public static void onMaidUnload(EntityMaid maid) {
        // ⚠ 2026-09-18 回滚 (错题 #357): 此处曾加"任务已结束 ⇒ 还原调度坐标劫持" (修「空置域已关闭仍被拉回」),
        // 但实测 `lmavoidexcavationmultimaid` 在 neo 稳定失败 (dug=0/4, forge 绿) ⇒ 整体回滚, 只保留原行为:
        // 释放认领 + 清认领归属 (调度坐标还原留待专项定位, 见 #357)
        String uid = maid.getStringUUID();
        synchronized (POOL_LOCK) {
            ClaimInfo ci = CLAIM_OWNER.remove(uid);
            if (ci == null) return;
            BLOCK_POOL.computeIfAbsent(ci.region, k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put(net.minecraft.world.level.ChunkPos.asLong(ci.cx, ci.cz), 0);   // 释放为未挖
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                "[VOID-MOVE] UNLOAD release maid={} region={} block=({},{})", uid.substring(0, 8), ci.region, ci.cx, ci.cz);
        }
    }

    // ── 调度坐标劫持的"可恢复 + 落盘" (v79.69.2) ──────────────────────────────
    // 背景: adaptToChunk 把 SchedulePos 的 work/idle/sleep 三点劫持到坑位 (为了让 TLM restrict 跟着
    // 工作区), 而 SchedulePos.save/load **会落盘** ⇒ 只要还原没跑 (任务中卸载/退游戏/重启, 内存 State
    // 丢失且 resetPool 清空) ⇒ 三点永久留在坑里 ✗。现将原点存进女仆 NBT (cfg) + 标记, 三条路径还原:
    // ① onCleanup (正常结束) ② onMaidUnload (卸载/死亡/存档) ③ onEntityJoin (重进自愈, 未恢复任务时) ✓

    private static final String CFG_SCHED_HIJACK = "schedHijack";
    private static final String CFG_ORIG_WORK = "origWork";
    private static final String CFG_ORIG_IDLE = "origIdle";
    private static final String CFG_ORIG_SLEEP = "origSleep";

    /** 劫持**前**把 SchedulePos 三点写进 cfg (持久 NBT) + 打标记 — 幂等 (只记一次). */
    public static void markScheduleHijack(EntityMaid maid) {
        var cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, "void_excavation");
        if (cfg.getBoolean(CFG_SCHED_HIJACK)) return;
        var sp = maid.getSchedulePos();
        BlockPos w = sp.getWorkPos() != null ? sp.getWorkPos() : maid.blockPosition();
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, CFG_ORIG_WORK, w);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, CFG_ORIG_IDLE,
                sp.getIdlePos() != null ? sp.getIdlePos() : w);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.writeBlockPos(cfg, CFG_ORIG_SLEEP,
                sp.getSleepPos() != null ? sp.getSleepPos() : w);
        cfg.putBoolean(CFG_SCHED_HIJACK, true);
    }

    /** 按 cfg 记录还原 SchedulePos 三点 + 清 restrict + 清标记 (幂等; 任务结束/卸载/重进都调).
     *  @return true = 本次确实还原 */
    public static boolean restoreScheduleHijack(EntityMaid maid) {
        var cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfg(maid, "void_excavation");
        if (cfg == null || !cfg.getBoolean(CFG_SCHED_HIJACK)) return false;
        var sp = maid.getSchedulePos();
        BlockPos w = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, CFG_ORIG_WORK);
        BlockPos i = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, CFG_ORIG_IDLE);
        BlockPos s = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, CFG_ORIG_SLEEP);
        if (w != null) sp.setWorkPos(w);
        if (i != null) sp.setIdlePos(i);
        if (s != null) sp.setSleepPos(s);
        maid.clearRestriction();
        cfg.remove(CFG_SCHED_HIJACK);
        cfg.remove(CFG_ORIG_WORK);
        cfg.remove(CFG_ORIG_IDLE);
        cfg.remove(CFG_ORIG_SLEEP);
        LittleMaidMoreAction.LOGGER.info("[VOID] 还原调度坐标 (work/idle/sleep → {}) maid={}",
                w, maid.getStringUUID().substring(0, 8));
        return true;
    }

    /** 认领池锁 (多女仆同 tick 并发 — 防止 poolClaim/poolMark 竞态导致死循环/重复认领).
     *  v79.62.1 用户实证第 4 女仆启动卡死 → 同步保证认领原子性. */
    private static final Object POOL_LOCK = new Object();

    // ── 扫描结果共享缓存 (v79.62.1 用户裁定: 同区域女仆复用 — 起始点相同扫描结果一样) ──
    // region → (扫描完成时间 tick, outNoExpand 是否无空间). 女仆查共享: 同区域已有结果 → 复用
    // (不再每女仆独立扫描); 200t 过期重扫 (箱子状态会变).
    private static final java.util.Map<String, long[]> SCAN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int SCAN_CACHE_TTL = 200;

    private static void scanCachePut(String region, long time, boolean noExpand) {
        SCAN_CACHE.put(region, new long[]{time, noExpand ? 1 : 0});
    }

    /** 查询共享扫描结果: 返回 true = 该区域已有"无空间"缓存且在 TTL 内 (可直接返回 null 不扫).
     *  找到新空间 → 清缓存 (scanCachePut false). */
    private static boolean scanCacheNoSpace(ServerLevel world, String region) {
        long[] e = SCAN_CACHE.get(region);
        if (e == null) return false;
        if (world.getGameTime() - e[0] > SCAN_CACHE_TTL) { SCAN_CACHE.remove(region); return false; }
        return e[1] == 1;
    }

    /** 区块 key → 状态 (进行中=1, 已挖完=2). 女仆 tick 认领/释放用.
     *  v79.62.1 修: 池 key 用「维度+区域」而非纯维度 — 不同区域 (start+size) 的挖空任务
     *  独立认领 (gametest 实证: 多女仆测试残留污染单女仆测试 → 认领到已挖完区块不挖). */
    private static String poolRegion(ServerLevel world, BlockPos start, int size) {
        int scx = start.getX() >> 4, scz = start.getZ() >> 4;
        int minCX = scx - size / 2, minCZ = scz - size / 2;
        return world.dimension().location() + "|" + minCX + "," + minCZ + "," + size;
    }

    public static void poolMark(ServerLevel world, BlockPos start, int size, int cx, int cz, int state) {
        synchronized (POOL_LOCK) {
            BLOCK_POOL.computeIfAbsent(poolRegion(world, start, size), k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put(net.minecraft.world.level.ChunkPos.asLong(cx, cz), state);
        }
    }

    /** 领取下一个未挖区块 (区域边界内).
     *  @return 区块 key (已认领进行中); Long.MIN_VALUE = 无未挖但有进行中 (等待, 防误判完成);
     *  null = 全部已挖完 (任务可完成).
     *  v79.62.1 修: 原 null 只在"全挖完", 但有人进行中时第二个女仆误判完成不挖 (用户实证第二个不捡). */
    /** 认领结果 — null=全部挖完; claimed=true=认领 (cx,cz); claimed=false=等待 (有人正在挖). */
    public static final class BlockClaim {
        public final boolean claimed; public final int cx; public final int cz;
        public BlockClaim(boolean claimed, int cx, int cz) { this.claimed = claimed; this.cx = cx; this.cz = cz; }
    }

    public static BlockClaim poolClaim(EntityMaid maid, ServerLevel world, BlockPos start, int size,
                                        int minCX, int maxCX, int minCZ, int maxCZ) {
        String region = poolRegion(world, start, size);
        long now = world.getGameTime();
        synchronized (POOL_LOCK) {
            var pool = BLOCK_POOL.computeIfAbsent(region, k -> new java.util.concurrent.ConcurrentHashMap<>());
            boolean anyInProgress = false;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                    Integer st = pool.get(key);
                    if (st == null || st == 0) {   // 未挖 → 认领 (进行中)
                        pool.put(key, 1);
                        CLAIM_OWNER.put(maid.getStringUUID(), new ClaimInfo(region, cx, cz, now));
                        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.debug(
                            "[VOID-MOVE] CLAIM region={} block=({},{}) poolSize={}", region, cx, cz, pool.size());
                        return new BlockClaim(true, cx, cz);
                    }
                    if (st == 1) {
                        // 认领超时自愈: 认领者已离开/卡死 → 释放为未挖 (防永久占坑死等)
                        // 用显式遍历 (lambda 不能引用循环变量 cx/cz — 非 final)
                        ClaimInfo owner = null;
                        for (ClaimInfo ci : CLAIM_OWNER.values()) {
                            if (ci.region.equals(region) && ci.cx == cx && ci.cz == cz) { owner = ci; break; }
                        }
                        if (owner != null && now - owner.lastActiveTick > CLAIM_TIMEOUT_TICKS) {
                            // 显式删除匹配的认领归属 (removeIf 需 final 捕获, 改遍历)
                            java.util.Iterator<ClaimInfo> it = CLAIM_OWNER.values().iterator();
                            while (it.hasNext()) {
                                ClaimInfo ci = it.next();
                                if (ci.region.equals(region) && ci.cx == cx && ci.cz == cz) it.remove();
                            }
                            pool.put(key, 0);   // 超时 → 释放
                            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                                "[VOID-MOVE] CLAIM-TIMEOUT release region={} block=({},{})", region, cx, cz);
                            return poolClaim(maid, world, start, size, minCX, maxCX, minCZ, maxCZ);   // 递归重新认领
                        }
                        anyInProgress = true;
                    }
                }
            }
            // 有进行中的 → 等待; 全挖完 → null (完成) + 清理该区域 pool (v79.62.1 用户裁定:
            // 静态 Map 挖完清理, 防长期运行累积)
            if (!anyInProgress) BLOCK_POOL.remove(region);
            return anyInProgress ? new BlockClaim(false, 0, 0) : null;
        }
    }

    // ── v79.63 抽出时新增: 把"池内锁"封在池里, 调用方不再直接摸 CLAIM_OWNER / POOL_LOCK ──

    /** 该女仆当前是否有认领 (只读, 供调用方前置判断) */
    public static boolean hasClaim(EntityMaid maid) {
        return CLAIM_OWNER.containsKey(maid.getStringUUID());
    }

    /** 原子: 双检"无认领" → 把指定区块释放为未挖 (语义同原管线内 synchronized 双检块).
     *  @return true = 本次确实释放 (调用方据此清理 PD 进度与打日志) */
    public static boolean releaseStaleClaim(EntityMaid maid, ServerLevel world, BlockPos start, int size,
                                            int cx, int cz) {
        String uid = maid.getStringUUID();
        synchronized (POOL_LOCK) {
            if (CLAIM_OWNER.containsKey(uid)) return false;
            poolMark(world, start, size, cx, cz, 0);
            return true;
        }
    }

    /** 取走并移除该女仆的预载状态 (任务结束/清理时用; 无则 null) */
    public static com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.ChunkWorkArea.State removeChunkWorkState(EntityMaid maid) {
        return CHUNK_WORK_STATE.remove(maid.getStringUUID());
    }

    /** 清认领归属 (原子; 区块状态标记由调用方先做 poolMark) */
    public static void releaseClaim(EntityMaid maid) {
        synchronized (POOL_LOCK) { CLAIM_OWNER.remove(maid.getStringUUID()); }
    }
}

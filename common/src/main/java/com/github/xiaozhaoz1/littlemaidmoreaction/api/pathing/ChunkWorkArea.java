package com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 区块工作制 (v79.62.2) — HOME 模式大区域工作的「工作区适应器」, <b>pipeline 内部调用的工具方法</b>.
 *
 * <p><b>问题</b>: TLM `MaidConfig.MAID_WORK_RANGE` 上限 64 且 `SchedulePos.tick` 每 40t
 * 按活动重置 `restrictTo(workPos, ≤64)` — 大面积 (>64 格) 工作只设大半径无效, 每 40t
 * 被重置回 workPos+≤64, 女仆照旧被 TLM 拉回。唯一杠杆 = <b>移动 workPos/restrict 中心跟随
 * 当前工作区块</b>, 让女仆始终在 ≤64 的范围内。
 *
 * <p><b>用法 (pipeline 内部)</b>: pipeline 每 tick 调 {@link #adaptToChunk} (传工作区块 cx/cz —
 * void = 认领区块 curCX/curCZ, farm = 区域中心区块), onCleanup 调 {@link #restoreFromChunk}.
 * 数据 (工作区块坐标) 在管道内直接交互, 不经过 behavior 开关.
 *
 * <p>内部做: 首次适应记录原 workPos → `setWorkPos(工作区块中心)` + `restrictTo(12)` +
 * 3×3 `setChunkForced` 异步预载 (换块才重发) + 每 tick 心跳; 恢复时还原 workPos + 释放预载.
 * 状态放调用方 (pipeline 的 per-maid State) — 瞬态, 不写 NBT (用户裁定: 瞬态不落盘).
 */
public final class ChunkWorkArea {

    /** 跟随半径 — 女仆距区块中心 ≤8 格 < 12 不拉回 (TLM WORK_RANGE 最小 3 / 上限 64) */
    public static final int FOLLOW_RADIUS = 12;

    /** 内置区块缓存预加载半径 (区块): 工作区块 ±1 → 3×3 */
    public static final int PRELOAD_RADIUS = 1;

    private ChunkWorkArea() {}

    /** 工作区块中心 (区块 16×16 中心格; Y 取 anchorY) — 纯计算, 可单测 */
    public static BlockPos workChunkCenter(int cx, int cz, int anchorY) {
        return new BlockPos(cx * 16 + 8, anchorY, cz * 16 + 8);
    }

    /** 每女仆适应状态 (pipeline 持有, 瞬态不写 NBT) */
    public static final class State {
        /** 已临时 relocate (home → 工作区块) */
        public boolean adapted;
        /** 原 workPos/idlePos/sleepPos (恢复用; 首次适应时记录 3 点 — v79.62.2 全设区块中心) */
        public BlockPos origWorkPos;
        public BlockPos origIdlePos;
        public BlockPos origSleepPos;
        /** 当前适应的工作区块 (换块判定) */
        public int adaptedCX = Integer.MIN_VALUE;
        public int adaptedCZ = Integer.MIN_VALUE;
        /** 已预载 3×3 中心 (未预载 loaded=false) */
        public boolean loaded;
        public int loadCX, loadCZ;
    }

    /** 内置区块缓存: 工作区块变化时释放旧 3×3 + force 新 3×3 (异步后台生成, 主线程不阻塞).
     *  方法内部判未变 (loaded 且同中心) 零开销; 换块由 {@link #adaptToChunk} 判定后调用. */
    public static void updatePreload(ServerLevel world, State state, int cx, int cz) {
        if (state.loaded && state.loadCX == cx && state.loadCZ == cz) return;
        if (state.loaded) {
            for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
                for (int dz = -PRELOAD_RADIUS; dz <= PRELOAD_RADIUS; dz++) {
                    world.setChunkForced(state.loadCX + dx, state.loadCZ + dz, false);
                }
            }
        }
        for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
            for (int dz = -PRELOAD_RADIUS; dz <= PRELOAD_RADIUS; dz++) {
                world.setChunkForced(cx + dx, cz + dz, true);
            }
        }
        state.loaded = true;
        state.loadCX = cx;
        state.loadCZ = cz;
    }

    /** 释放内置区块缓存 3×3 (停止/恢复时调) */
    public static void releasePreload(ServerLevel world, State state) {
        if (!state.loaded) return;
        for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
            for (int dz = -PRELOAD_RADIUS; dz <= PRELOAD_RADIUS; dz++) {
                world.setChunkForced(state.loadCX + dx, state.loadCZ + dz, false);
            }
        }
        state.loaded = false;
    }

    /**
     * 区块工作制适应 (pipeline 每 tick 调) — 把 home/workPos/restrict 移到「工作区块中心」+ 预载 + 心跳.
     * <p>内部: 首次适应记录原 workPos → setWorkPos(工作区块中心) + restrictTo(12) (每 10t,
     * 防 SchedulePos 40t 重置大半径 → 拉回); 换块才重发 3×3 预载; 心跳刷新 (续超时).
     *
     * @param state per-maid 状态 (pipeline 持有)
     * @param cx    工作区块 chunkX (void = 认领区块 curCX)
     * @param cz    工作区块 chunkZ
     * @param anchorY 工作区块中心 Y (void = 当前挖掘层 y)
     */
    public static void adaptToChunk(ServerLevel world, EntityMaid maid, State state, int cx, int cz, int anchorY) {
        long now = world.getGameTime();
        // 首次适应: 记录原 workPos (恢复用)
        if (!state.adapted) {
            var sp0 = maid.getSchedulePos();
            state.origWorkPos = sp0.getWorkPos() != null ? sp0.getWorkPos() : maid.blockPosition();
            state.origIdlePos = sp0.getIdlePos() != null ? sp0.getIdlePos() : state.origWorkPos;
            state.origSleepPos = sp0.getSleepPos() != null ? sp0.getSleepPos() : state.origWorkPos;
            state.adapted = true;
        }
        // 重定位 (v79.62.2 每 tick 重设 3 点: workPos/idlePos/sleepPos 全 = 区块中心 —
        // SchedulePos.tick 每 40t 按 Activity 重设 restrict: WORK→workPos/12, IDLE→idlePos/6,
        // REST→sleepPos; 原只设 workPos → 女仆 Activity 变 IDLE 时 restrict 到原家 idlePos(半径6)
        // → 走两三格被拉回 (用户实证). 三点全设 → 任何 Activity 都 restrict 区块中心.)
        BlockPos center = workChunkCenter(cx, cz, anchorY);
        var sp = maid.getSchedulePos();
        sp.setWorkPos(center);
        sp.setIdlePos(center);
        sp.setSleepPos(center);
        maid.restrictTo(center, FOLLOW_RADIUS);
        // 换块才重发 3×3 预载
        if (state.adaptedCX != cx || state.adaptedCZ != cz) {
            updatePreload(world, state, cx, cz);
            state.adaptedCX = cx;
            state.adaptedCZ = cz;
        }
    }

    /**
     * 区块工作制恢复 (pipeline onCleanup 调) — 还原原 workPos + 释放 3×3 预载. 幂等.
     * v79.62.2 修「切砍树不往矿走」: 原还原 restrict 中心到老家 → 女仆被 restrict 12 格内,
     * 远处矿 (>12) 被排除 → 不找不挖 (用户实证). 改清 restrict — 还原 workPos 但不限制活动范围.
     */
    public static void restoreFromChunk(ServerLevel world, EntityMaid maid, State state) {
        if (!state.adapted && !state.loaded) return;
        if (state.adapted && state.origWorkPos != null) {
            var sp = maid.getSchedulePos();
            sp.setWorkPos(state.origWorkPos);
            if (state.origIdlePos != null) sp.setIdlePos(state.origIdlePos);
            if (state.origSleepPos != null) sp.setSleepPos(state.origSleepPos);
            maid.clearRestriction();   // v79.62.2 清 restrict — 防远处任务找不到目标
        }
        releasePreload(world, state);
        state.adapted = false;
        state.origWorkPos = null;
        state.origIdlePos = null;
        state.origSleepPos = null;
        state.adaptedCX = Integer.MIN_VALUE;
        state.adaptedCZ = Integer.MIN_VALUE;
    }
}

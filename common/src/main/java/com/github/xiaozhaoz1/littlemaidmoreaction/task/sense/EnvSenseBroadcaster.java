package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.*;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;

/**
 * 环境感知广播器 (v63) — 全局扫描 + 信号分发。
 *
 * <p>每 200 tick（可配置）对服务端做一次全局扫描，
 * 对每个开启环境感知的女仆做边沿检测，将命中信号分发给声明了对应需求的被动任务 Pipeline。
 *
 * <p>v72: 信号泛化 — 对外分发统一为 String 信号 id (event:/env: 前缀, 见 {@link Signals})。
 * 新增事件信号入口 {@link #emit}, 瞬态队列在广播末尾统一分发。
 *
 * <p>v79.3: 边沿检测委托 {@link EnvEdgeDetector} (纯 JVM 核心); D1: dispatch validate
 * pass 作用域缓存 (每管线一次, 非每信号×每管线); D3: 广播 pass 墙钟预算
 * ({@link EnvSenseBudget} — 超限跳过快照刷新, 边沿只延后不误报)。
 *
 * <h3>生命周期（闭环）</h3>
 * 内存 Map（非 NBT，不跨 session）；女仆卸载时
 * {@code LittleMaidMoreActionExtension.ServerEvents.onEntityLeaveLevel}
 * 调 {@link #onMaidUnload} 清除 (v79.3: +ScanScheduler.cancelFor 扫描任务清理)。
 */
public final class EnvSenseBroadcaster {

    /** entityId → 上次快照（边沿检测基线） */
    private static final Map<Integer, EnvSnapshot> PREV_SNAPSHOTS = new HashMap<>();
    /** entityId → 上次结构探测 gameTime（低频独立通道） */
    private static final Map<Integer, Long> STRUCT_LAST = new HashMap<>();
    /** entityId → 上轮探测到的结构信号 (v72: String 信号 id) */
    private static final Map<Integer, Set<String>> STRUCT_FOUND = new HashMap<>();

    private EnvSenseBroadcaster() {}

    // ── 公共入口 ──

    /**
     * 广播入口 — 由 TaskTickHandler 每 200 tick 调用一次。
     * 对 level 中所有女仆做边沿检测 + 信号分发。
     * 末尾统一分发瞬态事件信号 (不受 {@code ENVSENSE_ENABLED} 门控)。
     */
    public static void broadcast(ServerLevel level) {
        // v79.3 D3: 单次广播 pass 墙钟预算 (多女仆叠加防冻)
        EnvSenseBudget.Pass pass = EnvSenseBudget.begin(System.nanoTime(), EnvSenseBudget.DEFAULT_MAX_NANOS);
        // v79.3 D1: needsSignals pass 作用域缓存 (每管线一次, 非每信号×每管线)
        Map<String, Set<String>> needsCache = new HashMap<>();
        if (PassiveTaskConfig.ENVSENSE_ENABLED.get()) {
            long now = level.getGameTime();

            for (var e : level.getAllEntities()) {
                if (!(e instanceof EntityMaid maid)) continue;
                if (!maid.isAlive()) continue;

                // master 开关
                if (!maid.getPersistentData().getBoolean(TaskKeys.ENVSENSE_ENABLED)) continue;

                // 玩家门控
                int gateRadius = PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS.get();
                if (gateRadius > 0
                        && !level.hasNearbyAlivePlayer(maid.getX(), maid.getY(), maid.getZ(), gateRadius)) {
                    continue;
                }

                // v79.3 D3: 预算耗尽 → 停止本轮刷新 (边沿只延后不误报 — 旧快照保留下轮对比)
                if (pass.exhausted(System.nanoTime())) break;

                // 读取世界状态（轻量 — 所有女仆共享同一个 WorldInfo 快照合并）
                EnvSnapshot.WorldInfo world = EnvScanner.readWorld(level, maid.blockPosition());
                int radius = maid.hasRestriction()
                        ? Math.max(4, (int) maid.getRestrictRadius())
                        : PassiveTaskConfig.ENV_DEFAULT_RADIUS.get();
                Map<String, List<net.minecraft.world.entity.LivingEntity>> entities =
                        EnvScanner.scanEntities(level, maid, radius, PassiveTaskConfig.ENV_MAX_HITS.get());

                EnvSnapshot snap = new EnvSnapshot(now, Map.of(), entities, world, List.of());
                EnvSnapshot prev = PREV_SNAPSHOTS.get(maid.getId());

                // 边沿检测
                Set<EnvSignal> signals = detectSignals(prev, snap, level, maid, now);
                PREV_SNAPSHOTS.put(maid.getId(), snap);

                // 分发
                if (!signals.isEmpty()) {
                    dispatch(maid, snap, signals, needsCache);
                }
            }
        }
        // v72: 事件信号统一分发 — 不受 ENVSENSE_ENABLED 门控 (事件信号与扫描开关无关)
        flushPending(needsCache);
    }

    /** 获取女仆最新快照（AI Context / 调试用） */
    @Nullable
    public static EnvSnapshot getSnapshot(EntityMaid maid) {
        return PREV_SNAPSHOTS.get(maid.getId());
    }

    /** 女仆卸载清理 (v79.3: +ScanScheduler.cancelFor — 任务句柄悬空烧预算, 必堵口) */
    public static void onMaidUnload(int entityId) {
        PREV_SNAPSHOTS.remove(entityId);
        STRUCT_LAST.remove(entityId);
        STRUCT_FOUND.remove(entityId);
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.ScanScheduler.cancelFor(entityId);
    }

    // ── 边沿检测 ──

    private static Set<EnvSignal> detectSignals(@Nullable EnvSnapshot prev, EnvSnapshot snap,
                                                  ServerLevel level, EntityMaid maid, long now) {
        // v79.3: 边沿检测委托纯逻辑核心 (EnvEdgeDetector, 零 MC 依赖可 JVM 测)
        Set<EnvSignal> signals = EnvEdgeDetector.detect(
                prev != null ? prev.world() : null, snap.world(),
                presenceOf(prev), presenceOf(snap),
                new EnvEdgeDetector.EnvConfig(
                        PassiveTaskConfig.ENV_COLD_THRESHOLD.get().floatValue(),
                        PassiveTaskConfig.ENV_HOT_THRESHOLD.get().floatValue(),
                        PassiveTaskConfig.ENV_DARKNESS_THRESHOLD.get()));

        // ── 结构 (低频独立通道, 默认 24000 tick = 1 MC 天) — MC 绑定留广播器 ──
        if (PassiveTaskConfig.ENV_STRUCTURE_ENABLED.get()) {
            detectStructureSignals(signals, level, maid, now);
        }

        return signals;
    }

    /** 快照 → 实体在场状态 (纯派生, 供 EnvEdgeDetector) */
    private static EnvEdgeDetector.EntityPresence presenceOf(@Nullable EnvSnapshot snap) {
        if (snap == null) return EnvEdgeDetector.EntityPresence.NONE;
        return new EnvEdgeDetector.EntityPresence(
                !snap.entities(EnvScanner.CAT_MONSTER).isEmpty(),
                !snap.entities(EnvScanner.CAT_FRIENDLY).isEmpty(),
                !snap.entities(EnvScanner.CAT_MAID).isEmpty());
    }

    private static void detectStructureSignals(Set<EnvSignal> out, ServerLevel level,
                                                EntityMaid maid, long now) {
        int structInterval = PassiveTaskConfig.ENV_STRUCTURE_INTERVAL.get();
        long last = STRUCT_LAST.getOrDefault(maid.getId(), 0L);
        if (last != 0 && now - last < structInterval) return;
        STRUCT_LAST.put(maid.getId(), now);

        int id = maid.getId();
        Set<EnvSignal> found = new HashSet<>();
        BlockPos center = maid.blockPosition();
        int radius = PassiveTaskConfig.ENV_STRUCTURE_RADIUS.get();

        checkStructure(found, EnvSignal.VILLAGE_NEARBY,
                net.minecraft.tags.StructureTags.VILLAGE, level, center, radius);
        checkStructure(found, EnvSignal.MINESHAFT_NEARBY,
                net.minecraft.tags.StructureTags.MINESHAFT, level, center, radius);
        checkStructureOutpost(found, level, center, radius);

        // v72: 结构信号历史以 String 信号 id 存储 (与分发面统一)
        Set<String> prevFound = STRUCT_FOUND.getOrDefault(id, Set.of());
        for (EnvSignal sig : found) {
            if (!prevFound.contains(Signals.envOf(sig))) out.add(sig);
        }
        STRUCT_FOUND.put(id, found.stream().map(Signals::envOf).collect(java.util.stream.Collectors.toSet()));
    }

    private static void checkStructure(Set<EnvSignal> out, EnvSignal signal,
                                        net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag,
                                        ServerLevel level, BlockPos center, int radius) {
        try {
            BlockPos pos = level.findNearestMapStructure(tag, center, radius, false);
            if (pos != null) out.add(signal);
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.error("[EnvSense] 结构探测异常: {}", signal, ex);
        }
    }

    private static void checkStructureOutpost(Set<EnvSignal> out, ServerLevel level,
                                               BlockPos center, int radius) {
        var tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.STRUCTURE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        LittleMaidMoreAction.MOD_ID, "pillager_outpost"));
        checkStructure(out, EnvSignal.OUTPOST_NEARBY, tag, level, center, radius);
    }

    // ── 分发 ──

    private static void dispatch(EntityMaid maid, EnvSnapshot snap, Set<EnvSignal> signals,
                                 Map<String, Set<String>> needsCache) {
        for (EnvSignal sig : signals) {
            dispatchToPipelines(maid, Signals.envOf(sig), snap, needsCache);
        }
    }

    /**
     * 信号分发到被动管线 — dispatch (环境信号) 与 flushPending (事件信号) 共用。
     * 仅声明了该信号需求的管线收到回调; 管线自身 TaskToggle 检查先行。
     * v79.3 D1: needsSignals pass 作用域缓存 (每管线一次; 异常 → 空集哨兵, 只 log 一次)。
     */
    private static void dispatchToPipelines(EntityMaid maid, String signalId, @Nullable EnvSnapshot snap,
                                            Map<String, Set<String>> needsCache) {
        for (TaskRegistry.TaskHandler h : TaskRegistry.passiveTasksList()) {
            TaskPipeline pipeline = h.pipeline();
            if (!TaskToggle.isEnabled(h.taskType()) || !TaskToggle.isEnabledFor(maid, h.taskType())) {
                continue;
            }
            // v79.3 D1: pass 作用域缓存 — 每管线一次 validate (原每信号×每管线)
            Set<String> needs = needsCache.computeIfAbsent(h.taskType(), tt -> {
                try {
                    var result = pipeline.validate(
                            (ServerLevel) maid.level(), maid,
                            new PipelineContext("", 0, ""));
                    return result.needsSignals();
                } catch (Exception ex) {
                    LittleMaidMoreAction.LOGGER.error("[EnvSense] validate 异常: {}", h.taskType(), ex);
                    return Set.of();   // 异常哨兵: 空集 = 本轮不分发
                }
            });
            if (needs.isEmpty() || !needs.contains(signalId)) continue;

            try {
                pipeline.onSignal(maid, snap, signalId);
            } catch (Exception ex) {
                LittleMaidMoreAction.LOGGER.error("[EnvSense] onSignal 异常: {} signal={}",
                        h.taskType(), signalId, ex);
            }
        }
    }

    // ── 事件信号入口 (v72) ──

    /** 待分发信号 — 瞬态队列元素; ctx 由 Phase 3 条件评估消费 (当前透传不消费) */
    private record PendingSignal(EntityMaid maid, String signalId) {}

    /** 事件信号瞬态队列 — 仅 tick 线程访问 (broadcast 末尾统一 flush, 无需同步) */
    private static final Queue<PendingSignal> PENDING = new ArrayDeque<>();

    /**
     * v72: 事件信号入口 — 由外部事件回调调用。
     *
     * <p>不入队立即分发, 延迟到 {@link #broadcast} 末尾统一 flush —
     * 避免事件回调期间修改被动任务状态造成并发遍历问题。
     *
     * <p>不适用 {@code ENVSENSE_ENABLED}/玩家门控 (事件信号与扫描开关无关);
     * 被动管线自身的 TaskToggle 检查在 {@link #dispatchToPipelines} 内保留。
     *
     * <p>v72 Phase 4: flush 时按前缀路由 — {@code event:} → {@code TaskScreeningService.fire}
     * (无 cancel 消费 — <b>需取消的事件桥必须直连 fire 拿返回值</b>, 见 event/bridge/*);
     * {@code env:} → 被动管线分发。
     *
     * @param ctx 规则上下文 (事件桥直连 fire 时传递; 队列路径透传给筛选服务)
     */
    public static void emit(ServerLevel level, EntityMaid maid, String signalId) {
        if (maid == null || !maid.isAlive()) return;
        PENDING.add(new PendingSignal(maid, signalId));
    }

    /** 分发瞬态队列 — 按前缀路由: event: → 筛选服务 (同步入口); 其余 (env:) → 管线分发 */
    private static void flushPending(Map<String, Set<String>> needsCache) {
        PendingSignal p;
        while ((p = PENDING.poll()) != null) {
            // env 信号: 无快照的女仆跳过 (emit 不触发扫描)
            EnvSnapshot snap = PREV_SNAPSHOTS.get(p.maid().getId());
            if (snap == null) continue;
            dispatchToPipelines(p.maid(), p.signalId(), snap, needsCache);
        }
    }
}

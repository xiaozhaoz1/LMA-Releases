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
 * 环境感知广播器 (v63, v79.47: 节日 stateless 状态广播) — 全局扫描 + 信号分发。
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
 * <p>v79.47: 节日 stateless 状态广播 — 每轮查 {@link FestivalTable} (LocalDate.now() 现实日期)
 * 非空即 FESTIVAL_ENTER 全女仆 emit (无日期对比基线 — 女仆错过广播后上线首收即触发;
 * 消费端 per-maid 当天首收去重)。
 *
 * <p>v79.60: 结构信号移出 per-maid 通道 → 独立 per-player 段 (玩家为中心扫 1 次,
 * 发主人女仆选 1 个) — 见 {@link StructureSense}。
 *
 * <h3>生命周期（闭环）</h3>
 * 内存 Map（非 NBT，不跨 session）；女仆卸载时
 * {@code LittleMaidMoreActionExtension.ServerEvents.onEntityLeaveLevel}
 * 调 {@link #onMaidUnload} 清除 (v79.3: +ScanScheduler.cancelFor 扫描任务清理);
 * 结构 per-player 缓存由 {@link StructureSense#sweep} 懒清理。
 */
public final class EnvSenseBroadcaster {

    /** entityId → 上次快照（边沿检测基线） */
    private static final Map<Integer, EnvSnapshot> PREV_SNAPSHOTS = new HashMap<>();

    private EnvSenseBroadcaster() {}

    // ── 公共入口 ──

    /**
     * 广播入口 — 由 TaskTickHandler 每 200 tick 调用一次。
     * 对 level 中所有女仆做边沿检测 + 信号分发 + 结构 per-player 检测。
     * 末尾统一分发瞬态事件信号 (不受 {@code ENVSENSE_ENABLED} 门控)。
     */
    public static void broadcast(ServerLevel level) {
        // 单次广播 pass 墙钟预算 (多女仆叠加防冻)
        EnvSenseBudget.Pass pass = EnvSenseBudget.begin(System.nanoTime(), EnvSenseBudget.DEFAULT_MAX_NANOS);
        // needsSignals pass 作用域缓存 (每管线一次, 非每信号×每管线)
        Map<String, Set<String>> needsCache = new HashMap<>();
        if (PassiveTaskConfig.ENVSENSE_ENABLED.get()) {
            long now = level.getGameTime();

            for (var e : level.getAllEntities()) {
                if (!(e instanceof EntityMaid maid)) continue;
                if (!maid.isAlive()) continue;

                // per-maid 环境感知开关 (v79.47 解锁, 默认开 — 无键视为开; 显式 false = 关, GUI 可切)
                if (maid.getPersistentData().contains(TaskKeys.ENVSENSE_ENABLED)
                        && !maid.getPersistentData().getBoolean(TaskKeys.ENVSENSE_ENABLED)) continue;

                // 玩家门控
                int gateRadius = PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS.get();
                if (gateRadius > 0
                        && !level.hasNearbyAlivePlayer(maid.getX(), maid.getY(), maid.getZ(), gateRadius)) {
                    continue;
                }

                // 预算耗尽 → 停止本轮刷新 (边沿只延后不误报 — 旧快照保留下轮对比)
                if (pass.exhausted(System.nanoTime())) break;

                // 读取世界状态（轻量 — 所有女仆共享同一个 WorldInfo 快照合并）
                EnvSnapshot.WorldInfo world = EnvScanner.readWorld(level, maid.blockPosition());
                int radius = maid.hasRestriction()
                        ? Math.max(4, (int) maid.getRestrictRadius())
                        : PassiveTaskConfig.ENV_DEFAULT_RADIUS.get();
                Map<String, List<net.minecraft.world.entity.LivingEntity>> entities =
                        EnvScanner.scanEntities(level, maid, radius, PassiveTaskConfig.ENV_MAX_HITS.get());

                EnvSnapshot snap = new EnvSnapshot(now, entities, world);
                EnvSnapshot prev = PREV_SNAPSHOTS.get(maid.getId());

                // 边沿检测
                Set<EnvSignal> signals = detectSignals(prev, snap, level, maid, now);
                // v79.58 F-1 (审查): 哈气运行中不更新基线 — 边沿被哈气互斥挡 (submitPassive 拒)
                // 后保留到下轮, 哈气结束重新检测 → 重发 (原无条件更新 → 边沿永久丢失:
                // TempAdapt 冷地永不取暖 / TorchLight 错过整夜); 分发照走 (纯信号管线不受
                // 互斥影响); 边沿最多延迟 200t (用户裁定 A 方案)
                boolean haqiActive = TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData()
                        .getString(TaskKeys.passiveKey("haqi")));
                if (!haqiActive) {
                    PREV_SNAPSHOTS.put(maid.getId(), snap);
                }

                // 分发
                if (!signals.isEmpty()) {
                    dispatch(maid, snap, signals, needsCache);
                }
            }
            // ── 结构信号 (v79.60: per-player 独立通道 — 玩家为中心扫 1 次, 发主人女仆选 1 个) ──
            StructureSense.sweep(level);
            for (var player : level.players()) {
                StructureSense.detect(level, player);
            }
        }
        // 节日 stateless 状态广播 (现实日期口径, 与 env 扫描开关无关; 消费端当天首收去重)
        detectFestivalSignal(level);
        // 事件信号统一分发 — 不受 ENVSENSE_ENABLED 门控 (事件信号与扫描开关无关)
        flushPending(needsCache);
    }

    /** 获取女仆最新快照（AI Context / 调试用） */
    @Nullable
    public static EnvSnapshot getSnapshot(EntityMaid maid) {
        return PREV_SNAPSHOTS.get(maid.getId());
    }

    /** 女仆卸载清理 (ScanScheduler.cancelFor — 任务句柄悬空烧预算, 必堵口; 结构缓存为 player 维度由 sweep 管) */
    public static void onMaidUnload(int entityId) {
        PREV_SNAPSHOTS.remove(entityId);
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.ScanScheduler.cancelFor(entityId);
    }

    // ── 边沿检测 ──

    private static Set<EnvSignal> detectSignals(@Nullable EnvSnapshot prev, EnvSnapshot snap,
                                                  ServerLevel level, EntityMaid maid, long now) {
        // 边沿检测委托纯逻辑核心 (EnvEdgeDetector, 零 MC 依赖可 JVM 测)
        // v79.60: 结构检测不在此通道 — broadcast 独立 per-player 段 (StructureSense.detect)
        return EnvEdgeDetector.detect(
                prev != null ? prev.world() : null, snap.world(),
                presenceOf(prev), presenceOf(snap),
                new EnvEdgeDetector.EnvConfig(
                        PassiveTaskConfig.ENV_COLD_THRESHOLD.get().floatValue(),
                        PassiveTaskConfig.ENV_HOT_THRESHOLD.get().floatValue(),
                        PassiveTaskConfig.ENV_DARKNESS_THRESHOLD.get()));
    }

    /** 快照 → 实体在场状态 (纯派生, 供 EnvEdgeDetector) */
    private static EnvEdgeDetector.EntityPresence presenceOf(@Nullable EnvSnapshot snap) {
        if (snap == null) return EnvEdgeDetector.EntityPresence.NONE;
        return new EnvEdgeDetector.EntityPresence(
                !snap.entities(EnvScanner.CAT_FRIENDLY).isEmpty(),
                !snap.entities(EnvScanner.CAT_MAID).isEmpty());
    }

    // ── 节日 stateless 状态广播 (v79.47) ──

    /**
     * 节日状态广播 — 每轮查表 (现实日期 LocalDate.now()) 非空 → FESTIVAL_ENTER 全女仆 emit。
     * 无日期对比基线 (stateless): 女仆错过广播后上线/回主人旁 → 下一轮首收即触发;
     * 重复 emit 由消费端 (FestivalPipeline) per-maid 当天首收去重兜住。
     */
    private static void detectFestivalSignal(ServerLevel level) {
        if (FestivalTable.lookup(java.time.LocalDate.now()) == null) return;
        for (var e : level.getAllEntities()) {
            if (e instanceof EntityMaid maid && maid.isAlive()) {
                emit(level, maid, Signals.envOf(EnvSignal.FESTIVAL_ENTER));
            }
        }
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
     * needsSignals pass 作用域缓存 (每管线一次; 异常 → 空集哨兵, 只 log 一次)。
     */
    private static void dispatchToPipelines(EntityMaid maid, String signalId, @Nullable EnvSnapshot snap,
                                            Map<String, Set<String>> needsCache) {
        for (TaskRegistry.TaskHandler h : TaskRegistry.passiveTasksList()) {
            TaskPipeline pipeline = h.pipeline();
            if (!TaskToggle.isEnabled(h.taskType()) || !TaskToggle.isEnabledFor(maid, h.taskType())) {
                continue;
            }
            // pass 作用域缓存 — 每管线一次 validate (原每信号×每管线)
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
            // v79.58: 通配订阅支持 (结构动态信号 env:structure:* — 管线 validate 声明前缀 + "*")
            boolean matches = !needs.isEmpty() && needs.contains(signalId);
            if (!matches) {
                for (String n : needs) {
                    if (n.endsWith("*") && signalId.startsWith(n.substring(0, n.length() - 1))) {
                        matches = true;
                        break;
                    }
                }
            }
            if (!matches) continue;

            try {
                // 信号维度拆分 — 未实现 TaskSignalListener 的管线忽略信号
                if (pipeline instanceof com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener l) {
                    l.onSignal(maid, snap, signalId);
                }
            } catch (Exception ex) {
                LittleMaidMoreAction.LOGGER.error("[EnvSense] onSignal 异常: {} signal={}",
                        h.taskType(), signalId, ex);
            }
        }
    }

    // ── 事件信号入口 ──

    /** 待分发信号 — 瞬态队列元素 */
    private record PendingSignal(EntityMaid maid, String signalId) {}

    /** 事件信号瞬态队列 — 仅 tick 线程访问 (broadcast 末尾统一 flush, 无需同步) */
    private static final Queue<PendingSignal> PENDING = new ArrayDeque<>();

    /**
     * 事件信号入口 — 由外部事件回调调用。
     *
     * <p>不入队立即分发, 延迟到 {@link #broadcast} 末尾统一 flush —
     * 避免事件回调期间修改被动任务状态造成并发遍历问题。
     *
     * <p>不适用 {@code ENVSENSE_ENABLED}/玩家门控 (事件信号与扫描开关无关);
     * 被动管线自身的 TaskToggle 检查在 {@link #dispatchToPipelines} 内保留。
     *
     * <p>flush 时按前缀路由 — {@code event:} → {@code TaskScreeningService.fire}
     * (无 cancel 消费 — <b>需取消的事件桥必须直连 fire 拿返回值</b>, 见 event/bridge/*);
     * {@code env:} → 被动管线分发。
     */
    public static void emit(ServerLevel level, EntityMaid maid, String signalId) {
        if (maid == null || !maid.isAlive()) return;
        PENDING.add(new PendingSignal(maid, signalId));
    }

    /** 分发瞬态队列 — 按前缀路由: event: → 筛选服务 (同步入口); 其余 (env:) → 管线分发 */
    private static void flushPending(Map<String, Set<String>> needsCache) {
        PendingSignal p;
        while ((p = PENDING.poll()) != null) {
            // 事件注入信号不依赖扫描快照 — 快照由 per-maid 门 (L64) 饿死时
            // 队列信号曾全被丢弃 (死链); 无快照也分发 (snap=null, 管线 onSignal 自行容错)
            dispatchToPipelines(p.maid(), p.signalId(), PREV_SNAPSHOTS.get(p.maid().getId()), needsCache);
        }
    }
}

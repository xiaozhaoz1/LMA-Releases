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
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.EntityScanCache;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.EntityScanner;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StructureScanCache;

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
    /** 混合分流阈值 (v79.6x 用户裁定): eligible 女仆 ≤2 直扫, ≥3 预热+区块缓存共享 (成本模型: 预热 9 区块全高扫描 ≈ 5-10 次近身扫描) */
    private static final int MIN_MAIDS_FOR_CACHE = 3;

    /** 分流判定纯函数 (JVM 可测): eligible 女仆 ≥3 走缓存, ≤2 直扫 */
    static boolean shouldUseCache(int eligibleCount) {
        return eligibleCount >= MIN_MAIDS_FOR_CACHE;
    }

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


            // v79.6x 混合分流 (用户裁定): 先收集 eligible 女仆 — ≤2 直扫 (家庭场景, 缓存预热反而更贵),
            // ≥3 预热主人区块 + 区块缓存共享 (多女仆场景)
            int gateRadius = PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS.get();
            List<EntityMaid> eligible = new ArrayList<>();
            for (var e : level.getAllEntities()) {
                if (!(e instanceof EntityMaid maid)) continue;
                if (!maid.isAlive()) continue;
                // per-maid 环境感知开关 (v79.47 解锁, 默认开 — 无键视为开; 显式 false = 关, GUI 可切)
                if (maid.getPersistentData().contains(TaskKeys.ENVSENSE_ENABLED)
                        && !maid.getPersistentData().getBoolean(TaskKeys.ENVSENSE_ENABLED)) continue;
                // 玩家门控
                if (gateRadius > 0
                        && !level.hasNearbyAlivePlayer(maid.getX(), maid.getY(), maid.getZ(), gateRadius)) {
                    continue;
                }
                eligible.add(maid);
            }
            boolean useCache = shouldUseCache(eligible.size());
            if (useCache) {
                // 预热: 主人区块先入缓存 (独立 2ms 预算不挤占广播 pass 8ms)
                long preheatDeadline = System.nanoTime() + EntityScanCache.SCAN_BUDGET_NANOS;
                for (var player : level.players()) {
                    EntityScanCache.GLOBAL.warm(level, player.blockPosition(),
                            PassiveTaskConfig.ENV_DEFAULT_RADIUS.get(), now, preheatDeadline);
                }
            }

            int radius = PassiveTaskConfig.ENV_DEFAULT_RADIUS.get();   // v79.6x 统一半径 (缓存 key 不带半径)
            for (EntityMaid maid : eligible) {
                // 预算耗尽 → 停止本轮刷新 (边沿只延后不误报 — 旧快照保留下轮对比)
                if (pass.exhausted(System.nanoTime())) break;

                // 读取世界状态（轻量 — 所有女仆共享同一个 WorldInfo 快照合并）
                EnvSnapshot.WorldInfo world = EnvScanner.readWorld(level, maid.blockPosition());
                Map<String, List<net.minecraft.world.entity.LivingEntity>> entities = useCache
                        ? EntityScanCache.GLOBAL.scanAround(level, maid.blockPosition(), radius, maid,
                                PassiveTaskConfig.ENV_MAX_HITS.get(), now, pass.deadlineNanos())
                        : EntityScanner.scanEntities(level, maid, radius, PassiveTaskConfig.ENV_MAX_HITS.get());
                EnvSnapshot snap = new EnvSnapshot(now, entities, world);
                EnvSnapshot prev = PREV_SNAPSHOTS.get(maid.getId());

                // 边沿检测
                Set<EnvSignal> signals = detectSignals(prev, snap, level, maid, now);
                // v79.58 F-1 (审查): 哈气运行中不更新基线 — 边沿被哈气互斥挡 (submitPassive 拒)
                // 后保留到下轮, 哈气结束重新检测 → 重发 (原无条件更新 → 边沿永久丢失:
                // TempAdapt 冷地永不取暖 / TorchLight 错过整夜); 分发照走 (纯信号管线不受
                // 互斥影响); 边沿最多延迟 200t (用户裁定 A 方案)
                // v79.61x S1-1.6-1 (用户裁定增强): 哈气期间连分发一起冻结 — 边沿信号
                // 整体不消费 (原照常 dispatch → onSignal 里 submitPassive 被拒 = 无效调用
                // + DEBUG 噪音); 哈气结束下轮重检测重放; 结构/节日事件信号走 flushPending
                // 独立通道不受影响
                boolean haqiActive = TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData()
                        .getString(TaskKeys.passiveKey("haqi")));
                if (!haqiActive) {
                    PREV_SNAPSHOTS.put(maid.getId(), snap);

                    // 分发
                    if (!signals.isEmpty()) {
                        dispatch(maid, snap, signals, needsCache);
                    }
                }
            }
            // ── 结构信号 (v79.60 per-player 独立通道; v79.6x 预算 P: 2ms 墙钟预算跨玩家共享) ──
            StructureSense.sweep(level);
            long structureDeadline = System.nanoTime() + StructureScanCache.SCAN_BUDGET_NANOS;
            for (var player : level.players()) {
                StructureSense.detect(level, player, structureDeadline);
            }
        }
        // 节日 stateless 状态广播 (现实日期口径, 与 env 扫描开关无关; 消费端当天首收去重)
        detectFestivalSignal(level);
        // v79.62.1 稀有群系 stateless 广播 (当前在稀有群系即发; 消费端每群系去重)
        detectRareBiomeSignal(level);
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
        EntityScanCache.GLOBAL.onEntityRemoved(entityId);   // v79.6x per-查询漂移缓存闭环
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
                !snap.entities(EntityScanner.CAT_FRIENDLY).isEmpty(),
                !snap.entities(EntityScanner.CAT_MAID).isEmpty());
    }

    // ── 节日 stateless 状态广播 (v79.47) ──

    /**
     * 节日状态广播 — 每轮查表 (现实日期 LocalDate.now()) 非空 → FESTIVAL_ENTER 全女仆 emit。
     * 无日期对比基线 (stateless): 女仆错过广播后上线/回主人旁 → 下一轮首收即触发;
     * 重复 emit 由消费端 (FestivalPassiveTask) per-maid 当天首收去重兜住。
     */
    private static void detectFestivalSignal(ServerLevel level) {
        if (FestivalTable.lookup(java.time.LocalDate.now()) == null) return;
        for (var e : level.getAllEntities()) {
            if (e instanceof EntityMaid maid && maid.isAlive()) {
                emit(level, maid, Signals.envOf(EnvSignal.FESTIVAL_ENTER));
            }
        }
    }

    /**
     * 稀有群系 per-player 检测 (v79.62.1 用户裁定: 跟结构一致 — 玩家为中心扫 1 次, 发主人女仆).
     * 女仆跟随玩家, 通报给玩家看 → 查玩家所在 biome 即可, 无需每女仆查 (省 O(女仆数)).
     * 每玩家节流 (interval) + 门控 (无主人女仆不扫) + 信号半径选 1 女仆.
     * 重复 emit 由消费端 (RareBiomePassiveTask) per-maid 每群系去重兜住.
     */
    private static final Map<UUID, Long> RARE_BIOME_LAST = new HashMap<>();

    private static void detectRareBiomeSignal(ServerLevel level) {
        if (!PassiveTaskConfig.ENV_RARE_BIOME_ENABLED.get()) return;
        long now = level.getGameTime();
        int interval = PassiveTaskConfig.ENV_RARE_BIOME_INTERVAL.get();
        int radius = PassiveTaskConfig.ENV_RARE_BIOME_SIGNAL_RADIUS.get();
        for (var player : level.players()) {
            UUID pid = player.getUUID();
            long last = RARE_BIOME_LAST.getOrDefault(pid, 0L);
            if (last != 0 && now >= last && now - last < interval) continue;   // 时钟回退守卫
            RARE_BIOME_LAST.put(pid, now);
            // 门控: 无附近主人女仆不扫 (信号必须有接收者)
            EntityMaid maid = pickOwnerMaid(level, player, radius);
            if (maid == null) continue;
            // 查玩家所在群系 (女仆跟随玩家 → 等价; 扫 1 次)
            String biomeId = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader
                    .getBiome(level, player.blockPosition());
            if (com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask
                    .isRareBiome(biomeId)) {
                emit(level, maid, Signals.envOf(EnvSignal.RARE_BIOME));
            }
        }
    }

    /** 玩家附近的主人女仆中选 1 个 (随机/最近 — 复用结构感知语义; 无 → null) */
    private static EntityMaid pickOwnerMaid(ServerLevel level, net.minecraft.server.level.ServerPlayer player, int radius) {
        double r2 = (double) radius * radius;
        java.util.UUID owner = player.getUUID();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(radius);
        java.util.List<EntityMaid> near = new java.util.ArrayList<>();
        for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class, box,
                m -> m.isAlive() && owner.equals(m.getOwnerUUID()))) {
            if (maid.distanceToSqr(player) <= r2) near.add(maid);
        }
        if (near.isEmpty()) return null;
        return near.get(level.random.nextInt(near.size()));
    }

    // ── 分发 ──

    private static void dispatch(EntityMaid maid, EnvSnapshot snap, Set<EnvSignal> signals,
                                 Map<String, Set<String>> needsCache) {
        for (EnvSignal sig : signals) {
            dispatchToPipelines(maid, Signals.envOf(sig), snap, needsCache);
        }
    }

    /**
     * 信号分发到被动 — dispatch (环境信号) 与 flushPending (事件信号) 共用。
     * 仅声明了该信号需求的条目收到回调; 条目自身 TaskToggle 检查先行。
     * needsSignals pass 作用域缓存 (每条目一次; 异常 → 空集哨兵, 只 log 一次)。
     *
     * <p>v79.61x 脱管线: 无 pipeline 占位条目 (纯触发型) 走 {@link PassiveDispatcher} 通道 —
     * needsSignals 声明面 + exec 四道闸 (注册/开关/ha qi 覆盖/冷却), 不经管线 validate/onSignal。
     */
    private static void dispatchToPipelines(EntityMaid maid, String signalId, @Nullable EnvSnapshot snap,
                                            Map<String, Set<String>> needsCache) {
        for (TaskRegistry.TaskHandler h : TaskRegistry.passiveTasksList()) {
            if (!TaskToggle.isEnabled(h.taskType())) {
                continue;
            }
            if (h.pipeline() == null) {
                // 纯触发型 — PassiveDispatcher 通道
                com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveTask pt =
                        com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveDispatcher.get(h.taskType());
                if (pt == null) continue;
                Set<String> needs = needsCache.computeIfAbsent(h.taskType(), tt -> pt.needsSignals());
                if (!signalMatches(signalId, needs)) continue;
                com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveDispatcher.exec(
                        maid, h.taskType(), signalId);
                continue;
            }
            TaskPipeline pipeline = h.pipeline();
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
            if (!signalMatches(signalId, needs)) continue;

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

    /** 信号匹配 — 精确 + 通配前缀 (v79.58: 结构动态信号 env:structure:* 通配订阅) */
    private static boolean signalMatches(String signalId, Set<String> needs) {
        if (needs.isEmpty()) return false;
        if (needs.contains(signalId)) return true;
        for (String n : needs) {
            if (n.endsWith("*") && signalId.startsWith(n.substring(0, n.length() - 1))) {
                return true;
            }
        }
        return false;
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
     * <p>flush 时统一走 {@link #dispatchToPipelines} (v77.4 后无 event: 前缀路由 —
     * 事件链直调 TaskDispatcher, 本队列只剩 env: 动态结构信号)。
     */
    public static void emit(ServerLevel level, EntityMaid maid, String signalId) {

        if (maid == null || !maid.isAlive()) return;
        PENDING.add(new PendingSignal(maid, signalId));
    }

    /** 分发瞬态队列 — 统一走被动管线分发 (v77.4 后无 event: 前缀路由; 无快照也分发, 管线自行容错) */
    private static void flushPending(Map<String, Set<String>> needsCache) {
        PendingSignal p;
        while ((p = PENDING.poll()) != null) {
            // 事件注入信号不依赖扫描快照 — 快照由 per-maid 门 (L64) 饿死时
            // 队列信号曾全被丢弃 (死链); 无快照也分发 (snap=null, 管线 onSignal 自行容错)
            dispatchToPipelines(p.maid(), p.signalId(), PREV_SNAPSHOTS.get(p.maid().getId()), needsCache);
        }
    }
}

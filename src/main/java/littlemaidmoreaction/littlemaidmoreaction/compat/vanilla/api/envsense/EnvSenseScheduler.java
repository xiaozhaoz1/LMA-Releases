package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense.EnvSenseRegistry.BlockSensor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense.EnvSenseRegistry.EntitySensor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense.EnvSenseRegistry.StructureSensor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense.EnvSenseRegistry.WorldSensor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.sense.EnvScanner;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境感知调度器 (v37) — 每女仆每 200 tick（可配置）最多一次合并扫描。
 *
 * <p>挂钩点: {@code TlmEventAdapter.onMaidTick} 每 tick 调用 {@link #tick}，
 * 内部自节流。无感知器注册时开销 = 一次布尔判断（零扫描）。
 *
 * <h3>v37.2 玩家门控</h3>
 * 仅玩家 {@code player_gate_radius}（默认 64 格，0=关闭门控）范围内的女仆
 * 参与一切感知 — 远离玩家的女仆（挂机农场等）零感知开销。
 *
 * <h3>双通道分发</h3>
 * <ol>
 *   <li>命中的感知器回调 {@code SensorCallback.onScan}（逐个 try/catch 隔离）</li>
 *   <li>任一命中 → {@code RuleEngine.handleEvent("lma_env_scan", ctx)}，
 *       ctx attribute {@code env_hits} = 逗号分隔命中 id</li>
 * </ol>
 *
 * <h3>缓存生命周期（闭环）</h3>
 * 内存 Map（非 NBT，不跨 session）；女仆卸载时由
 * {@code LittleMaidMoreActionExtension.ServerEvents.onEntityLeaveLevel}
 * 调 {@link #onMaidUnload} 清除。
 */
public final class EnvSenseScheduler {

    /** entityId → 上次扫描 gameTime */
    private static final Map<Integer, Long> LAST_SCAN = new ConcurrentHashMap<>();
    /** entityId → 最新快照 */
    private static final Map<Integer, EnvSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    /** v37.2: entityId → 上次结构探测 gameTime（独立低频计时） */
    private static final Map<Integer, Long> STRUCT_LAST = new ConcurrentHashMap<>();
    /** v37.2: entityId → 上轮探测到的结构感知器 id 集（边沿检测基线） */
    private static final Map<Integer, Set<String>> STRUCT_FOUND = new ConcurrentHashMap<>();

    private EnvSenseScheduler() {}

    /** 每 tick 入口（内部自节流至 config scan_interval_ticks） */
    public static void tick(EntityMaid maid) {
        if (!EnvSenseRegistry.hasSensors()) return;
        if (!(maid.level() instanceof ServerLevel level)) return;

        long now = level.getGameTime();
        int id = maid.getId();
        long last = LAST_SCAN.getOrDefault(id, 0L);
        int interval = MoreActionConfig.ENV_SCAN_INTERVAL.get();
        // 时间戳防护: last==0(首次) 或 last>now(异常) 时直接扫描
        if (last != 0 && last <= now && now - last < interval) return;
        LAST_SCAN.put(id, now);

        // v37.2 玩家门控: 远离玩家的女仆零感知（每周期只查一次距离）
        int gateRadius = MoreActionConfig.ENV_PLAYER_GATE_RADIUS.get();
        if (gateRadius > 0
                && !level.hasNearbyAlivePlayer(maid.getX(), maid.getY(), maid.getZ(), gateRadius)) {
            return;
        }

        // 按需过滤: 无适用感知器的女仆零扫描
        List<BlockSensor> blocks = new ArrayList<>();
        for (BlockSensor s : EnvSenseRegistry.blockSensors()) {
            if (s.appliesTo() == null || s.appliesTo().test(maid)) blocks.add(s);
        }
        List<EntitySensor> entities = new ArrayList<>();
        for (EntitySensor s : EnvSenseRegistry.entitySensors()) {
            if (s.appliesTo() == null || s.appliesTo().test(maid)) entities.add(s);
        }
        List<WorldSensor> worlds = new ArrayList<>();
        for (WorldSensor s : EnvSenseRegistry.worldSensors()) {
            if (s.appliesTo() == null || s.appliesTo().test(maid)) worlds.add(s);
        }
        List<StructureSensor> structs = new ArrayList<>();
        for (StructureSensor s : EnvSenseRegistry.structureSensors()) {
            if (s.appliesTo() == null || s.appliesTo().test(maid)) structs.add(s);
        }
        if (blocks.isEmpty() && entities.isEmpty() && worlds.isEmpty() && structs.isEmpty()) return;

        int radius = maid.hasRestriction()
                ? Math.max(4, (int) maid.getRestrictRadius())
                : MoreActionConfig.ENV_DEFAULT_RADIUS.get();
        // v37.1: 取上轮快照做 prev/now 边沿检测（首轮 prev=null）
        EnvSnapshot prev = SNAPSHOTS.get(id);
        EnvSnapshot scanned = EnvScanner.scan(level, maid, blocks, entities,
                radius, MoreActionConfig.ENV_MAX_HITS.get());

        // v37.1: 世界感知器触发判定
        List<String> triggered = new ArrayList<>();
        EnvSnapshot.WorldInfo prevWorld = prev != null ? prev.world() : null;
        for (WorldSensor s : worlds) {
            try {
                if (s.trigger().test(prevWorld, scanned.world())) triggered.add(s.id());
            } catch (Exception ex) {
                LittleMaidMoreAction.LOGGER.error("[EnvSense] 世界感知器 '{}' 触发判定异常", s.id(), ex);
            }
        }

        // v37.2: 结构低频探测通道（独立间隔，边沿触发）
        triggered.addAll(scanStructures(level, maid, structs, id, now));

        EnvSnapshot snapshot = triggered.isEmpty() ? scanned
                : new EnvSnapshot(scanned.gameTime(), scanned.blockHits(),
                        scanned.entityHits(), scanned.world(), List.copyOf(triggered));
        SNAPSHOTS.put(id, snapshot);

        // 通道1: 命中感知器回调（隔离异常）
        List<String> hitIds = new ArrayList<>();
        for (BlockSensor s : blocks) {
            if (snapshot.hit(s.id())) {
                hitIds.add(s.id());
                dispatch(s.id(), s.callback(), maid, snapshot);
            }
        }
        for (EntitySensor s : entities) {
            if (snapshot.hit(s.id())) {
                hitIds.add(s.id());
                dispatch(s.id(), s.callback(), maid, snapshot);
            }
        }
        for (WorldSensor s : worlds) {
            if (snapshot.worldTriggers().contains(s.id())) {
                hitIds.add(s.id());
                dispatch(s.id(), s.callback(), maid, snapshot);
            }
        }
        for (StructureSensor s : structs) {
            if (snapshot.worldTriggers().contains(s.id())) {
                hitIds.add(s.id());
                dispatch(s.id(), s.callback(), maid, snapshot);
            }
        }

        // 通道2: 规则引擎事件
        if (!hitIds.isEmpty()) {
            RuleContext ctx = new RuleContext(maid);
            ctx.setAttribute("env_hits", String.join(",", hitIds));
            RuleEngine.handleEvent("lma_env_scan", ctx);
        }
    }

    /** 最新快照（可能为 null — 尚未扫描或女仆已卸载） */
    @Nullable
    public static EnvSnapshot getSnapshot(EntityMaid maid) {
        return SNAPSHOTS.get(maid.getId());
    }

    /** 女仆卸载清理（key 闭环） */
    public static void onMaidUnload(int entityId) {
        LAST_SCAN.remove(entityId);
        SNAPSHOTS.remove(entityId);
        STRUCT_LAST.remove(entityId);
        STRUCT_FOUND.remove(entityId);
    }

    // ── private ──

    /**
     * v37.2 结构探测: findNearestMapStructure 较慢（主线程 ms 级），
     * 独立低频间隔（默认 24000 tick = 1 MC 天）+ 总开关。
     * 返回本轮新出现（边沿）的结构感知器 id。
     */
    private static List<String> scanStructures(ServerLevel level, EntityMaid maid,
                                               List<StructureSensor> structs, int id, long now) {
        if (structs.isEmpty() || !MoreActionConfig.ENV_STRUCTURE_ENABLED.get()) return List.of();
        long structLast = STRUCT_LAST.getOrDefault(id, 0L);
        int structInterval = MoreActionConfig.ENV_STRUCTURE_INTERVAL.get();
        if (structLast != 0 && structLast <= now && now - structLast < structInterval) return List.of();
        STRUCT_LAST.put(id, now);

        Set<String> found = new HashSet<>();
        for (StructureSensor s : structs) {
            try {
                BlockPos pos = level.findNearestMapStructure(s.tag(), maid.blockPosition(),
                        MoreActionConfig.ENV_STRUCTURE_RADIUS.get(), false);
                if (pos != null) found.add(s.id());
            } catch (Exception ex) {
                LittleMaidMoreAction.LOGGER.error("[EnvSense] 结构感知器 '{}' 探测异常", s.id(), ex);
            }
        }
        Set<String> prevFound = STRUCT_FOUND.getOrDefault(id, Set.of());
        List<String> newly = new ArrayList<>();
        for (String sid : found) {
            if (!prevFound.contains(sid)) newly.add(sid);
        }
        STRUCT_FOUND.put(id, found);
        return newly;
    }

    private static void dispatch(String id, @Nullable EnvSenseRegistry.SensorCallback cb,
                                 EntityMaid maid, EnvSnapshot snapshot) {
        if (cb == null) return;
        try {
            cb.onScan(maid, snapshot);
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.error("[EnvSense] 感知器 '{}' 回调异常", id, ex);
        }
    }
}

package littlemaidmoreaction.littlemaidmoreaction.task.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskToggle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 环境感知广播器 (v63) — 全局扫描 + 信号分发。
 *
 * <p>每 200 tick（可配置）对服务端做一次全局扫描，
 * 对每个开启环境感知的女仆做边沿检测，将命中信号分发给声明了对应需求的被动任务 Pipeline。
 *
 * <h3>生命周期（闭环）</h3>
 * 内存 Map（非 NBT，不跨 session）；女仆卸载时
 * {@code LittleMaidMoreActionExtension.ServerEvents.onEntityLeaveLevel}
 * 调 {@link #onMaidUnload} 清除。
 */
public final class EnvSenseBroadcaster {

    /** entityId → 上次快照（边沿检测基线） */
    private static final Map<Integer, EnvSnapshot> PREV_SNAPSHOTS = new HashMap<>();
    /** entityId → 上次结构探测 gameTime（低频独立通道） */
    private static final Map<Integer, Long> STRUCT_LAST = new HashMap<>();
    /** entityId → 上轮探测到的结构信号 */
    private static final Map<Integer, Set<EnvSignal>> STRUCT_FOUND = new HashMap<>();

    private EnvSenseBroadcaster() {}

    // ── 公共入口 ──

    /**
     * 广播入口 — 由 TaskTickHandler 每 200 tick 调用一次。
     * 对 level 中所有女仆做边沿检测 + 信号分发。
     */
    public static void broadcast(ServerLevel level) {
        if (!MoreActionConfig.ENVSENSE_ENABLED.get()) return;

        long now = level.getGameTime();

        for (var e : level.getAllEntities()) {
            if (!(e instanceof EntityMaid maid)) continue;
            if (!maid.isAlive()) continue;

            // master 开关
            if (!maid.getPersistentData().getBoolean(TaskKeys.ENVSENSE_ENABLED)) continue;

            // 玩家门控
            int gateRadius = MoreActionConfig.ENV_PLAYER_GATE_RADIUS.get();
            if (gateRadius > 0
                    && !level.hasNearbyAlivePlayer(maid.getX(), maid.getY(), maid.getZ(), gateRadius)) {
                continue;
            }

            // 读取世界状态（轻量 — 所有女仆共享同一个 WorldInfo 快照合并）
            EnvSnapshot.WorldInfo world = EnvScanner.readWorld(level, maid.blockPosition());
            int radius = maid.hasRestriction()
                    ? Math.max(4, (int) maid.getRestrictRadius())
                    : MoreActionConfig.ENV_DEFAULT_RADIUS.get();
            Map<String, List<net.minecraft.world.entity.LivingEntity>> entities =
                    EnvScanner.scanEntities(level, maid, radius, MoreActionConfig.ENV_MAX_HITS.get());

            EnvSnapshot snap = new EnvSnapshot(now, Map.of(), entities, world, List.of());
            EnvSnapshot prev = PREV_SNAPSHOTS.get(maid.getId());

            // 边沿检测
            Set<EnvSignal> signals = detectSignals(prev, snap, level, maid, now);
            PREV_SNAPSHOTS.put(maid.getId(), snap);

            // 分发
            if (!signals.isEmpty()) {
                dispatch(maid, snap, signals);
            }
        }
    }

    /** 获取女仆最新快照（AI Context / 调试用） */
    @Nullable
    public static EnvSnapshot getSnapshot(EntityMaid maid) {
        return PREV_SNAPSHOTS.get(maid.getId());
    }

    /** 女仆卸载清理 */
    public static void onMaidUnload(int entityId) {
        PREV_SNAPSHOTS.remove(entityId);
        STRUCT_LAST.remove(entityId);
        STRUCT_FOUND.remove(entityId);
    }

    // ── 边沿检测 ──

    private static Set<EnvSignal> detectSignals(@Nullable EnvSnapshot prev, EnvSnapshot snap,
                                                  ServerLevel level, EntityMaid maid, long now) {
        Set<EnvSignal> signals = EnumSet.noneOf(EnvSignal.class);
        if (snap.world() == null) return signals;

        EnvSnapshot.WorldInfo pw = prev != null ? prev.world() : null;
        EnvSnapshot.WorldInfo cw = snap.world();

        // ── 天气 ──
        boolean wasSnowing = pw != null && pw.raining() && "SNOW".equals(pw.precipitation());
        boolean isSnowing = cw.raining() && "SNOW".equals(cw.precipitation());
        if (isSnowing && !wasSnowing) signals.add(EnvSignal.SNOWING);

        boolean wasRaining = pw != null && pw.raining() && !"SNOW".equals(pw.precipitation());
        boolean isRaining = cw.raining() && !"SNOW".equals(cw.precipitation());
        if (isRaining && !wasRaining) signals.add(EnvSignal.RAINING);

        if (pw != null && !pw.thundering() && cw.thundering()) signals.add(EnvSignal.THUNDER_START);
        if (pw != null && pw.raining() && !cw.raining()) signals.add(EnvSignal.WEATHER_CLEAR);

        // ── 温度 ──
        boolean wasCold = pw != null && pw.temperature() < MoreActionConfig.ENV_COLD_THRESHOLD.get().floatValue();
        boolean isCold = cw.temperature() < MoreActionConfig.ENV_COLD_THRESHOLD.get().floatValue();
        boolean wasHot = pw != null && pw.temperature() > MoreActionConfig.ENV_HOT_THRESHOLD.get().floatValue();
        boolean isHot = cw.temperature() > MoreActionConfig.ENV_HOT_THRESHOLD.get().floatValue();
        if (isCold && !wasCold) signals.add(EnvSignal.TEMP_COLD);
        if (isHot && !wasHot) signals.add(EnvSignal.TEMP_HOT);
        if (!isCold && !isHot && (wasCold || wasHot)) signals.add(EnvSignal.TEMP_NORMAL);

        // ── 昼夜 ──
        if (pw != null && pw.day() != cw.day()) signals.add(EnvSignal.DAY_NIGHT_CHANGE);

        // ── 黑暗 ──
        boolean wasDark = pw != null && pw.lightAtMaid() < MoreActionConfig.ENV_DARKNESS_THRESHOLD.get();
        boolean isDark = cw.lightAtMaid() < MoreActionConfig.ENV_DARKNESS_THRESHOLD.get();
        if (isDark && !wasDark) signals.add(EnvSignal.DARKNESS);

        // ── 维度/时段 ──
        if (pw != null && !pw.dimension().equals(cw.dimension())) signals.add(EnvSignal.DIMENSION_CHANGE);
        if (pw != null && !pw.timeSegment().equals(cw.timeSegment())) signals.add(EnvSignal.TIME_SEGMENT);

        // ── 实体 ──
        boolean hadMonster = prev != null && !prev.entities(EnvScanner.CAT_MONSTER).isEmpty();
        boolean hasMonster = !snap.entities(EnvScanner.CAT_MONSTER).isEmpty();
        if (hasMonster && !hadMonster) signals.add(EnvSignal.MONSTER_NEARBY);
        if (!hasMonster && hadMonster) signals.add(EnvSignal.MONSTER_CLEAR);

        boolean hadFriendly = prev != null && !prev.entities(EnvScanner.CAT_FRIENDLY).isEmpty();
        boolean hasFriendly = !snap.entities(EnvScanner.CAT_FRIENDLY).isEmpty();
        if (hasFriendly && !hadFriendly) signals.add(EnvSignal.FRIENDLY_NEARBY);

        boolean hadMaid = prev != null && !prev.entities(EnvScanner.CAT_MAID).isEmpty();
        boolean hasMaid = !snap.entities(EnvScanner.CAT_MAID).isEmpty();
        if (hasMaid && !hadMaid) signals.add(EnvSignal.MAID_NEARBY);

        // ── 结构 (低频独立通道, 默认 24000 tick = 1 MC 天) ──
        if (MoreActionConfig.ENV_STRUCTURE_ENABLED.get()) {
            detectStructureSignals(signals, level, maid, now);
        }

        return signals;
    }

    private static void detectStructureSignals(Set<EnvSignal> out, ServerLevel level,
                                                EntityMaid maid, long now) {
        int structInterval = MoreActionConfig.ENV_STRUCTURE_INTERVAL.get();
        long last = STRUCT_LAST.getOrDefault(maid.getId(), 0L);
        if (last != 0 && now - last < structInterval) return;
        STRUCT_LAST.put(maid.getId(), now);

        int id = maid.getId();
        Set<EnvSignal> found = new HashSet<>();
        BlockPos center = maid.blockPosition();
        int radius = MoreActionConfig.ENV_STRUCTURE_RADIUS.get();

        checkStructure(found, EnvSignal.VILLAGE_NEARBY,
                net.minecraft.tags.StructureTags.VILLAGE, level, center, radius);
        checkStructure(found, EnvSignal.MINESHAFT_NEARBY,
                net.minecraft.tags.StructureTags.MINESHAFT, level, center, radius);
        checkStructureOutpost(found, level, center, radius);

        Set<EnvSignal> prevFound = STRUCT_FOUND.getOrDefault(id, Set.of());
        for (EnvSignal sig : found) {
            if (!prevFound.contains(sig)) out.add(sig);
        }
        STRUCT_FOUND.put(id, found);
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

    private static void dispatch(EntityMaid maid, EnvSnapshot snap, Set<EnvSignal> signals) {
        for (TaskRegistry.TaskHandler h : TaskRegistry.passiveTasks().toList()) {
            TaskPipeline pipeline = h.pipeline();
            if (!TaskToggle.isEnabled(h.taskType()) || !TaskToggle.isEnabledFor(maid, h.taskType())) {
                continue;
            }
            // 获取 pipeline 声明的信号需求
            Set<EnvSignal> needs;
            try {
                var result = pipeline.validate(
                        (ServerLevel) maid.level(), maid,
                        new PipelineContext("", 0, ""));
                needs = result.needsSignals();
            } catch (Exception ex) {
                LittleMaidMoreAction.LOGGER.error("[EnvSense] validate 异常: {}", h.taskType(), ex);
                continue;
            }
            if (needs.isEmpty()) continue;

            // 交集 — 命中信号是否匹配 pipeline 需求？
            for (EnvSignal sig : signals) {
                if (needs.contains(sig)) {
                    try {
                        pipeline.onSignal(maid, snap, sig);
                    } catch (Exception ex) {
                        LittleMaidMoreAction.LOGGER.error("[EnvSense] onSignal 异常: {} signal={}",
                                h.taskType(), sig, ex);
                    }
                }
            }
        }
    }
}

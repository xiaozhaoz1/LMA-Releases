package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter.LmaTaskMemory;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaConstants;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.item.ToolStateReader;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.ConnectedBlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.HarvestTarget;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.TaskStateService;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.ToolJudge;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * 连锁采集执行器 (v36.6) — 只做编排，判定全部委托 {@link HarvestTarget}。
 *
 * <h3>循环（用户定义）</h3>
 * <pre>
 * 每 3 秒扫描 → 最近能采的(HarvestTarget 过滤+跳过集) → 走过去
 *   → 到达 BFS 整脉 → 蓄力(块数×每块tick) → 整脉同消 → 立即下一轮
 *   → 无目标待机扫描(常驻) + 一次性气泡提示
 * </pre>
 *
 * <h3>v36.6 心跳</h3>
 * keepAlive 同时刷 {@link TaskStateService#heartbeat} —
 * 修复 TaskEngine 60s 超时杀活任务 → auto-restart churn。
 */
public final class ChainHarvestExecute {

    /** 对外 API 兼容的模式枚举（内部全委托 HarvestTarget） */
    public enum Mode {
        WOOD(HarvestTarget.WOOD), ORE(HarvestTarget.ORE);
        final HarvestTarget target;
        Mode(HarvestTarget target) { this.target = target; }
    }

    // ── PersistentData keys (set → remove 闭环; IDX/TICK 为旧版残留一并清理) ──
    public static final String KEY_QUEUE = "lma_chain_queue";
    public static final String KEY_CHARGE_END = "lma_chain_charge_end";
    private static final String KEY_IDX_LEGACY = "lma_chain_idx";
    private static final String KEY_TICK_LEGACY = "lma_chain_tick";

    /** v36.4 用户定: 3 秒扫描一次 */
    private static final int SCAN_INTERVAL_TICKS = 60;
    private static final double MAX_DIST_SQR = 32 * 32;
    private static final int TOOL_RESERVE_DURABILITY = 1;
    /** 跳过集容量 (v36.3 用户定 10) */
    private static final int SKIP_MAX = 10;

    private static final Map<Integer, Long> LAST_SCAN = new ConcurrentHashMap<>();
    private static final Map<Integer, SkipState> SKIPPED = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> BUBBLE_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> BUBBLE_TICK = new ConcurrentHashMap<>();
    /** v36.6: 无目标待机提示去重（状态翻转才再发） */
    private static final Map<Integer, Boolean> IDLE_NOTIFIED = new ConcurrentHashMap<>();

    private static final class SkipState {
        int tierLevel = Integer.MIN_VALUE;
        final LinkedHashSet<Long> positions = new LinkedHashSet<>();
    }

    private ChainHarvestExecute() {}

    /** TaskRegistry.TaskExecutor 入口 */
    public static TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                     CompoundTag data, Mode mode) {
        HarvestTarget target = mode.target;
        ItemStack tool = maid.getMainHandItem();

        if (mode == Mode.ORE && !ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY)) {
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] {} 工具缺失或将坏，停止", target.label());
            clearChainData(data);
            return TaskResult.FAILED;
        }

        if (data.contains(KEY_QUEUE)) {
            return charge(world, maid, data, target, tool);
        }

        BlockState state = world.getBlockState(pos);
        if (target.matches(state)) {
            return tryStartVein(world, maid, pos, data, target, tool);
        }
        return idleScan(world, maid, data, target, tool, false);
    }

    // ── 阶段 1: 到达开脉（不能采 → 跳过 → 立即找下一个） ──

    private static TaskResult tryStartVein(ServerLevel world, EntityMaid maid, BlockPos pos,
                                           CompoundTag data, HarvestTarget target, ItemStack tool) {
        var skip = skipSet(maid, tool);
        BlockState state = world.getBlockState(pos);

        if (skip.contains(pos.asLong())
                || !target.canHarvest(tool, state)
                || !target.validAt(world, pos)) {
            addSkip(skip, pos.asLong());
            return idleScan(world, maid, data, target, tool, true);
        }

        List<BlockPos> vein = ConnectedBlockSearch.findConnected(world, pos,
                target.veinPredicate(state),
                MoreActionConfig.CHAIN_MAX_BLOCKS.get(), maid.blockPosition(), MAX_DIST_SQR);
        if (vein.isEmpty()) {
            addSkip(skip, pos.asLong());
            return idleScan(world, maid, data, target, tool, true);
        }

        // 耐久保护: 需耗久时队列截断至剩余耐久-1
        if (target.consumesDurability(tool)) {
            int budget = ToolStateReader.getRemainingDurability(tool) - TOOL_RESERVE_DURABILITY;
            if (budget < vein.size()) {
                vein = vein.subList(0, Math.max(0, budget));
            }
            if (vein.isEmpty()) {
                clearChainData(data);
                return TaskResult.FAILED;
            }
        }

        long chargeTicks = (long) vein.size() * target.intervalTicks(tool);
        long[] queue = new long[vein.size()];
        for (int i = 0; i < queue.length; i++) queue[i] = vein.get(i).asLong();
        data.putLongArray(KEY_QUEUE, queue);
        data.putLong(KEY_CHARGE_END, world.getGameTime() + chargeTicks);

        IDLE_NOTIFIED.put(maid.getId(), false);
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] {} 开脉 {} 块 @ {} 蓄力 {}t",
                target.label(), queue.length, pos, chargeTicks);
        bubble(world, maid, target.label() + " " + queue.length + " 块 蓄力 "
                + String.format("%.1f", chargeTicks / 20.0) + " 秒", true);
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    // ── 阶段 2: 蓄力 → 时间到整脉同消 ──

    private static TaskResult charge(ServerLevel world, EntityMaid maid,
                                     CompoundTag data, HarvestTarget target, ItemStack tool) {
        long now = world.getGameTime();
        long end = data.getLong(KEY_CHARGE_END);

        if (now < end) {
            if (now % 5 == 0) maid.swing(InteractionHand.MAIN_HAND);
            bubble(world, maid, target.label() + " 蓄力 "
                    + String.format("%.1f", (end - now) / 20.0) + " 秒", false);
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        long[] queue = data.getLongArray(KEY_QUEUE);
        int broken = 0;
        for (long l : queue) {
            BlockPos blockPos = BlockPos.of(l);
            BlockState state = world.getBlockState(blockPos);
            if (state.isAir() || !target.matches(state)) continue;
            if (blockPos.distSqr(maid.blockPosition()) > MAX_DIST_SQR) continue;
            if (!target.canHarvest(tool, state)) continue;
            if (!maid.canDestroyBlock(blockPos)) continue;
            if (maid.destroyBlock(blockPos)) broken++;
        }
        if (broken > 0 && target.consumesDurability(tool)) {
            tool.hurtAndBreak(broken, maid,
                    e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
        maid.swing(InteractionHand.MAIN_HAND);
        clearChainData(data);
        LAST_SCAN.remove(maid.getId()); // 立即下一轮扫描
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] {} 整脉破坏 {} 块", target.label(), broken);
        bubble(world, maid, target.label() + " 完成 " + broken + " 块", true);
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    // ── 阶段 0: 空闲扫描（3 秒节流，常驻不休眠） ──

    private static TaskResult idleScan(ServerLevel world, EntityMaid maid, CompoundTag data,
                                       HarvestTarget target, ItemStack tool, boolean immediate) {
        long now = world.getGameTime();
        int id = maid.getId();
        if (!immediate) {
            long last = LAST_SCAN.getOrDefault(id, 0L);
            if (last != 0 && last <= now && now - last < SCAN_INTERVAL_TICKS) {
                keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
        }
        LAST_SCAN.put(id, now);

        int radius = searchRadius(maid);
        BlockPos next = findNearestValid(world, maid, target, tool, radius);
        if (next == null) {
            // 常驻空闲 + 一次性提示（状态翻转才再发）
            if (!IDLE_NOTIFIED.getOrDefault(id, false)) {
                IDLE_NOTIFIED.put(id, true);
                bubble(world, maid, "附近没有可采集的" + target.label() + "目标", true);
            }
            LittleMaidMoreAction.LOGGER.debug("[ChainHarvest] {} 空闲扫描无目标 radius={}",
                    target.label(), radius);
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }
        IDLE_NOTIFIED.put(id, false);
        if (next.distSqr(maid.blockPosition()) < VanillaConstants.ARRIVE_DIST_SQR) {
            return tryStartVein(world, maid, next, data, target, tool);
        }
        LmaTaskMemory.setNavTarget(maid, next);
        LmaTaskMemory.setNavStartTick(maid, now);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, next, 1.0F, 2);
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    /** v36.6 半径规则: 有工作范围用工作范围，否则回退 config 默认(16) — 与 EnvSense 一致 */
    private static int searchRadius(EntityMaid maid) {
        return maid.hasRestriction()
                ? Math.max(4, (int) maid.getRestrictRadius())
                : MoreActionConfig.ENV_DEFAULT_RADIUS.get();
    }

    /** 最近的"能采的"目标（HarvestTarget 过滤 + 跳过集），BlockSearch 已按距离排序 */
    @Nullable
    private static BlockPos findNearestValid(ServerLevel world, EntityMaid maid,
                                             HarvestTarget target, ItemStack tool, int radius) {
        var skip = skipSet(maid, tool);
        BiPredicate<BlockPos, BlockState> pred = (p, s) ->
                target.matches(s) && !skip.contains(p.asLong()) && target.canHarvest(tool, s);
        var matches = BlockSearch.findBlocksInRange(world, maid.blockPosition(), radius, pred);
        return matches.isEmpty() ? null : matches.get(0).pos();
    }

    // ── 跳过集 ──

    private static LinkedHashSet<Long> skipSet(EntityMaid maid, ItemStack tool) {
        SkipState state = SKIPPED.computeIfAbsent(maid.getId(), k -> new SkipState());
        int tier = ToolStateReader.getTierLevel(tool);
        if (state.tierLevel != tier) {
            state.positions.clear();
            state.tierLevel = tier;
        }
        return state.positions;
    }

    private static void addSkip(LinkedHashSet<Long> set, long pos) {
        if (set.size() >= SKIP_MAX) {
            set.remove(set.iterator().next());
        }
        set.add(pos);
    }

    // ── 生命周期 ──

    /**
     * 自持导航目标 + v36.6 任务心跳 —
     * tick 循环维持 + TaskStateService.heartbeat 防 TaskEngine 超时误杀。
     */
    private static void keepAlive(ServerLevel world, EntityMaid maid) {
        LmaTaskMemory.setNavTarget(maid, maid.blockPosition());
        LmaTaskMemory.setNavStartTick(maid, world.getGameTime());
        TaskStateService.heartbeat(maid, world.getGameTime());
    }

    /** 清除连锁状态 key（闭环，含旧版残留 key） */
    public static void clearChainData(CompoundTag data) {
        data.remove(KEY_QUEUE);
        data.remove(KEY_CHARGE_END);
        data.remove(KEY_IDX_LEGACY);
        data.remove(KEY_TICK_LEGACY);
    }

    /** 女仆卸载清理（内存态闭环） */
    public static void onMaidUnload(int entityId) {
        SKIPPED.remove(entityId);
        LAST_SCAN.remove(entityId);
        BUBBLE_ID.remove(entityId);
        BUBBLE_TICK.remove(entityId);
        IDLE_NOTIFIED.remove(entityId);
    }

    /** 头顶气泡 — 按 id 替换（sendBubbleReplacing），非强制时 3 秒节流 */
    private static void bubble(ServerLevel world, EntityMaid maid, String text, boolean force) {
        int id = maid.getId();
        long now = world.getGameTime();
        if (!force) {
            long last = BUBBLE_TICK.getOrDefault(id, 0L);
            if (last != 0 && last <= now && now - last < SCAN_INTERVAL_TICKS) return;
        }
        BUBBLE_TICK.put(id, now);
        long prev = BUBBLE_ID.getOrDefault(id, -1L);
        BUBBLE_ID.put(id, WorldOutput.sendBubbleReplacing(maid, text, prev));
    }
}

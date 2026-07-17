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
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.ToolJudge;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * 连锁采集执行器 (v36.4) — 砍树 (WOOD) / 挖矿 (ORE) 简化循环。
 *
 * <h3>循环（用户 2026-07-17 定义）</h3>
 * <pre>
 * 每 3 秒(60 tick)扫描周围目标
 *   → 最近的"能挖的"(等级过滤+跳过集) → 设路径走过去
 *   → 到达 → BFS 整脉/整树 → 蓄力(块数 × 每块tick) → 时间到整脉同消
 *   → 挖不了跳过找下一个；挖完立即接下一轮扫描
 *   → 无目标保持空闲扫描(任务常驻，树再生自动恢复作业)
 * </pre>
 *
 * <h3>速度表（v36.2 用户定义, tick/块）</h3>
 * {@link ToolJudge#harvestIntervalTicks}: 空手40/木20/石15/铁10/钻5/合金5。
 * 蓄力总时长 = 块数 × 每块 tick。
 */
public final class ChainHarvestExecute {

    public enum Mode { WOOD, ORE }

    // ── PersistentData keys (set → remove 闭环; IDX/TICK 为旧版残留一并清理) ──
    public static final String KEY_QUEUE = "lma_chain_queue";
    public static final String KEY_CHARGE_END = "lma_chain_charge_end";
    private static final String KEY_IDX_LEGACY = "lma_chain_idx";
    private static final String KEY_TICK_LEGACY = "lma_chain_tick";

    /** v36.4 用户定: 3 秒扫描一次 */
    private static final int SCAN_INTERVAL_TICKS = 60;
    /** BFS/破坏距离上限平方 (以女仆为锚点 32 格) */
    private static final double MAX_DIST_SQR = 32 * 32;
    private static final int NATURE_CHECK_MAX_LOGS = 100;
    private static final int TOOL_RESERVE_DURABILITY = 1;
    /** 跳过集容量 (v36.3 用户定 10) */
    private static final int SKIP_MAX = 10;

    /** entityId → 上次扫描 gameTime（空闲扫描节流） */
    private static final Map<Integer, Long> LAST_SCAN = new ConcurrentHashMap<>();
    /** entityId → 跳过集（按工具 tier 版本化） */
    private static final Map<Integer, SkipState> SKIPPED = new ConcurrentHashMap<>();

    private static final class SkipState {
        int tierLevel = Integer.MIN_VALUE;
        final LinkedHashSet<Long> positions = new LinkedHashSet<>();
    }

    private ChainHarvestExecute() {}

    /** TaskRegistry.TaskExecutor 入口 */
    public static TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                     CompoundTag data, Mode mode) {
        ItemStack tool = maid.getMainHandItem();

        if (mode == Mode.ORE && !ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY)) {
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] ORE 工具缺失或将坏，停止");
            clearChainData(data);
            return TaskResult.FAILED;
        }

        // 蓄力/破坏阶段
        if (data.contains(KEY_QUEUE)) {
            return charge(world, maid, data, mode, tool);
        }

        // 到达目标方块 → 尝试开脉
        BlockState state = world.getBlockState(pos);
        if (isTarget(mode, state)) {
            return tryStartVein(world, maid, pos, data, mode, tool);
        }

        // 空闲（pos=女仆脚下 keepAlive 回调）→ 3 秒节流扫描
        return idleScan(world, maid, data, mode, tool, false);
    }

    // ── 阶段 1: 到达开脉（挖不了 → 跳过 → 立即找下一个） ──

    private static TaskResult tryStartVein(ServerLevel world, EntityMaid maid, BlockPos pos,
                                           CompoundTag data, Mode mode, ItemStack tool) {
        var skip = skipSet(maid, tool);
        BlockState state = world.getBlockState(pos);

        if (skip.contains(pos.asLong())) {
            return idleScan(world, maid, data, mode, tool, true);
        }
        if (mode == Mode.ORE && !ToolJudge.canPickaxeMine(tool, state)) {
            addSkip(skip, pos.asLong());
            return idleScan(world, maid, data, mode, tool, true);
        }
        if (mode == Mode.WOOD && MoreActionConfig.CHAIN_WOOD_NATURE_CHECK.get()
                && !ConnectedBlockSearch.isNaturalTree(world, pos, NATURE_CHECK_MAX_LOGS)) {
            addSkip(skip, pos.asLong());
            return idleScan(world, maid, data, mode, tool, true);
        }

        List<BlockPos> vein = ConnectedBlockSearch.findConnected(world, pos,
                matchPredicate(mode, state),
                MoreActionConfig.CHAIN_MAX_BLOCKS.get(), maid.blockPosition(), MAX_DIST_SQR);
        if (vein.isEmpty()) {
            addSkip(skip, pos.asLong());
            return idleScan(world, maid, data, mode, tool, true);
        }

        // 耐久保护: 需耗久时队列截断至剩余耐久-1（保 1 点不打坏工具）
        if (consumesDurability(mode, tool)) {
            int budget = ToolStateReader.getRemainingDurability(tool) - TOOL_RESERVE_DURABILITY;
            if (budget < vein.size()) {
                vein = vein.subList(0, Math.max(0, budget));
            }
            if (vein.isEmpty()) {
                clearChainData(data);
                return TaskResult.FAILED;
            }
        }

        int perBlockTicks = ToolJudge.harvestIntervalTicks(tool, mode == Mode.WOOD);
        long chargeTicks = (long) vein.size() * perBlockTicks;
        long[] queue = new long[vein.size()];
        for (int i = 0; i < queue.length; i++) queue[i] = vein.get(i).asLong();
        data.putLongArray(KEY_QUEUE, queue);
        data.putLong(KEY_CHARGE_END, world.getGameTime() + chargeTicks);

        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 开脉 {} 块 @ {} 蓄力 {}t",
                mode, queue.length, pos, chargeTicks);
        bubble(maid, label(mode) + " " + queue.length + " 块 蓄力 "
                + String.format("%.1f", chargeTicks / 20.0) + " 秒");
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    // ── 阶段 2: 蓄力 → 时间到整脉同消 ──

    private static TaskResult charge(ServerLevel world, EntityMaid maid,
                                     CompoundTag data, Mode mode, ItemStack tool) {
        long now = world.getGameTime();
        long end = data.getLong(KEY_CHARGE_END);

        if (now < end) {
            if (now % 5 == 0) maid.swing(InteractionHand.MAIN_HAND);
            bubble(maid, label(mode) + " 蓄力 "
                    + String.format("%.1f", (end - now) / 20.0) + " 秒");
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        // 时间到 — 逐块复核后整脉同消
        long[] queue = data.getLongArray(KEY_QUEUE);
        int broken = 0;
        for (long l : queue) {
            BlockPos target = BlockPos.of(l);
            BlockState state = world.getBlockState(target);
            if (!stillMatches(mode, state)) continue;
            if (target.distSqr(maid.blockPosition()) > MAX_DIST_SQR) continue;
            if (mode == Mode.ORE && !ToolJudge.canPickaxeMine(tool, state)) continue;
            if (!maid.canDestroyBlock(target)) continue;
            if (maid.destroyBlock(target)) broken++;
        }
        if (broken > 0 && consumesDurability(mode, tool)) {
            tool.hurtAndBreak(broken, maid,
                    e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
        maid.swing(InteractionHand.MAIN_HAND);
        clearChainData(data);
        LAST_SCAN.remove(maid.getId()); // 立即触发下一轮扫描
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 整脉破坏 {} 块", mode, broken);
        bubble(maid, label(mode) + " 完成 " + broken + " 块");
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    // ── 阶段 0: 空闲扫描（3 秒节流，常驻不休眠） ──

    private static TaskResult idleScan(ServerLevel world, EntityMaid maid, CompoundTag data,
                                       Mode mode, ItemStack tool, boolean immediate) {
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

        BlockPos next = findNearestValid(world, maid, mode, tool);
        if (next == null) {
            // 常驻空闲: 不 FAILED — 树再生/矿刷新后自动恢复作业
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }
        if (next.distSqr(maid.blockPosition()) < VanillaConstants.ARRIVE_DIST_SQR) {
            return tryStartVein(world, maid, next, data, mode, tool);
        }
        // 设路径立即起步（协调行为同款调用，不等行为重启）
        LmaTaskMemory.setNavTarget(maid, next);
        LmaTaskMemory.setNavStartTick(maid, now);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, next, 1.0F, 2);
        return TaskResult.CONTINUE;
    }

    /** 最近的"能挖的"目标（等级过滤 + 跳过集过滤），BlockSearch 已按距离排序 */
    @Nullable
    private static BlockPos findNearestValid(ServerLevel world, EntityMaid maid,
                                             Mode mode, ItemStack tool) {
        var skip = skipSet(maid, tool);
        BiPredicate<BlockPos, BlockState> pred;
        if (mode == Mode.WOOD) {
            pred = (p, s) -> s.is(BlockTags.LOGS) && !skip.contains(p.asLong());
        } else {
            pred = (p, s) -> s.is(Tags.Blocks.ORES) && !skip.contains(p.asLong())
                    && ToolJudge.canPickaxeMine(tool, s);
        }
        var matches = BlockSearch.findBlocksInRange(world, maid.blockPosition(),
                maid.getRestrictRadius(), pred);
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

    // ── 判定 ──

    private static boolean isTarget(Mode mode, BlockState state) {
        return mode == Mode.WOOD ? state.is(BlockTags.LOGS) : state.is(Tags.Blocks.ORES);
    }

    private static boolean stillMatches(Mode mode, BlockState state) {
        return !state.isAir() && isTarget(mode, state);
    }

    private static BiPredicate<BlockPos, BlockState> matchPredicate(Mode mode, BlockState startState) {
        if (mode == Mode.WOOD) return (p, s) -> s.is(BlockTags.LOGS);
        Block startBlock = startState.getBlock();
        return (p, s) -> s.getBlock() == startBlock;
    }

    /** 耗久规则: 挖矿恒扣; 砍树仅持可用斧扣（空手/非斧慢砍不扣） */
    private static boolean consumesDurability(Mode mode, ItemStack tool) {
        return mode == Mode.ORE
                || (ToolStateReader.isAxe(tool)
                    && ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY));
    }

    private static String label(Mode mode) {
        return mode == Mode.WOOD ? "伐木" : "采矿";
    }

    // ── 生命周期 ──

    /** 自持导航目标 = 女仆脚下 → 协调行为 tick() 到达检查恒真 → 每 tick 回调本执行器 */
    private static void keepAlive(ServerLevel world, EntityMaid maid) {
        LmaTaskMemory.setNavTarget(maid, maid.blockPosition());
        LmaTaskMemory.setNavStartTick(maid, world.getGameTime());
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
    }

    /** 头顶气泡进度（LMA 内置超时节流） */
    private static void bubble(EntityMaid maid, String text) {
        WorldOutput.sendBubbleIfTimeout(maid, text, 3000);
    }
}

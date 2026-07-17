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
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.ToolJudge;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * 连锁采集执行器 (v36) — 砍树 (WOOD) / 挖矿 (ORE) 共用状态机。
 *
 * <h3>v36.3 批量预队列</h3>
 * 到达时一次性搜范围内所有目标 → BFS 扁平化入单一队列 → 跳过集标记 → 连续挖掘。
 * 打破 v36.2 "每脉走→挖→搜下一脉" 慢循环。挖掘头顶气泡实时显示进度。
 *
 * <h3>速度模型（v36.2 用户定义）</h3>
 * {@link ToolJudge#harvestIntervalTicks}: 空手40/木20/石15/铁10/钻5/合金5 (tick/块)。
 */
public final class ChainHarvestExecute {

    public enum Mode { WOOD, ORE }

    public static final String KEY_QUEUE = "lma_chain_queue";
    public static final String KEY_IDX = "lma_chain_idx";
    public static final String KEY_TICK = "lma_chain_tick";

    private static final double MAX_DIST_SQR = 32 * 32;
    private static final int NATURE_CHECK_MAX_LOGS = 100;
    private static final int TOOL_RESERVE_DURABILITY = 1;
    /** v36.3 用户定 10 — 批量预队列后跳过集作用降低 */
    private static final int SKIP_MAX = 10;

    // ── 跳过集（按工具 tier 版本化） ──
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

        if (!data.contains(KEY_QUEUE)) {
            return initQueue(world, maid, pos, data, mode, tool);
        }
        return breakNext(world, maid, data, mode, tool);
    }

    // ── 阶段 1: v36.3 批量预队列 — 到达时一次性搜所有候选，全量扁平化入队 ──

    private static TaskResult initQueue(ServerLevel world, EntityMaid maid, BlockPos pos,
                                        CompoundTag data, Mode mode, ItemStack tool) {
        // 1. 搜范围内所有候选
        List<BlockPos> candidates = findAllTargets(world, maid, mode, tool);
        if (candidates.isEmpty()) {
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 范围内无可采集目标", mode);
            return TaskResult.FAILED;
        }

        // 2. 对每个候选 BFS → 全量扁平化入队（去重）
        Set<Long> seen = new HashSet<>();
        List<Long> flat = new ArrayList<>();
        int blockedByNature = 0;
        for (BlockPos c : candidates) {
            if (flat.size() >= MoreActionConfig.CHAIN_MAX_BLOCKS.get()) break;
            if (mode == Mode.WOOD && MoreActionConfig.CHAIN_WOOD_NATURE_CHECK.get()
                    && !ConnectedBlockSearch.isNaturalTree(world, c, NATURE_CHECK_MAX_LOGS)) {
                addSkip(skipSet(maid, tool), c.asLong());
                blockedByNature++;
                continue;
            }
            BlockState startState = world.getBlockState(c);
            List<BlockPos> vein = ConnectedBlockSearch.findConnected(world, c,
                    matchPredicate(mode, startState),
                    MoreActionConfig.CHAIN_MAX_BLOCKS.get() - flat.size(),
                    maid.blockPosition(), MAX_DIST_SQR);
            for (BlockPos v : vein) {
                if (seen.add(v.asLong())) flat.add(v.asLong());
            }
            // 脉起点标记已入队（下次 findAllTargets 跳过）
            addSkip(skipSet(maid, tool), c.asLong());
        }

        if (flat.isEmpty()) {
            clearChainData(data);
            if (blockedByNature > 0) {
                return skipAndRelay(world, maid, pos, data, mode, tool,
                        "非天然树×" + blockedByNature);
            }
            return TaskResult.FAILED;
        }

        long[] queue = new long[flat.size()];
        for (int i = 0; i < queue.length; i++) queue[i] = flat.get(i);
        data.putLongArray(KEY_QUEUE, queue);
        data.putInt(KEY_IDX, 0);
        data.putLong(KEY_TICK, 0L);

        String label = mode == Mode.WOOD ? "伐木" : "采矿";
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 批量入队 {} 块 ({}脉)",
                mode, queue.length, candidates.size());
        bubble(maid, label + " 剩余 " + queue.length + " 块");
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    // ── 阶段 2: 逐块破坏（v36.2 等级提速 + v36.3 气泡 + 脉间 relay） ──

    private static TaskResult breakNext(ServerLevel world, EntityMaid maid,
                                        CompoundTag data, Mode mode, ItemStack tool) {
        long now = world.getGameTime();
        long last = data.getLong(KEY_TICK);
        int interval = ToolJudge.harvestIntervalTicks(tool, mode == Mode.WOOD);
        if (last != 0 && last <= now && now - last < interval) {
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        long[] queue = data.getLongArray(KEY_QUEUE);
        int idx = data.getInt(KEY_IDX);
        while (idx < queue.length) {
            BlockPos target = BlockPos.of(queue[idx]);
            idx++;
            BlockState state = world.getBlockState(target);
            if (!stillMatches(mode, state)) continue;
            if (target.distSqr(maid.blockPosition()) > MAX_DIST_SQR) continue;
            if (mode == Mode.ORE && !ToolJudge.canPickaxeMine(tool, state)) continue;
            if (!maid.canDestroyBlock(target)) continue;

            data.putInt(KEY_IDX, idx);
            if (maid.destroyBlock(target)) {
                boolean consumeDurability = mode == Mode.ORE
                        || (ToolStateReader.isAxe(tool)
                            && ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY));
                if (consumeDurability) {
                    tool.hurtAndBreak(1, maid,
                            e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                }
                maid.swing(InteractionHand.MAIN_HAND);
                data.putLong(KEY_TICK, now);

                // v36.3 气泡进度
                String label = mode == Mode.WOOD ? "伐木" : "采矿";
                bubble(maid, label + " 剩余 " + (queue.length - idx) + "/" + queue.length + " 块");
            }
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        // 队列耗尽 — v36.3 重搜+接力（批量预队列后少数残余可能新增/跳过集耗尽）
        clearChainData(data);
        BlockPos next = lastTarget(maid);  // 优先接力上次队列末尾附近的
        if (next == null) next = firstTarget(world, maid, mode, tool);
        if (next != null) {
            LmaTaskMemory.setNavTarget(maid, next);
            LmaTaskMemory.setNavStartTick(maid, now);
            return TaskResult.CONTINUE;
        }
        clearFlowData(data);
        LmaTaskMemory.clearAllNav(maid);
        return TaskResult.CONTINUE;
    }

    // ── v36.3 批量目标搜索 ──

    /** 范围内所有可采目标（等级前置过滤 + 跳过集过滤），按距离排 */
    private static List<BlockPos> findAllTargets(ServerLevel world, EntityMaid maid,
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
        List<BlockPos> result = new ArrayList<>();
        for (var m : matches) result.add(m.pos());
        return result;
    }

    /** 上次队列最后一个有效的 pos（接力锚点） */
    @Nullable
    private static BlockPos lastTarget(EntityMaid maid) {
        BlockPos nav = LmaTaskMemory.getNavTarget(maid);
        return nav != null ? nav : LmaTaskMemory.getNavTarget(maid); // blockPos fallback
    }

    /** 搜下一个候选（for relay）— 等价 findAllTargets 首项，但会清除已入队旧 skip 后可能回归 */
    @Nullable
    private static BlockPos firstTarget(ServerLevel world, EntityMaid maid,
                                        Mode mode, ItemStack tool) {
        var list = findAllTargets(world, maid, mode, tool);
        return list.isEmpty() ? null : list.get(0);
    }

    // ── 跳过集 ──

    private static TaskResult skipAndRelay(ServerLevel world, EntityMaid maid, BlockPos pos,
                                           CompoundTag data, Mode mode, ItemStack tool, String reason) {
        clearChainData(data);
        BlockPos next = firstTarget(world, maid, mode, tool);
        if (next == null) {
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 范围内无可采集目标 ({})", mode, reason);
            return TaskResult.FAILED;
        }
        LmaTaskMemory.setNavTarget(maid, next);
        LmaTaskMemory.setNavStartTick(maid, world.getGameTime());
        return TaskResult.CONTINUE;
    }

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

    private static BiPredicate<BlockPos, BlockState> matchPredicate(Mode mode, BlockState startState) {
        if (mode == Mode.WOOD) return (p, s) -> s.is(BlockTags.LOGS);
        Block startBlock = startState.getBlock();
        return (p, s) -> s.getBlock() == startBlock;
    }

    private static boolean stillMatches(Mode mode, BlockState state) {
        if (state.isAir()) return false;
        return mode == Mode.WOOD ? state.is(BlockTags.LOGS) : state.is(Tags.Blocks.ORES);
    }

    // ── 生命周期 ──

    private static void keepAlive(ServerLevel world, EntityMaid maid) {
        LmaTaskMemory.setNavTarget(maid, maid.blockPosition());
        LmaTaskMemory.setNavStartTick(maid, world.getGameTime());
    }

    public static void clearChainData(CompoundTag data) {
        data.remove(KEY_QUEUE);
        data.remove(KEY_IDX);
        data.remove(KEY_TICK);
    }

    public static void onMaidUnload(int entityId) {
        SKIPPED.remove(entityId);
    }

    private static void clearFlowData(CompoundTag data) {
        data.remove(TaskKeys.FLOW_TASK); data.remove(TaskKeys.FLOW_TASK_ID);
        data.remove(TaskKeys.FLOW_STATE); data.remove(TaskKeys.FLOW_STEP);
        data.remove(TaskKeys.FLOW_COUNTER); data.remove(TaskKeys.FLOW_MAX_COUNT);
        data.remove(TaskKeys.FLOW_TICK); data.remove(TaskKeys.FLOW_TIMEOUT);
        data.remove(TaskKeys.FLOW_DATA); data.remove(TaskKeys.FLOW_CACHED);
    }

    /** v36.3 头顶气泡 — 高频破坏节流（sendBubbleIfTimeout 内部 3000ms 冷却，自动返回相同的 bubble id） */
    private static void bubble(EntityMaid maid, String text) {
        WorldOutput.sendBubbleIfTimeout(maid, text, 3000);
    }
}

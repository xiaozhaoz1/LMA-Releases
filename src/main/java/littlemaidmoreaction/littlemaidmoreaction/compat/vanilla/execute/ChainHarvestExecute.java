package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter.LmaTaskMemory;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.item.ToolStateReader;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.ConnectedBlockSearch;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * 连锁采集执行器 (v36) — 砍树 (WOOD) / 挖矿 (ORE) 共用状态机。
 *
 * <p>由 {@code LmaFlowCoordinationBehavior.doExecute()} 驱动：
 * 首次调用（女仆已到达起始方块 3 格内）BFS 建队；后续每 tick 回调
 * （通过 keepAlive 自持 navTarget 维持 tick 循环），按节流间隔逐块破坏。
 *
 * <h3>v36.1 流程语义</h3>
 * <ul>
 *   <li><b>跳过机制</b>: 镐等级不足的矿 / 非天然树 → 记入跳过集，接力搜索下一个目标
 *       （不再 FAILED 休眠）。跳过集按工具等级版本化 — 换镐后自动重置</li>
 *   <li><b>无斧慢砍</b>: 砍树不要求斧；持可用斧 = 正常速度+扣耐久，
 *       空手/非斧 = 慢速（interval × no_axe_interval_multiplier）+ 不扣耐久</li>
 *   <li><b>等级前置过滤</b>: 接力搜索时 ORE 模式直接排除挖不动的矿，不走冤枉路</li>
 * </ul>
 *
 * <h3>PersistentData key 闭环</h3>
 * lma_chain_queue / lma_chain_idx / lma_chain_tick —
 * 所有终止路径调用 {@link #clearChainData}；跳过集为内存态（onMaidUnload 清理）。
 */
public final class ChainHarvestExecute {

    /** 采集模式 */
    public enum Mode { WOOD, ORE }

    // ── PersistentData keys (set → remove 闭环) ──
    public static final String KEY_QUEUE = "lma_chain_queue";
    public static final String KEY_IDX = "lma_chain_idx";
    public static final String KEY_TICK = "lma_chain_tick";

    /** BFS/破坏距离上限平方 (以女仆为锚点 32 格) */
    private static final double MAX_DIST_SQR = 32 * 32;
    /** 天然树校验 DFS 原木遍历上限 */
    private static final int NATURE_CHECK_MAX_LOGS = 100;
    /** 工具保留耐久 — 剩最后 1 点时停手，不打坏工具 */
    private static final int TOOL_RESERVE_DURABILITY = 1;
    /** 跳过集容量上限（FIFO 淘汰） */
    private static final int SKIP_MAX = 128;

    /** v36.1: entityId → 跳过集（挖不动的矿/非天然树），按工具等级版本化 */
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

        // v36.1: 硬工具闸只保留挖矿（砍树无斧=慢砍，不拦截）
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

    // ── 阶段 1: BFS 建队（失败 → 跳过+接力，v36.1） ──

    private static TaskResult initQueue(ServerLevel world, EntityMaid maid, BlockPos pos,
                                        CompoundTag data, Mode mode, ItemStack tool) {
        BlockState startState = world.getBlockState(pos);
        if (mode == Mode.ORE && !ToolJudge.canPickaxeMine(tool, startState)) {
            return skipAndRelay(world, maid, pos, data, mode, tool, "镐等级不足");
        }
        if (mode == Mode.WOOD && MoreActionConfig.CHAIN_WOOD_NATURE_CHECK.get()
                && !ConnectedBlockSearch.isNaturalTree(world, pos, NATURE_CHECK_MAX_LOGS)) {
            return skipAndRelay(world, maid, pos, data, mode, tool, "非天然树");
        }

        List<BlockPos> connected = ConnectedBlockSearch.findConnected(world, pos,
                matchPredicate(mode, startState),
                MoreActionConfig.CHAIN_MAX_BLOCKS.get(), maid.blockPosition(), MAX_DIST_SQR);
        if (connected.isEmpty()) {
            return skipAndRelay(world, maid, pos, data, mode, tool, "起点不匹配");
        }

        long[] queue = new long[connected.size()];
        for (int i = 0; i < queue.length; i++) {
            queue[i] = connected.get(i).asLong();
        }
        data.putLongArray(KEY_QUEUE, queue);
        data.putInt(KEY_IDX, 0);
        data.putLong(KEY_TICK, 0L);
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 建队 {} 块 @ {}",
                mode, queue.length, pos);
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    // ── 阶段 2: 逐块破坏 ──

    private static TaskResult breakNext(ServerLevel world, EntityMaid maid,
                                        CompoundTag data, Mode mode, ItemStack tool) {
        long now = world.getGameTime();
        long last = data.getLong(KEY_TICK);
        // v36.1: 持可用斧=正常速度; 空手/非斧砍树=慢速
        boolean fastAxe = mode == Mode.WOOD
                && ToolStateReader.isAxe(tool)
                && ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY);
        int interval = Math.max(1, MoreActionConfig.CHAIN_BREAK_INTERVAL.get());
        if (mode == Mode.WOOD && !fastAxe) {
            interval *= Math.max(1, MoreActionConfig.CHAIN_NO_AXE_MULTIPLIER.get());
        }
        // 时间戳防护: last==0(尚未破坏) 或 last>now(跨session异常) 时不节流，直接推进
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
            if (!stillMatches(mode, state)) continue;                          // 已破坏/被替换
            if (target.distSqr(maid.blockPosition()) > MAX_DIST_SQR) continue; // 残留队列距离守卫
            if (mode == Mode.ORE && !ToolJudge.canPickaxeMine(tool, state)) continue; // 混入等级不足的方块
            if (!maid.canDestroyBlock(target)) continue;                       // TLM 破坏权限

            data.putInt(KEY_IDX, idx);
            if (maid.destroyBlock(target)) {
                // TLM TaskSnow 样板: 掉落进背包由 destroyBlock 内部处理
                // v36.1: 仅挖矿或持可用斧时消耗耐久（空手/非斧砍树不消耗）
                if (mode == Mode.ORE || fastAxe) {
                    tool.hurtAndBreak(1, maid,
                            e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                }
                maid.swing(InteractionHand.MAIN_HAND);
                data.putLong(KEY_TICK, now);
                LittleMaidMoreAction.LOGGER.debug("[ChainHarvest] mode={} 破坏 {} ({}/{})",
                        mode, target, idx, queue.length);
            }
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        // 队列耗尽 — 本棵树/本条矿脉完成。v36.1: 直接接力搜索下一目标（跳过集感知）
        clearChainData(data);
        BlockPos next = findNextTarget(world, maid, mode, tool);
        if (next != null) {
            LmaTaskMemory.setNavTarget(maid, next);
            LmaTaskMemory.setNavStartTick(maid, now);
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 本轮完成，接力下一目标 {}", mode, next);
            return TaskResult.CONTINUE;
        }
        // 范围内无目标 → 回落 GUI-init 循环（下轮协调行为重搜，最终无果则休眠）
        clearFlowData(data);
        LmaTaskMemory.clearAllNav(maid);
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 本轮完成，范围内暂无下一目标", mode);
        return TaskResult.CONTINUE;
    }

    // ── v36.1: 跳过 + 接力 ──

    /** 目标不可采集（等级不足/非天然树）→ 记跳过集 → 搜下一个 → 设导航接力 */
    private static TaskResult skipAndRelay(ServerLevel world, EntityMaid maid, BlockPos pos,
                                           CompoundTag data, Mode mode, ItemStack tool, String reason) {
        addSkip(skipSet(maid, tool), pos.asLong());
        LittleMaidMoreAction.LOGGER.debug("[ChainHarvest] mode={} 跳过 {} ({})", mode, pos, reason);
        clearChainData(data);
        BlockPos next = findNextTarget(world, maid, mode, tool);
        if (next == null) {
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 范围内无可采集目标 ({})", mode, reason);
            return TaskResult.FAILED;
        }
        LmaTaskMemory.setNavTarget(maid, next);
        LmaTaskMemory.setNavStartTick(maid, world.getGameTime());
        return TaskResult.CONTINUE;
    }

    /**
     * 跳过集感知 + 工具感知的下一目标搜索。
     * ORE 模式在搜索阶段就用 canPickaxeMine 排除挖不动的矿（不走冤枉路）。
     */
    @Nullable
    private static BlockPos findNextTarget(ServerLevel world, EntityMaid maid,
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

    /** 取跳过集（按工具等级版本化 — 换更好的镐后自动清空重试） */
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
            var it = set.iterator();
            it.next();
            it.remove();
        }
        set.add(pos);
    }

    // ── 判定组装（模式分派，判断本体在计算层/输入层） ──

    /** BFS 建队匹配: WOOD=所有原木(整树含分叉树种), ORE=与起始方块同种(单一矿脉) */
    private static BiPredicate<BlockPos, BlockState> matchPredicate(Mode mode, BlockState startState) {
        if (mode == Mode.WOOD) {
            return (p, s) -> s.is(BlockTags.LOGS);
        }
        Block startBlock = startState.getBlock();
        return (p, s) -> s.getBlock() == startBlock;
    }

    /** 破坏前复核: 方块仍是可采集目标 */
    private static boolean stillMatches(Mode mode, BlockState state) {
        if (state.isAir()) return false;
        return mode == Mode.WOOD ? state.is(BlockTags.LOGS) : state.is(Tags.Blocks.ORES);
    }

    // ── 生命周期 ──

    /** 自持导航目标 = 女仆脚下 → 协调行为 tick() 到达检查恒真 → 每 tick 回调本执行器 */
    private static void keepAlive(ServerLevel world, EntityMaid maid) {
        LmaTaskMemory.setNavTarget(maid, maid.blockPosition());
        LmaTaskMemory.setNavStartTick(maid, world.getGameTime());
    }

    /** 清除连锁状态 key（闭环） */
    public static void clearChainData(CompoundTag data) {
        data.remove(KEY_QUEUE);
        data.remove(KEY_IDX);
        data.remove(KEY_TICK);
    }

    /** v36.1: 女仆卸载清理跳过集（内存态闭环） */
    public static void onMaidUnload(int entityId) {
        SKIPPED.remove(entityId);
    }

    /** 清除流程任务 key → 允许协调行为 GUI-init 开始下一轮（与 StartTaskTool 清理列表一致） */
    private static void clearFlowData(CompoundTag data) {
        data.remove(TaskKeys.FLOW_TASK);
        data.remove(TaskKeys.FLOW_TASK_ID);
        data.remove(TaskKeys.FLOW_STATE);
        data.remove(TaskKeys.FLOW_STEP);
        data.remove(TaskKeys.FLOW_COUNTER);
        data.remove(TaskKeys.FLOW_MAX_COUNT);
        data.remove(TaskKeys.FLOW_TICK);
        data.remove(TaskKeys.FLOW_TIMEOUT);
        data.remove(TaskKeys.FLOW_DATA);
        data.remove(TaskKeys.FLOW_CACHED);
    }
}

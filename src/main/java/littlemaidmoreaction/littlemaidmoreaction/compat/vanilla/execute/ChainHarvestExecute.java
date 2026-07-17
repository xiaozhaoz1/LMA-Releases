package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter.LmaTaskMemory;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.TaskResult;
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

import java.util.List;
import java.util.function.BiPredicate;

/**
 * 连锁采集执行器 (v36) — 砍树 (WOOD) / 挖矿 (ORE) 共用状态机。
 *
 * <p>由 {@code LmaFlowCoordinationBehavior.doExecute()} 驱动：
 * 首次调用（女仆已到达起始方块 3 格内）BFS 建队；后续每 tick 回调
 * （通过 keepAlive 自持 navTarget 维持 tick 循环），按节流间隔逐块破坏。
 *
 * <p>判断全部委托计算层 {@link ToolJudge} 与输入层 {@link ConnectedBlockSearch}，
 * 本类只做编排。破坏样板遵循 TLM TaskSnow：destroyBlock + hurtAndBreak + swing。
 *
 * <h3>PersistentData key 闭环</h3>
 * lma_chain_queue / lma_chain_idx / lma_chain_tick —
 * 所有终止路径（完成/失败/工具不可用）调用 {@link #clearChainData}；
 * 跨 session 残留由 TlmEventAdapter.onEntityJoin 清理 + 逐块距离守卫双重防护。
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

    private ChainHarvestExecute() {}

    /** TaskRegistry.TaskExecutor 入口 */
    public static TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                     CompoundTag data, Mode mode) {
        ItemStack tool = maid.getMainHandItem();

        if (!ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY)) {
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 工具缺失或将坏，停止", mode);
            clearChainData(data);
            return TaskResult.FAILED;
        }

        if (!data.contains(KEY_QUEUE)) {
            return initQueue(world, maid, pos, data, mode, tool);
        }
        return breakNext(world, maid, data, mode, tool);
    }

    // ── 阶段 1: BFS 建队 ──

    private static TaskResult initQueue(ServerLevel world, EntityMaid maid, BlockPos pos,
                                        CompoundTag data, Mode mode, ItemStack tool) {
        BlockState startState = world.getBlockState(pos);
        if (!canHarvest(mode, tool, startState)) {
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 工具不符 (需{}且等级足够)",
                    mode, mode == Mode.ORE ? "镐" : "斧");
            clearChainData(data);
            return TaskResult.FAILED;
        }
        if (mode == Mode.WOOD && MoreActionConfig.CHAIN_WOOD_NATURE_CHECK.get()
                && !ConnectedBlockSearch.isNaturalTree(world, pos, NATURE_CHECK_MAX_LOGS)) {
            LittleMaidMoreAction.LOGGER.info("[ChainHarvest] {} 非天然树（无相连自然树叶），跳过", pos);
            clearChainData(data);
            return TaskResult.FAILED;
        }

        List<BlockPos> connected = ConnectedBlockSearch.findConnected(world, pos,
                matchPredicate(mode, startState),
                MoreActionConfig.CHAIN_MAX_BLOCKS.get(), maid.blockPosition(), MAX_DIST_SQR);
        if (connected.isEmpty()) {
            clearChainData(data);
            return TaskResult.FAILED;
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
        int interval = Math.max(1, MoreActionConfig.CHAIN_BREAK_INTERVAL.get());
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
            if (!canHarvest(mode, tool, state)) continue;                      // 混入等级不足的方块
            if (!maid.canDestroyBlock(target)) continue;                       // TLM 破坏权限

            data.putInt(KEY_IDX, idx);
            if (maid.destroyBlock(target)) {
                // TLM TaskSnow 样板: 掉落进背包由 destroyBlock 内部处理
                tool.hurtAndBreak(1, maid,
                        e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                maid.swing(InteractionHand.MAIN_HAND);
                data.putLong(KEY_TICK, now);
                LittleMaidMoreAction.LOGGER.debug("[ChainHarvest] mode={} 破坏 {} ({}/{})",
                        mode, target, idx, queue.length);
            }
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        // 队列耗尽 — 本棵树/本条矿脉完成。
        // 清链数据 + 流程键 → 协调行为下轮 GUI-init 自动搜索下一目标（连续作业）。
        clearChainData(data);
        clearFlowData(data);
        LmaTaskMemory.clearAllNav(maid);
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] mode={} 本轮完成，等待搜索下一目标", mode);
        return TaskResult.CONTINUE;
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

    /** 工具-方块判定 → 计算层 */
    private static boolean canHarvest(Mode mode, ItemStack tool, BlockState state) {
        return mode == Mode.ORE
                ? ToolJudge.canPickaxeMine(tool, state)
                : ToolJudge.canAxeChop(tool, state);
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

package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.sense.SenseApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemSelect;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.ConnectedBlockSearch;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HarvestTarget;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ToolJudge;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;

/**
 * v36: 连锁采集执行器 — 砍树 (collect_wood) / 挖矿 (collect_ore)。
 *
 * <p>数据源: BlockScanner (服务端扫描, 客户机不扫描) + ConnectedBlockSearch (BFS 连块脉) +
 * 跳过集 (失败目标暂时跳过, TTL 过期重试)。
 *
 * <p>v79.23: 走路全 TLM 导航 (maid_useful_task 模式, 自研引擎 12 文件退役)。
 *
 * <p>v79.26.6: 挖矿行为参数配置化 — 垂直挖穿深度 / 垫柱触发高度 / 面前挖穿距离 /
 * 导航看门狗 移 ActiveTaskConfig chain_harvest 组 (跳过集 TTL v79.26.7 退役回分档死值)。
 *
 * <p>v79.26.7: 寻路收编 {@link PathingApi#navigate} — 垂直挖穿/面前挖穿/垫柱兜底
 * 从本类内联逻辑提取 (DigThroughCoordinator + BlockUpCoordinator), 管道只调导航门面。
 * 档位 (TLM/AGGRESSIVE) 生效 (v79.26.7 用户裁定: 扫同激进, 挖分档)。
 *
 * <p>v79.26.8e: 用户裁定简化 — "把寻路全用TLM的把, 不用垫方块了, 只要挖上下能挖到的
 * 就行了, 不过向下的可以多挖几格, 还有危险判断, 然后那个寻路全局设置就没用了, 子任务
 * 的寻路设置也没用了": 垫柱 (BlockUpCoordinator) 全删 / 面前挖穿 (digFront) 删 /
 * 档位 (PathingModes) 全删 (navigate 去 mode 参数, 全局+子任务 GUI 退役) — 走路全 TLM,
 * 只挖垂直: 脚下 digVertical (深度默认 6, 用户 "向下的可以多挖几格") + 头顶 2.83 球直接
 * 开脉; 危险堵护 (DangerGuardCoordinator) 保留; 跳过集 TTL 统一 60t (原 TLM 档值)。
 */
public final class ChainHarvestExecute {

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

    private static final int TOOL_RESERVE_DURABILITY = 1;
    /** 跳过集容量 (v36.3 用户定 10) */
    private static final int SKIP_MAX = 10;
    /** 跳过集 TTL 决策史 — v79.26.8e 统一 60t (档位删, 原 TLM 档值):
     *  v79.19m: FAILED 目标过期重试。原永久跳过 (仅换工具 tier 清) → 16 格内矿失败一次
     *  永远不挖 (用户: "16 格内有矿但不去挖"), 路径可能已变/可绕。
     *  v79.19o: 600 → 20 (1 秒) — 用户裁定 1 秒刷新。
     *  v79.20.1: 20 → 600 (30 秒) — **死循环实测 (错题 #119)**: 女仆在地下全 NO_PATH 场景,
     *  1 秒 TTL + 1 秒扫描 = 每 1 秒 6 目标 × 250-400ms 寻路冻结服务器 (用户实测: 互动不了,
     *  退出卡死)。30 秒重试: 路径变化慢, 每秒刷屏停止。
     *  v79.26.6: 配置化 ActiveTaskConfig.CHAIN_SKIP_TTL (默认 600) + GUI 项。
     *  v79.26.7: 配置退役回分档死值 (激进 20t ≈ 原 1 秒裁定; TLM 60t) — 用户裁定
     *  "标记不可到达的矿物集在TLM档应该是60tick刷新, 在激进档是1s刷新"。
     *  v79.26.8e: 档位删 → 统一 60 (TLM 档值, 挖不到更多长 TTL 防垃圾输出)。 */
    private static final int SKIP_TTL = 60;
    /** tick → 秒显示转换 (v79.11 硬编码收敛) */
    private static final double TICKS_PER_SECOND = 20.0;
    /** 寻路预算系数 — BlockScanner 结果预算 = radius² / 16 (v79.11 硬编码收敛) */
    private static final int SCAN_BUDGET_DIVISOR = 16;
    /** v79.19i: nearPass 3 格球轻节流 (tick) — 独立于 CHAIN_SCAN_INTERVAL, 寻路途中也扫周围矿 */
    private static final int NEAR_SCAN_INTERVAL = 5;
    /** v79.26.7: 扫描垂直范围 (±Y — 区块高 5 地下 5, 用户裁定; 无档位差异) */
    private static final int V_RANGE = 5;
    /** 任务类型名 (v79.11 路径硬编码收敛) */
    private static final String TASK_WOOD = "collect_wood";
    private static final String TASK_ORE = "collect_ore";

    private static final Map<Integer, Long> LAST_SCAN = new ConcurrentHashMap<>();
    /** v79.19i: nearPass 上次扫描 tick (3 格球, 每 5t 轻扫) */
    private static final Map<Integer, Long> LAST_NEAR_SCAN = new ConcurrentHashMap<>();
    private static final Map<Integer, SkipState> SKIPPED = new ConcurrentHashMap<>();
    /** v79.19m: 跳过条目时间戳 (pos.asLong → gameTime) — 过期重试用 */
    private static final Map<Long, Long> SKIP_AT = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> BUBBLE_TICK = new ConcurrentHashMap<>();
    /** v36.6: 无目标待机提示去重（状态翻转才再发） */
    private static final Map<Integer, Boolean> IDLE_NOTIFIED = new ConcurrentHashMap<>();
    /** v63: 上次执行模式 — 模式切换(Wood↔Ore)时全量清理避免跨任务污染 */
    private static final Map<Integer, Mode> LAST_MODE = new ConcurrentHashMap<>();

    private static final class SkipState {
        int tierLevel = Integer.MIN_VALUE;
        final LinkedHashSet<Long> positions = new LinkedHashSet<>();
    }

    private ChainHarvestExecute() {}

    /** TaskRegistry.TaskExecutor 入口 */
    public static TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                     CompoundTag data, Mode mode) {
        if (TaskStateManager.isCancelled(maid)) return TaskResult.FAILED;
        // v79.20.5: 防御 — 任务已终结/未挂载 (clearAll 后 getTask 空, 或非本模式) →
        // 不执行扫描/寻路 (覆盖 IExecutor 直调路径 — 绕过 TaskStateMachine.tick 的入口;
        // 错题 #124 同类)
        String flow = FlowTaskData.getTask(maid);
        if (flow.isEmpty() || (!TASK_WOOD.equals(flow) && !TASK_ORE.equals(flow))) {
            return TaskResult.FAILED;
        }

        // v63: 模式切换(Wood↔Ore)时全量清理残留
        int id = maid.getId();
        Mode prevMode = LAST_MODE.get(id);
        if (prevMode != null && prevMode != mode) {
            clearChainData(data);
            SKIPPED.remove(id);
            LAST_SCAN.remove(id);
            IDLE_NOTIFIED.remove(id);
            BUBBLE_TICK.remove(id);
            PathingApi.clearNav(maid); // v79.8: 模式切换清导航 (含 v79.26.7 导航看门狗记录)
            DangerGuardCoordinator.clear(maid); // v79.26.7: 模式切换清危险堵护状态
        }
        LAST_MODE.put(id, mode);

        // v79.26.8d: 卡方块自救 — 被埋/卡住先瞬破窒息块脱困 (maid_useful_task
        // MaidSelfRescueBehavior 移植), 优先于一切动作 (工具选择/导航/垫柱/开脉);
        // 挖掉后 AABB 不再相交, 下轮正常执行
        if (SelfRescueCoordinator.tick(world, maid)) {
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        HarvestTarget target = mode.target;
        ItemStack tool = maid.getMainHandItem();
        // v77.5: 自动换最优工具 (全背包扫描 — ORE 选 tier 最高可用镐; WOOD 选 tier 最高可用斧)
        // v79.13: ORE 条件对齐 WOOD — 非镐即可用也换 (原仅坏耐久才换 → 拿剑/斧时全矿被 canHarvest 过滤)
        // v79.19p: ORE 加铲豁免 — 泥土/沙属采集目标, 手拿好铲不换镐 (防每轮换镐↔铲抖动);
        // 具体工具类型由扫描后的 ensureToolFor 按目标方块决定
        if (mode == Mode.ORE && !((ToolStateReader.isPickaxe(tool) || ToolStateReader.isShovel(tool))
                && ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY))) {
            var pick = ItemSelect.selectBest(maid,
                    s -> ToolStateReader.isPickaxe(s) && ToolJudge.isToolUsable(s, TOOL_RESERVE_DURABILITY),
                    s -> ToolStateReader.getTierLevel(s));
            if (pick.isPresent()) {
                // v79.19q: 旧工具放回背包 (原 setItemInHand 直接替换 → 旧工具丢失,
                // 用户实测: "铲子直接被替换没了, 只剩稿子了")
                swapTool(maid, pick.get().slot());
                tool = pick.get().value();
            }
        } else if (mode == Mode.WOOD && !ToolStateReader.isAxe(tool)) {
            var pick = ItemSelect.selectBest(maid,
                    s -> ToolStateReader.isAxe(s) && ToolJudge.isToolUsable(s, TOOL_RESERVE_DURABILITY),
                    s -> ToolStateReader.getTierLevel(s));
            if (pick.isPresent()) {
                // v79.19q: 旧工具放回背包 (原 setItemInHand 直接替换 → 旧工具丢失,
                // 用户实测: "铲子直接被替换没了, 只剩稿子了")
                swapTool(maid, pick.get().slot());
                tool = pick.get().value();
            }
        }

        if (mode == Mode.ORE && !ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY)) {
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        if (data.contains(KEY_QUEUE)) {
            return charge(world, maid, data, target, tool);
        }

        BlockState state = world.getBlockState(pos);
        if (target.matches(state) && allowed(maid, state)) {
            return tryStartVein(world, maid, pos, data, target, tool);
        }
        return idleScan(world, maid, data, target, tool, false);
    }

    private static TaskResult tryStartVein(ServerLevel world, EntityMaid maid, BlockPos pos,
                                           CompoundTag data, HarvestTarget target, ItemStack tool) {
        var skip = skipSet(maid, tool);
        BlockState state = world.getBlockState(pos);

        if (skip.contains(pos.asLong()) || !allowed(maid, state)) {
            addSkip(skip, pos.asLong(), world.getGameTime());
            return idleScan(world, maid, data, target, tool, true);
        }

        // v79.19p: 目标驱动换工具 — 先按方块合适类型换 (泥土→铲/矿→镐/树→斧)
        // 再判 canHarvest (手工具可能不匹配此目标类型, 如拿镐挖泥土换铲)
        ensureToolFor(maid, state);
        tool = maid.getMainHandItem();

        if (!target.canHarvest(tool, state) || !target.validAt(world, pos)) {
            addSkip(skip, pos.asLong(), world.getGameTime());
            return idleScan(world, maid, data, target, tool, true);
        }

        // v79.26.7: 危险堵护 — 开脉前检查目标 6 侧液体 (岩浆/水), 有 → 堵上方块再挖
        // (Baritone plausibleToBreak/avoidAdjacentBreaking 语义升级: Baritone 直接放弃,
        // 用户裁定 "岩浆等危险需要堵上方块的"; 防岩浆流出烫伤 / 水冲走掉落物)。
        // v79.26.8e: 保留 (用户 "还有危险判断") — 垫柱删后堵护独立, 无依赖。
        switch (DangerGuardCoordinator.tick(world, maid, pos)) {
            case RUNNING -> {
                // 堵护中 — 每 tick 放一块 (多液体逐轮), 下轮重检; 看门狗 240t 超时 FAILED
                keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
            case FAILED -> {
                // 无方块/无实心邻格可点/超时 → 跳过该目标 (跳过集 TTL 过期重试)
                DangerGuardCoordinator.clear(maid);
                addSkip(skip, pos.asLong(), world.getGameTime());
                return idleScan(world, maid, data, target, tool, true);
            }
            case DONE -> {
                // 已无液体 → 开脉 (下轮不再查 — 液体已被方块覆盖, 挖脉安全)
            }
            default -> { }
        }

        List<BlockPos> vein = ConnectedBlockSearch.findConnected(world, pos,
                target.veinPredicate(state),
                ActiveTaskConfig.CHAIN_MAX_BLOCKS.get(), maid.blockPosition(), maxDistSqr());
        if (vein.isEmpty()) {
            addSkip(skip, pos.asLong(), world.getGameTime());
            return idleScan(world, maid, data, target, tool, true);
        }

        if (target.consumesDurability(tool)) {
            int budget = ToolStateReader.getRemainingDurability(tool) - TOOL_RESERVE_DURABILITY;
            if (budget < vein.size()) {
                vein = vein.subList(0, Math.max(0, budget));
            }
            if (vein.isEmpty()) {
                return idleScan(world, maid, data, target, tool, true);
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
        bubble(maid, target.label() + " " + queue.length + " 块 蓄力 "
                + String.format("%.1f", chargeTicks / TICKS_PER_SECOND) + " 秒", true);
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    private static TaskResult charge(ServerLevel world, EntityMaid maid,
                                     CompoundTag data, HarvestTarget target, ItemStack tool) {
        long now = world.getGameTime();
        long end = data.getLong(KEY_CHARGE_END);

        if (now < end) {
            if (now % 5 == 0) maid.swing(InteractionHand.MAIN_HAND);
            bubble(maid, target.label() + " 蓄力 "
                    + String.format("%.1f", (end - now) / TICKS_PER_SECOND) + " 秒", false);
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        long[] queue = data.getLongArray(KEY_QUEUE);
        int broken = 0;
        for (long l : queue) {
            BlockPos blockPos = BlockPos.of(l);
            BlockState state = world.getBlockState(blockPos);
            if (state.isAir() || !target.matches(state)) continue;
            // v79.14: 破块限 3 格 3D (原 32 格远程破不合理); 脉内其余块留待下轮
            if (blockPos.distSqr(maid.blockPosition()) > VanillaConstants.ARRIVE_DIST_SQR) continue;
            if (!target.canHarvest(tool, state)) continue;
            if (!maid.canDestroyBlock(blockPos)) continue;
            if (maid.destroyBlock(blockPos)) broken++;
        }
        if (broken > 0 && target.consumesDurability(tool)) {
//? if 1.20.1 {
            tool.hurtAndBreak(broken, maid,
                    e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
//?} else {
            tool.hurtAndBreak(broken, maid, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
        }
        maid.swing(InteractionHand.MAIN_HAND);
        clearChainData(data);
        LAST_SCAN.remove(maid.getId());
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] {} 整脉破坏 {} 块", target.label(), broken);
        bubble(maid, target.label() + " 完成 " + broken + " 块", true);
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    private static TaskResult idleScan(ServerLevel world, EntityMaid maid, CompoundTag data,
                                       HarvestTarget target, ItemStack tool, boolean immediate) {
        long now = world.getGameTime();
        int id = maid.getId();
        // v79.19i: 寻路途中也扫周围 3 格矿 (用户: "即便寻路没到指定地点依旧要搜寻周围 3 格") —
        // 独立轻节流, 不卡在 CHAIN_SCAN_INTERVAL; 有矿直接开脉 (挖脉中走 charge 分支不进来)
        // v79.19p: 传 scanTool (默认钻石镐/斧) — 存在性判断与手工具解耦, 挖完泥土拿铲也能扫到矿
        BlockPos near = nearPass(world, maid, target, scanTool(target));
        if (near != null) {
            return tryStartVein(world, maid, near, data, target, tool);
        }
        if (!immediate) {
            long last = LAST_SCAN.getOrDefault(id, 0L);
            if (last != 0 && last <= now && now - last < ActiveTaskConfig.CHAIN_SCAN_INTERVAL.get()) {
                keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
        }
        LAST_SCAN.put(id, now);

        // v79.26.8e: 档位删 (用户: "寻路全用TLM... 全局设置/子任务设置都没用了") —
        // 扫描半径 16 + 垂直 ±5 唯一行为; v79.26.8: 到达判定 1 格邻域 (用户:
        // "卡极限距离的就算在三格内也要走过去, 至少要走到矿旁边") — 直接开脉门
        // 2.83 球 (DIG_DIRECT_DIST_SQR, 攻击距离 3.0 留余量): 头顶 2 格 (3D 2)
        // 仍直接挖, 水平 3 格外 (3D 3 边界) 走寻路走近, 头顶 3+ 格走垫柱 (触发 ≥3)
        // — v79.26.8e 垫柱删: 头顶 3+ 格 TLM 不可达 → 跳过集 (60t 过期重试)
        int radius = searchRadius(maid);
        // v79.19p: 扫描用 scanTool (默认钻石镐/斧) 做 canHarvest — 目标存在性判定与手工具解耦
        BlockPos next = findNearestValid(world, maid, target, scanTool(target), radius);
        if (next == null) {
            if (!IDLE_NOTIFIED.getOrDefault(id, false)) {
                IDLE_NOTIFIED.put(id, true);
                bubble(maid, "附近没有可采集的" + target.label() + "目标", true);
            }
            LittleMaidMoreAction.LOGGER.debug("[ChainHarvest] {} 空闲扫描无目标 radius={}",
                    target.label(), radius);
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }
        IDLE_NOTIFIED.put(id, false);
        // v79.19g: 到达判定 2 格 (用户: "周围两格没有矿物就搜索其他矿物";
        // 女仆攻击距离 3 格 — 2 格内直接开脉, 否则寻路走过去)
        // v79.19i: 2 格 → 3 格 (与破块门 ARRIVE_DIST_SQR 一致)
        // v79.19n: → 1 格邻域 (3x3x3, 与寻路目标 oneAway 同一语义 — 用户: "寻路就走到
        // 目标一格旁边或头上, 不要再卡在极限格"; 原 3 格球: 寻路 ARRIVED 但整格 distSqr
        // 可能 >9 → 破块挖不到 → 卡死) — 到达后 3D 距离 ≤ 1.73 < 破块门 3 格, 稳挖
        // v79.19o: 恢复 3 格球 (用户: "明明头上三个就是矿") — 到达判定 ≠ 寻路目标:
        // 头顶 3 格矿 distSqr 9 ≤ 9 在破块门内可直接挖; 原 1 格邻域把头顶 3 格排除 →
        // 去寻路 → 绕不过 → FAILED → 跳过集 → 发呆。卡极限格问题已由寻路侧 oneAway
        // 解决 (走动必到旁 1 格/头顶), 到达判定 3 格球不冲突
        // v79.26.8: 3 格球 → 2.83 球 (DIG_DIRECT_DIST_SQR=8) — v79.19o 的 3 格球回归
        // 后用户实测水平 3 格外 (3D 3 = 攻击距离 3.0 边界) 卡极限 "不过去"; 2.83 球
        // 留余量: 头顶 2 格 (3D 2) 直接挖保留, 水平 3 格外/头顶 3+ 格走寻路 (走近/垫柱)
        // v79.26.8e: 垫柱删 — 头顶 3+ 格 TLM 不可达 → 跳过集 (60t 过期重试)
        // v79.19p: 目标驱动换工具 (用户: "挖泥土会换铲子, 挖矿换镐子") — 扫描谓词用
        // scanTool 与手工具解耦, 找到目标后按方块合适类型换 (换后 tool 更新, 后续
        // 挖穿/寻路/开脉全用新工具; 换后挖不了由 tryStartVein canHarvest + skip 兜底)
        ensureToolFor(maid, world.getBlockState(next));
        tool = maid.getMainHandItem();
        BlockPos foot = maid.blockPosition();
        if (next.distSqr(foot) <= VanillaConstants.DIG_DIRECT_DIST_SQR) {
            return tryStartVein(world, maid, next, data, target, tool);
        }
        // v79.26.7: 完整寻路 API — TLM 导航 + 垂直挖穿/面前挖穿/垫柱兜底全收编
        // PathingApi.navigate (新管道复用同一套寻路, 不再内联)。决策史: v79.19o 垂直挖穿
        // (TLM 到不了地下站着看) / v79.23 垫柱+面前挖穿 / v79.26.4 去档位门控 (兜底全档位
        // 可用) / v79.26.6 行为参数配置化 / v79.26.7 双门控防误挖 (水平 ≤3 格 + canReach
        // 预检, 可达走正常路) + 收编 API + 档位语义重定义 (两档扫同激进, TLM 纯走只挖
        // 3 格球内 / AGGRESSIVE 垫柱挖穿全开)
        // v79.26.8: navigate 去档位门控回归 + 到达 1 格邻域 + 垫柱触发 ≥ (详见 PathingApi)
        // v79.26.8e: navigate 去 mode 参数 (垫柱/面前挖穿/档位全删 — 用户裁定简化)
        switch (PathingApi.navigate(world, maid, next)) {
            case REACHED -> {
                // 已进 3 格球 — 下轮 execute 顶部命中直接开脉 (idleScan 兜底)
            }
            case WALKING, DIGGING -> {
                keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
            case FAILED -> {
                // TLM 不可达/导航超时 → 跳过 (跳过集 TTL 过期重试)
                LittleMaidMoreAction.LOGGER.info("[ChainHarvest] 女仆 {} 寻路放弃 目标 {} 跳过",
                        maid.getId(), next);
                addSkip(skipSet(maid, maid.getMainHandItem()), next.asLong(), world.getGameTime());
                return idleScan(world, maid, data, target, tool, true);
            }
        }
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    /** v67.3: 采集方块黑白名单 (方块id). v67.8: per-maid pipelineConfig 非空覆盖全局 */
    private static boolean allowed(EntityMaid maid, BlockState state) {
        // v79.20.5: 防御 — 任务已终结 (超时 clearAll, 错题 #124) 时 getTask 为空 → get 为 null;
        // maidList 对 null cfg 也 NPE — 空任务走全局默认名单 (主修: GameTickPipelineManager 超时 return)
        String task = FlowTaskData.getTask(maid);
        var h = task.isEmpty() ? null : TaskRegistry.get(task);
        CompoundTag cfg = h == null ? null : h.pipeline().pipelineConfig(maid);
        List<String> black = ItemFilters.effective(cfg == null ? List.of() : ItemFilters.maidList(cfg, ItemFilters.KEY_BLACKLIST),
                ActiveTaskConfig.COLLECT_BLACKLIST.get());
        List<String> white = ItemFilters.effective(cfg == null ? List.of() : ItemFilters.maidList(cfg, ItemFilters.KEY_WHITELIST),
                ActiveTaskConfig.COLLECT_WHITELIST.get());
        return ItemFilters.isAllowed(state, black, white);
    }

    private static int searchRadius(EntityMaid maid) {
        return maid.hasRestriction()
                ? Math.max(4, (int) maid.getRestrictRadius())
                : PassiveTaskConfig.ENV_DEFAULT_RADIUS.get();
    }

    /**
     * v79.19p: 目标驱动换工具 (用户: "挖泥土会换铲子, 挖矿换镐子") — 按方块合适类型
     * (泥土/沙→铲, 矿/石→镐, 原木→斧) 从全背包选 tier 最高可用工具。
     * 当前手工具已合适且可用 → 不动; 背包无合适工具 → 不动 (空手慢速兜底,
     * 由调用方 canHarvest 门控)。v79.26.7: 挖穿协调器 (DigThroughCoordinator) 复用;
     * v79.26.8d: 自救 (SelfRescueCoordinator) 复用。
     */
    static void ensureToolFor(EntityMaid maid, BlockState state) {
        ToolJudge.ToolType need = ToolJudge.suitableToolType(state);
        if (need == ToolJudge.ToolType.NONE) return;
        ItemStack tool = maid.getMainHandItem();
        if (ToolJudge.isSuitableTool(tool, state)
                && ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY)) return;
        var pick = ItemSelect.selectBest(maid,
                s -> ToolJudge.matchesToolType(s, need) && ToolJudge.isToolUsable(s, TOOL_RESERVE_DURABILITY),
                s -> ToolStateReader.getTierLevel(s));
        if (pick.isPresent()) {
            // v79.19q: 旧工具放回背包 (原 setItemInHand 直接替换 → 旧工具丢失)
            swapTool(maid, pick.get().slot());
        }
    }

    /**
     * v79.19q: 换主手工具 — 新工具从背包槽位提取 (extractItem 保证写回), 旧工具放回该槽位。
     * 原 {@code setItemInHand(新工具)} 直接替换主手 → 旧工具不自动放回 → 永久丢失
     * (用户实测: "铲子直接被替换没了, 只剩稿子了"; 铲↔镐来回换时每轮丢一个)。
     * slot = ItemSelect 返回的槽位索引 (与 getAvailableInv(true) 槽位一一对应)。
     */
    static void swapTool(EntityMaid maid, int slot) {
        var inv = maid.getAvailableInv(true);
        if (slot < 0 || slot >= inv.getSlots()) return;
        ItemStack newTool = inv.extractItem(slot, 1, false);
        if (newTool.isEmpty()) return;
        ItemStack old = maid.getMainHandItem();
        if (!old.isEmpty()) inv.insertItem(slot, old, false);
        maid.setItemInHand(InteractionHand.MAIN_HAND, newTool);
    }

    /** v79.19p: 扫描谓词用模式默认工具 (与手工具解耦) — 手拿铲也能扫到矿 (挖完泥土换目标),
     *  手拿镐也能扫到泥土。钻石镐/斧: 等级门槛够绝大多数矿; 等级不够的矿由
     *  tryStartVein 真实工具 canHarvest + skip 兜底 */
    private static ItemStack scanTool(HarvestTarget target) {
        return target == HarvestTarget.ORE
                ? new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)
                : new ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE);
    }

    /** v67.4: 采集距离上限平方 (配置驱动, 默认 32 格) */
    private static double maxDistSqr() {
        int radius = ActiveTaskConfig.CHAIN_MAX_DISTANCE.get();
        return (double) radius * radius;
    }

    @Nullable
    private static BlockPos findNearestValid(ServerLevel world, EntityMaid maid,
                                             HarvestTarget target, ItemStack tool, int radius) {
        // v79.19m: 跳过集过期清理 — FAILED 目标过期后重试 (用户: "16 格内有矿但不去挖",
        // 原永久跳过永久不知道; 路径可能因挖脉/搭路变化)
        // v79.19p: skip 集按真实手工具分组 (tool 参数已改为 scanTool — 扫描谓词工具)
        // v79.26.6: TTL 配置化 (ActiveTaskConfig.CHAIN_SKIP_TTL, 原硬编码 600t; 决策史见常量区)
        // v79.26.7: 配置退役回分档死值 — TLM 60t / 激进 20t (用户裁定: 激进几乎总能挖到
        // → 快速重试; TLM 挖不到更多 → 长 TTL 防垃圾输出) + 垂直范围 ±5
        // v79.26.8e: 档位删 → 统一 SKIP_TTL=60
        LinkedHashSet<Long> skip = skipSet(maid, maid.getMainHandItem());
        long now = world.getGameTime();
        skip.removeIf(l -> {
            long t = SKIP_AT.getOrDefault(l, 0L);
            if (t != 0 && now - t > SKIP_TTL) {
                SKIP_AT.remove(l);
                return true;
            }
            return false;
        });
        // v79.5: 泛化最近搜索提升到 API 面 (SenseApi.findNearestBlock — BlockScanner + skip 集)
        return SenseApi.findNearestBlock(maid,
                radius, V_RANGE, s -> target.matches(s) && allowed(maid, s) && target.canHarvest(tool, s),
                skip, radius * radius / SCAN_BUDGET_DIVISOR);
    }

    /**
     * v79.19i: 3 格球近扫 (x/z/y ∈ [-3,3], 3D distSqr ≤ 9 ≈ 113 格) — 寻路途中经过的矿直接开脉。
     * SenseApi.findNearestBlock 的 radius 是 chunk 半径语义 (radius/16+1, 最小 1 chunk=16 格) —
     * 不可用于 3 格近扫。只做快过滤 (skip/matches/allowed/canHarvest); validAt 慢校验留给
     * tryStartVein (失败已 addSkip + immediate 重扫)。
     */
    @Nullable
    private static BlockPos nearPass(ServerLevel world, EntityMaid maid, HarvestTarget target, ItemStack tool) {
        long now = world.getGameTime();
        int id = maid.getId();
        long last = LAST_NEAR_SCAN.getOrDefault(id, 0L);
        if (last != 0 && now - last < NEAR_SCAN_INTERVAL) {
            return null;
        }
        LAST_NEAR_SCAN.put(id, now);

        // v79.19p: skip 集按真实手工具分组 (tool 参数 = scanTool 扫描谓词工具)
        LinkedHashSet<Long> skip = skipSet(maid, maid.getMainHandItem());
        BlockPos foot = maid.blockPosition();
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d > VanillaConstants.ARRIVE_DIST_SQR) continue;
                    BlockPos p = foot.offset(dx, dy, dz);
                    if (skip.contains(p.asLong())) continue;
                    BlockState state = world.getBlockState(p);
                    if (!target.matches(state) || !allowed(maid, state) || !target.canHarvest(tool, state)) continue;
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
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

    private static void addSkip(LinkedHashSet<Long> set, long pos, long now) {
        if (set.size() >= SKIP_MAX) {
            long oldest = set.iterator().next();
            set.remove(oldest);
            SKIP_AT.remove(oldest);
        }
        set.add(pos);
        SKIP_AT.put(pos, now);
    }

    private static void keepAlive(ServerLevel world, EntityMaid maid) {
        // v79.5: 三件套样板收敛 (api/navigation/NavigationUtil)
        NavigationUtil.keepAlive(world, maid);
    }

    /** 清除连锁状态 key（闭环，含旧版残留 key） */
    public static void clearChainData(CompoundTag data) {
        data.remove(KEY_QUEUE);
        data.remove(KEY_CHARGE_END);
        data.remove(KEY_IDX_LEGACY);
        data.remove(KEY_TICK_LEGACY);
    }

    /**
     * v79.27: 任务终结/实体卸载时清理 — 静态缓存 (maid.getId() key) + NBT 根键闭环。
     * <p>静态 map 条目随任务取消/女仆卸载清理, 防长期服务器内存增长 + MC 实体 ID 复用串扰
     * (新女仆继承旧跳过集不挖矿 / 气泡节流错乱 / LAST_MODE 跨任务残留)。
     * <p>调用方: ChainWood/ChainOre 管线 cleanup (终结路径汇聚) + EntityCleanupListener (实体卸载)。
     */
    public static void clearMaidState(EntityMaid maid) {
        int id = maid.getId();
        SkipState skip = SKIPPED.remove(id);
        if (skip != null) {
            // 跳过集时间戳连带清 (SKIP_AT 全局共享 — 其他女仆的条目不受影响, 由 addSkip 重写)
            for (long pos : skip.positions) {
                SKIP_AT.remove(pos);
            }
        }
        LAST_SCAN.remove(id);
        LAST_NEAR_SCAN.remove(id);
        BUBBLE_TICK.remove(id);
        IDLE_NOTIFIED.remove(id);
        LAST_MODE.remove(id);
        clearChainData(maid.getPersistentData());
    }

    private static void bubble(EntityMaid maid, String text, boolean force) {
        int id = maid.getId();
        long now = maid.level().getGameTime();
        if (!force) {
            long last = BUBBLE_TICK.getOrDefault(id, 0L);
            if (last != 0 && last <= now && now - last < ActiveTaskConfig.CHAIN_SCAN_INTERVAL.get()) return;
        }
        BUBBLE_TICK.put(id, now);

        MaidChatBubbleApi.showProgress(maid, text, 0d);
    }
}

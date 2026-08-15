package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HarvestTarget;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.MaidFavorability;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ToolJudge;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.ConnectedBlockSearch;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.HandSwap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>v79.57: 脚下挖穿 (digVertical) 退役 — 下挖挖泥土换主手铲 → 主手非镐 → ORE 管线
 * 卡住 (用户实测); 只挖裸露表面矿 + 头顶 ≤6 格 digUp 保留 (用户裁定), 连锁采集不变。
 *
 * <p>v79.52: 状态管理 per-maid 化 — 原 6 张跨女仆静态 map (LAST_SCAN/LAST_NEAR_SCAN/
 * SKIPPED/SKIP_AT/IDLE_NOTIFIED/LAST_MODE) 收编为 {@link MaidChainState} 单对象 +
 * UUID 注册表 (SKIP_AT 全局共享误删 TTL / int 实体 ID 复用串扰 / 清理散落 3 处 三缺陷根治)。
 *
 * <p>v79.53: 挖矿管线审计修复 — ① 扫描垂直范围对齐挖穿深度 (vRange, 双向挖穿解锁)
 * ② 大矿脉按可达球裁剪 (蓄力=实破量, 不再白等) ③ 寻路放弃日志 INFO→DEBUG (风暴)
 * ④ 背包满检查 20t 节流 (原每 tick 全背包遍历) ⑤ 危险堵护看门狗 240→60 对齐跳过集 TTL
 * ⑥ destroyBlock 不 fire BreakEvent (领地 mod 拦不住) — 用户裁定保持现状 (TLM 原版同行为)。
 *
 * <p>v79.56: 跳过集刷新死循环修复 (错题 #184) + 结构整理 — 已跳过目标不再每 tick
 * addSkip 刷新时间戳 (TTL 永 false → 永久跳过) + navigate FAILED 气泡反馈;
 * 失败出口收敛 failAndSkip 单点, 方法重排 (行为零变化)。
 * <p>v79.57: 工具判断收拢 ToolJudge (isModeOptimal/selectBestForMode/selectBestForBlock/isSuitableUsable) — ensureBestTool 删内联, ensureToolFor 瘦身。
 * <p>v79.58: 挖矿管线修正批次 (用户裁定) — ① 可挖掘距离 3→4 格 (MINE_DIG_DIST_SQR=16,
 * TLM destroyBlock 无距离限制实测, 3 格边界抖动白蓄力) ② charge 破 0 块失败出口
 * (原无 skip → 死循环看着, 用户实测) ③ 工具等级不够/执行中阶段气泡 ④ 无目标不气泡
 * 只 DEBUG log (有矿在跳过集时误报误导) ⑤ 自救迁被动 self_rescue (入口删)。
 * <p>v79.61 架构批 3a (C1): 纯决策内核抽取 {@link ChainHarvestMath} (蓄力时长/耐久预算裁剪/
 * 耐久消耗乘区/扫描垂直范围/扫描预算) — 零 MC 依赖纯 JVM 可测, 行为零变化。
 * <p>v79.61x execute 瘦身样本 4: 扫描决策域抽至 {@link ChainScan} (空闲扫描/近扫/最近目标/
 * 跳过集/采集名单/扫描参数) — 本类只留主循环三件套 (守卫链/开脉/蓄力) + 清理/气泡/工具辅助。
 */
public final class ChainHarvestExecute {

    public enum Mode {
        WOOD(HarvestTarget.WOOD), ORE(HarvestTarget.ORE);
        final HarvestTarget target;
        Mode(HarvestTarget target) { this.target = target; }
    }

    // ── PersistentData keys (set → remove 闭环; IDX/TICK 为旧版残留一并清理) ──
    public static final String KEY_QUEUE = TaskKeys.CHAIN_QUEUE;
    public static final String KEY_CHARGE_END = TaskKeys.CHAIN_CHARGE_END;
    public static final String KEY_PHASE = TaskKeys.CHAIN_PHASE;
    private static final String KEY_IDX_LEGACY = "lma_chain_idx";
    private static final String KEY_TICK_LEGACY = "lma_chain_tick";

    /** 工具耐久保留值 (P-14: 与 HarvestTarget 双常量合并 — 单点契约) */
    private static final int TOOL_RESERVE_DURABILITY = HarvestTarget.TOOL_RESERVE_DURABILITY;
    /** tick → 秒显示转换 (硬编码收敛) */
    private static final double TICKS_PER_SECOND = 20.0;
    /** 气泡节流 40t (2 秒) — 独立于 CHAIN_SCAN_INTERVAL (2026-08-11c: TLM 1.5.3
     *  ChatBubbleRegister.copy() 浅拷贝回归 → 高频气泡变更触发并发竞态断线 (错题根因分析),
     *  原复用扫描间隔 20t 过密; force 气泡 (开脉/完成/失败) 不受限) */
    private static final int CHAIN_BUBBLE_INTERVAL = 40;
    /** 背包满检查节流 (tick) — v79.53: 原每 tick 全背包遍历 */
    private static final int INV_CHECK_INTERVAL = 20;

    /** 扫描垂直范围 — ORE 对齐头顶挖穿深度 (v79.53: 原硬编码 ±5, CHAIN_DIG_DOWN_DEPTH 1-8 时
     *  digUp 的 6-8 段首次扫描不可见 → 头顶挖穿配置失效; v79.57 脚下挖穿退役后仍对齐 digUp);
     *  WOOD 放宽 ±12 (2026-08-11c F1: 树高无上限 — 云杉/丛林 10-30 格, 原 ±6
     *  树顶原木扫描不可见 → 砍一半残留浮空原木 + 误报无目标) */
    static int vRange(HarvestTarget target) {
        // v79.61 批3a: 纯内核收编 ChainHarvestMath (配置读取留在编排层)
        return ChainHarvestMath.vRange(target == HarvestTarget.WOOD, ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get());
    }

    /** 任务类型名 (路径硬编码收敛) */
    private static final String TASK_WOOD = "collect_wood";
    private static final String TASK_ORE = "collect_ore";

    /** 采集相位 (v79.61x 状态机化) — SCAN 闲逛扫描 (默认) / CHARGE 蓄力等待;
     *  DIG 是 CHARGE 到期同 tick 事务不持久化; 旧档无 phase 键但队列存在 → CHARGE (兼容) */
    public enum Phase { SCAN, CHARGE }

    private static Phase phaseOf(CompoundTag data) {
        if (data.contains(KEY_PHASE)) {
            int ord = data.getInt(KEY_PHASE);
            return ord == Phase.CHARGE.ordinal() ? Phase.CHARGE : Phase.SCAN;
        }
        // 旧存档兼容: 无 phase 键时以队列存在为据 (与状态机化前语义逐字一致)
        return data.contains(KEY_QUEUE) ? Phase.CHARGE : Phase.SCAN;
    }

    /** per-maid 状态注册表 (v79.52: 原 6 张跨女仆静态 map 收编 — SKIP_AT 全局共享
     *  误删 TTL / int 实体 ID 复用串扰 / 清理散落 3 处 三缺陷根治; UUID 稳定 + 线程安全) */
    private static final Map<UUID, MaidChainState> STATES = new ConcurrentHashMap<>();

    /** 取女仆状态 — 懒建实例 (不持有 EntityMaid 引用, 防强引用表阻止实体 GC) */
    static MaidChainState state(EntityMaid maid) {
        return STATES.computeIfAbsent(maid.getUUID(), k -> new MaidChainState());
    }

    /** 采集方块黑白名单 (方块id); per-maid pipelineConfig 非空覆盖全局。
     *  (2026-08-15 依赖方向归位: 原 ChainScan.allowed — task 配置面读取留在协调器,
     *   消除 ChainScan 对 task/api 的反向依赖) */
    static boolean allowed(EntityMaid maid, BlockState state) {
        // 防御 — 任务已终结 (超时 clearAll, 错题 #124) 时 getTask 为空 → get 为 null;
        // maidList 对 null cfg 也 NPE — 空任务走全局默认名单 (主修: GameTickPipelineManager 超时 return)
        String task = FlowTaskData.getTask(maid);
        var h = task.isEmpty() ? null : TaskRegistry.get(task);
        // 配置维度拆分 — 未实现 TaskConfigurable 的管线走全局默认名单
        CompoundTag cfg = h == null || !(h.pipeline() instanceof TaskConfigurable c)
                ? null : c.pipelineConfig(maid);
        var lists = ItemFilters.effectivePair(cfg == null ? new CompoundTag() : cfg,
                ActiveTaskConfig.COLLECT_BLACKLIST.get(), ActiveTaskConfig.COLLECT_WHITELIST.get());
        return ItemFilters.isAllowed(state, lists.get(0), lists.get(1));
    }

    private ChainHarvestExecute() {}

    // ── 主流程: 每 tick 执行 ──

    /** TaskRegistry.TaskExecutor 入口 */
    public static TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                     CompoundTag data, Mode mode) {
        if (TaskStateManager.isCancelled(maid)) return TaskResult.FAILED;
        // 防御 — 任务已终结/未挂载 (clearAll 后 getTask 空, 或非本模式) →
        // 不执行扫描/寻路 (覆盖 IExecutor 直调路径 — 绕过 TaskStateMachine.tick 的入口;
        // 错题 #124 同类)
        String flow = FlowTaskData.getTask(maid);
        if (flow.isEmpty() || (!TASK_WOOD.equals(flow) && !TASK_ORE.equals(flow))) {
            return TaskResult.FAILED;
        }

        // 模式切换(Wood↔Ore)时全量清理残留 — 状态对象重建 (v79.52: 原 4 表逐清 +
        // SKIP_AT 连带清收敛为 1 行; 错题 P-2/P-3 语义由 per-maid 归属根治)
        MaidChainState st = state(maid);
        if (st.lastMode != null && st.lastMode != mode) {
            clearChainData(data);
            STATES.remove(maid.getUUID());
            st = state(maid);
            PathingApi.clearNav(maid); // 模式切换清导航 (含导航看门狗记录)
            DangerGuardCoordinator.clear(maid); // 模式切换清危险堵护状态
        }
        st.lastMode = mode;

        // v79.58: 自救统一被动 (self_rescue) — 掉血触发, 被埋瞬破; 主动任务暂停数据保留 (删原每 tick 入口)

        HarvestTarget target = mode.target;
        // 自动换最优工具 (v79.57 判断收拢 ToolJudge): ORE 全背包选 tier 最高可用镐
        // (非镐/铲无条件换 — isToolUsable 只门控"手拿镐/铲"分支; 拿剑/斧时全矿被 canHarvest 过滤);
        // 手拿好铲不换 (泥土/沙属采集目标, 防每轮换镐↔铲抖动); WOOD 选 tier 最高可用斧;
        // 具体工具类型由扫描后的 ensureToolFor 按目标方块决定
        ItemStack tool = maid.getMainHandItem();
        if (!ToolJudge.isModeOptimal(tool, mode == Mode.ORE, TOOL_RESERVE_DURABILITY)) {
            ToolJudge.selectBestForMode(maid, mode == Mode.ORE, TOOL_RESERVE_DURABILITY)
                    .ifPresent(p -> HandSwap.swapTo(maid, p.slot()));
            tool = maid.getMainHandItem();
        }

        if (mode == Mode.ORE && !ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY)) {
            // v79.57 回归 (#186): 背包无可用镐/铲 (或全坏) → 原静默站桩零反馈;
            // 一次性气泡提示 (节流 40t, 与"没有可采集目标"同款)
            bubble(maid, "没有可用的镐", false);
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        // 背包满暂停 — 20t 节流遍历 (v79.53: 原每 tick 全背包 32 槽; 满时暂停,
        // 清包后 ≤20t 恢复), 不扫描/不寻路/不挖 (条件挂起无状态)
        if (st.invCheckTick + INV_CHECK_INTERVAL <= world.getGameTime()) {
            st.hasSpace = hasInventorySpace(maid);
            st.invCheckTick = world.getGameTime();
        }
        if (!st.hasSpace) {
            bubble(maid, "背包已满, 清理后继续", false);  // 节流由 ThrottleUtil (chain_bubble)
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        // 状态机化 (v79.61x): 显式相位替代"队列存在"判据 — 队列退化为数据 (蓄力目标)
        if (phaseOf(data) == Phase.CHARGE) {
            return charge(world, maid, data, target, tool);
        }

        BlockState state = world.getBlockState(pos);
        if (target.matches(state) && allowed(maid, state)) {
            return tryStartVein(world, maid, pos, data, target, tool);
        }
        return ChainScan.idleScan(world, maid, data, target, tool, false);
    }

    /** 开脉 — 守卫链验证目标, 全部通过后入队蓄力 (失败出口统一 failAndSkip) */
    static TaskResult tryStartVein(ServerLevel world, EntityMaid maid, BlockPos pos,
                                   CompoundTag data, HarvestTarget target, ItemStack tool) {
        MaidChainState st = state(maid);
        LinkedHashSet<Long> skip = ChainScan.skippedFor(maid, tool);
        BlockState state = world.getBlockState(pos);

        if (skip.contains(pos.asLong()) || !allowed(maid, state)) {
            // v79.56 (错题 #184): 已跳过不刷新时间戳 — 原每 tick addSkip → expire 永 false →
            // TTL 失效 → 目标永久跳过 (用户实测 "跳过集不能正常工作"); 已跳过也不 immediate
            // 重扫 (60t 内全被过滤, immediate 绕过节流 = 每 tick 全量扫描风暴)
            boolean firstFail = !skip.contains(pos.asLong());
            if (firstFail) {
                ChainScan.addSkip(st, pos.asLong(), world.getGameTime());
            }
            return ChainScan.idleScan(world, maid, data, target, tool, firstFail);
        }

        // 目标驱动换工具 — 先按方块合适类型换 (泥土→铲/矿→镐/树→斧)
        // 再判 canHarvest (手工具可能不匹配此目标类型, 如拿镐挖泥土换铲)
        ensureToolFor(maid, state);
        tool = maid.getMainHandItem();
        // v79.53 (#10): 换工具后重新维护跳过集 tier — 原当轮 addSkip 入旧 tier 集,
        // 下轮 skippedFor 检测新 tier 清空 → 跳过条目丢失 (换工具当轮跳过失效)
        skip = ChainScan.skippedFor(maid, tool);

        if (!target.canHarvest(tool, state)) {
            // v79.58: 工具等级不够可见反馈 — 原静默站桩 (scanTool=钻石镐 扫到高级矿,
            // 手工具等级低 → canHarvest false → 无提示反复跳过; 用户裁定 "需要提醒");
            // 节流 40t 由 bubble 内部 (chain_bubble) 兜底
            bubble(maid, "工具等级不够, 暂时跳过", false);
            return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);
        }
        if (!target.validAt(world, pos)) {
            // WOOD 非天然树 (CHAIN_WOOD_NATURE_CHECK) — 防拆建筑, 设计静默
            return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);
        }

        // 危险堵护 — 开脉前检查目标 6 侧液体 (岩浆/水), 有 → 堵上方块再挖
        // (Baritone plausibleToBreak/avoidAdjacentBreaking 语义升级: Baritone 直接放弃,
        // 用户裁定 "岩浆等危险需要堵上方块的"; 防岩浆流出烫伤 / 水冲走掉落物)。
        // 垫柱删后堵护独立, 无依赖。
        switch (DangerGuardCoordinator.tick(world, maid, pos)) {
            case RUNNING -> {
                // 堵护中 — 每 tick 放一块 (多液体逐轮), 下轮重检; 看门狗 60t 超时 FAILED (v79.53 对齐跳过集)
                keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
            case FAILED -> {
                // 无方块/无实心邻格可点/超时 → 跳过该目标 (跳过集 TTL 过期重试)
                DangerGuardCoordinator.clear(maid);
                return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);
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
            return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);
        }

        // 可达裁剪 (v79.53): 脉只留破块球内块 (v79.58: 3→4 格球 MINE_DIG_DIST_SQR) —
        // 原 queue 含全脉 (球外块留待下轮, charge 蓄力按全脉算 → 大矿脉白等 N 秒只破
        // 球内几块); 裁剪后蓄力=实破量, 球外块由重扫+移动后重新开脉覆盖 (自洽)
        // 2026-08-11c F3 (砍树检查): WOOD 跳过裁剪 — 树竖直无上限 (30 格树顶),
        // 裁剪后队列无树顶 → 砍一半残留 (连锁语义恢复: 整树入队)
        if (target != HarvestTarget.WOOD) {
            vein = vein.stream()
                    .filter(b -> b.distSqr(maid.blockPosition()) <= VanillaConstants.MINE_DIG_DIST_SQR)
                    .toList();
        }
        if (vein.isEmpty()) {
            return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);
        }

        if (target.consumesDurability(tool)) {
            int cropped = ChainHarvestMath.durabilityCropSize(vein.size(),
                    ToolStateReader.getRemainingDurability(tool), TOOL_RESERVE_DURABILITY);
            if (cropped < vein.size()) {
                vein = vein.subList(0, cropped);
            }
            if (vein.isEmpty()) {
                return ChainScan.idleScan(world, maid, data, target, tool, true);
            }
        }

        // 好感度效率乘区 — 蓄力间隔按等级缩短 (间隔 / speed); v79.61 批3a 纯内核收编
        long chargeTicks = ChainHarvestMath.chargeTicks(vein.size(), target.intervalTicks(tool),
                MaidFavorability.workSpeedMultiplier(maid));
        long[] queue = new long[vein.size()];
        for (int i = 0; i < queue.length; i++) queue[i] = vein.get(i).asLong();
        data.putLongArray(KEY_QUEUE, queue);
        data.putLong(KEY_CHARGE_END, world.getGameTime() + chargeTicks);
        data.putInt(KEY_PHASE, Phase.CHARGE.ordinal()); // 入队即 CHARGE (单点写)

        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] {} 开脉 {} 块 @ {} 蓄力 {}t",
                target.label(), queue.length, pos, chargeTicks);
        bubble(maid, target.label() + " " + queue.length + " 块 蓄力 "
                + String.format("%.1f", chargeTicks / TICKS_PER_SECOND) + " 秒", true);
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    /** 蓄力等待 → 到期破坏整脉 (破块限 4 格球, WOOD 不限距离) */
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
        // v79.57 回归 (#185): 蓄力期被埋 → 自救换铲 → 主手非目标工具 → canHarvest 全 false
        // → broken=0 误报"完成 0 块"; 破坏前按队列首目标方块换工具 (与开脉 ensureToolFor 同款)
        for (long l : queue) {
            BlockState first = world.getBlockState(BlockPos.of(l));
            if (!first.isAir() && target.matches(first)) {
                ensureToolFor(maid, first);
                tool = maid.getMainHandItem();
                break;
            }
        }
        int broken = 0;
        for (long l : queue) {
            BlockPos blockPos = BlockPos.of(l);
            BlockState state = world.getBlockState(blockPos);
            if (state.isAir() || !target.matches(state)) continue;
            // 破块限 4 格 3D (v79.58: 3→4 格 MINE_DIG_DIST_SQR — TLM destroyBlock 无距离
            // 限制实测, 3 格边界抖动白蓄力; 原 32 格远程破不合理); 脉内其余块留待下轮
            // 2026-08-11c F3: WOOD 不限距离 — 树顶 (竖直远块) 不可达, 连锁一次破整树
            // (ORE 保持 4 格球分轮推进)
            if (target != HarvestTarget.WOOD
                    && blockPos.distSqr(maid.blockPosition()) > VanillaConstants.MINE_DIG_DIST_SQR) continue;
            if (!target.canHarvest(tool, state)) continue;
            if (!maid.canDestroyBlock(blockPos)) continue;
            if (maid.destroyBlock(blockPos)) broken++;
        }
        if (broken > 0 && target.consumesDurability(tool)) {
            // 好感度消耗乘区 — 耐久消耗按等级降低 (最低 1 点); v79.61 批3a 纯内核收编
            broken = ChainHarvestMath.durabilityCost(broken, MaidFavorability.costMultiplier(maid));
//? if 1.20.1 {
            tool.hurtAndBreak(broken, maid,
                    e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
//?} else {
            tool.hurtAndBreak(broken, maid, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
        }
        maid.swing(InteractionHand.MAIN_HAND);
        clearChainData(data);
        // 整脉完成 → 清扫描节流, 下轮立即重扫 (原 LAST_SCAN.remove; per-maid 状态对象)
        state(maid).lastScan = 0;
        // v79.58 (用户实测): 破 0 块失败出口 — 原无出口 → 重扫 nearPass 又命中同矿 →
        // 又开脉 → 死循环看着 (球内判定 "能直接挖" 但 canEntityDestroy/事件取消
        // 实际破不了); 球内仍有目标块 = 真挖不了 → skip 退避 60t/3 次→600t
        if (broken == 0 && hasReachableRemaining(world, maid, queue, target)) {
            bubble(maid, "目标无法破坏, 暂时跳过", false);
            return ChainScan.failAndSkip(state(maid), BlockPos.of(queue[0]), world, maid, data, target, tool);
        }
        LittleMaidMoreAction.LOGGER.info("[ChainHarvest] {} 整脉破坏 {} 块", target.label(), broken);
        bubble(maid, target.label() + " 完成 " + broken + " 块", true);
        keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    /** 队列中是否仍有球内可挖目标块 — 破 0 块时区分"真挖不了" (skip) vs "被推离球外" (重走近) */
    private static boolean hasReachableRemaining(ServerLevel world, EntityMaid maid,
                                                 long[] queue, HarvestTarget target) {
        BlockPos foot = maid.blockPosition();
        for (long l : queue) {
            BlockPos p = BlockPos.of(l);
            BlockState s = world.getBlockState(p);
            if (!s.isAir() && target.matches(s)
                    && p.distSqr(foot) <= VanillaConstants.MINE_DIG_DIST_SQR) {
                return true;
            }
        }
        return false;
    }

    // ── 辅助 ──

    /** 采集距离上限平方 (配置驱动, 默认 32 格) */
    private static double maxDistSqr() {
        int radius = ActiveTaskConfig.CHAIN_MAX_DISTANCE.get();
        return (double) radius * radius;
    }

    static void keepAlive(ServerLevel world, EntityMaid maid) {
        // 三件套样板收敛 (api/navigation/NavigationUtil)
        NavigationUtil.keepAlive(world, maid);
    }

    /** 清除连锁状态 key（闭环，含旧版残留 key） */
    public static void clearChainData(CompoundTag data) {
        data.remove(KEY_QUEUE);
        data.remove(KEY_CHARGE_END);
        data.remove(KEY_PHASE); // 单点清: 相位随队列闭环 (SCAN 默认, 不写键)
        data.remove(KEY_IDX_LEGACY);
        data.remove(KEY_TICK_LEGACY);
    }

    /**
     * 任务终结/实体卸载时清理 — per-maid 状态对象整移除 + NBT 根键闭环。
     * <p>v79.52: 原 5 表逐清 + SKIP_AT 连带清收敛为 1 行 — 只删自己的 UUID 条目,
     * 其他女仆不受影响 (SKIP_AT 全局共享误删 TTL 根治)。
     * <p>调用方: ChainHarvestPipeline cleanup (终结路径汇聚) + EntityCleanupListener (实体卸载)。
     */
    public static void clearMaidState(EntityMaid maid) {
        STATES.remove(maid.getUUID());
        clearChainData(maid.getPersistentData());
    }

    static void bubble(EntityMaid maid, String text, boolean force) {
        // 统一节流工具 (原 BUBBLE_TICK map 手写 — 改 PD 键, map 删);
        // 2026-08-11c: 节流间隔改独立常量 40t (原复用 CHAIN_SCAN_INTERVAL=20t — TLM copy 回归竞态根因)
        if (!force && !com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                .shouldFire(maid, "chain_bubble", CHAIN_BUBBLE_INTERVAL)) {
            return;
        }
        MaidChatBubbleApi.showProgress(maid, text, 0d);
    }

    // ── 工具 ──

    /**
     * 背包是否有可收集空间 — 遍历 getAvailableInv(true) 找空槽或非满堆叠槽。
     * 满 → 采集暂停 (气泡提示), 清包/卸货后自动恢复。
     */
    static boolean hasInventorySpace(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            net.minecraft.world.item.ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) return true;
            if (s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    /**
     * 目标驱动换工具 (用户: "挖泥土会换铲子, 挖矿换镐子") — v79.57 判断收拢 ToolJudge:
     * 按方块合适类型 (泥土/沙→铲, 矿/石→镐, 原木→斧) 从全背包选 tier 最高可用工具。
     * 当前手工具已合适且可用 → 不动; 背包无合适工具 → 不动 (空手慢速兜底,
     * 由调用方 canHarvest 门控)。挖穿协调器 (DigThroughCoordinator) 复用。
     */
    static void ensureToolFor(EntityMaid maid, BlockState state) {
        ToolJudge.ToolType need = ToolJudge.suitableToolType(state);
        if (need == ToolJudge.ToolType.NONE) return;
        if (ToolJudge.isSuitableUsable(maid.getMainHandItem(), state, TOOL_RESERVE_DURABILITY)) return;
        ToolJudge.selectBestForBlock(maid, state, TOOL_RESERVE_DURABILITY)
                .ifPresent(p -> HandSwap.swapTo(maid, p.slot()));
    }

}

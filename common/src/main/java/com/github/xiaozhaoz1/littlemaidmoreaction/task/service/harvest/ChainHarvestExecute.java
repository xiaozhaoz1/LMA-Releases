package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

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
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HarvestTarget;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.MaidFavorability;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolJudge;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.ConnectedBlockSearch;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.HandSwap;
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
        // ★ v79.63.18 (用户裁定 2026-09-14): **采矿扫描垂直范围至少 8 格** ✓
        //   原来直接对齐挖穿深度 (默认 6 ✗) ⇒ 她**头顶 7-8 格的矿扫不到** ✗ (用户实测: 邻区块/更高的矿
        //   看不见 ⇒ "看着不挖" ✓ 之一)。现在取 `max(8, CHAIN_DIG_DOWN_DEPTH)` ✓
        //   ⇒ 保证 8 格下限 ✓, 配置调大 (最大 8 hmm ⇒ 1..8) 时仍跟随 ✓; **木头保持 ±12 不变** ✓
        int depth = ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get();
        return ChainHarvestMath.vRange(target == HarvestTarget.WOOD,
                target == HarvestTarget.WOOD ? depth : Math.max(8, depth));
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

    /** v79.62.2 矿裸露判定: 6 邻面 (上下左右前后) 至少 1 面空气/流体 — 全实心包夹 = 不裸露 */
    static boolean exposedToAir(ServerLevel world, BlockPos pos) {
        BlockPos[] neighbors = {
                pos.above(), pos.below(),
                pos.north(), pos.south(), pos.east(), pos.west()
        };
        for (BlockPos n : neighbors) {
            // ★ v79.63.31 (用户实证「区块不同的矿石不挖」✓ 是对的): 原来这里
            //   `if (!world.hasChunk(邻居)) continue;` ✗ ⇒ **区块边界上的矿**, 只要它唯一的
            //   "开口"朝**未加载的邻区块** ⇒ 本门判"不暴露" ⇒ **拒绝开脉** ⇒ 看着就是不挖 ✓✓
            //   (她日志里那块 x=-3072 = 区块 -192 的**第一格** 正是这种 ✓; 我此前的夹具矿六面都在
            //    已加载区块里 ⇒ 不触发 ⇒ 假阴性 ✗)
            //   现改为: **不跳过未加载邻居** — 未加载区块的 getBlockState 视为空 ⇒ 算开口 ✓
            //   (她本来就能挖穿它 ⇒ 放行正确 ✓; 代价: 可能选中"其实埋在邻块石头里"的矿 ⇒
            //    挖 0 块/超时由跳过集兜 ✓)
            BlockState ns = world.getBlockState(n);
            // ★ v79.63.37 (用户实测「我放的铜矿它不挖」✓ 日志实证: 1856 条铜矿行里"某面空气"=0 ✗ —
            //   六面全是 铜矿石/皓蓝石/石头 ⇒ 现行判据只认**空气/流体** ✗ ⇒ 玻璃/皓蓝石/草/雪/藤蔓
            //   这些"玩家看得见但不挡路"的方块**全都不算露头** ✗ ⇒ 一律跳过 ✓✓ 这正是用户的现象 ✓)
            //   ⇒ 放宽为: **不阻挡移动** (!blocksMotion) 也算露头 ✓ (玻璃/装饰石/草/雪/藤蔓 → 会挖 ✓)
            //   全实心包住 (石头/矿石/深板岩…) 仍然跳过 ✓ (你原裁定的"防她对挖不到的矿发呆"保持 ✓)
            // 判据 = "**玩家看得见吗**" ✓ (用户 2026-09-16 明确: 手放的矿必然放在空气里 ⇒ 必有一面能看见 ✓)
            //   ① 空气 / 流体 ✓  ② 不挡路 (玻璃/草/雪/藤蔓 ✓)  ③ **不遮挡视线** (!canOcclude ✓
            //   ⇒ 玻璃/皓蓝石这类装饰石、铁栏杆、树叶… 只要"看得见"就放行 ✓✓)
            //   仍保留: 六面全是**不透明实心** (石头/矿石/深板岩…) ⇒ 判包住 ⇒ 跳过 ✓
            if (ns.isAir() || !ns.getFluidState().isEmpty() || !ns.blocksMotion() || !ns.canOcclude()) return true;
        }
        // ★ v79.63.34 诊断 (用户: "墙旁边为什么判不暴露" ⇒ 把**六面**逐个打出来 ✓)
        try {
            StringBuilder sb = new StringBuilder();
            for (BlockPos n : neighbors) {
                BlockState ns = world.getBlockState(n);
                sb.append(n.toShortString()).append("[区块已加载=").append(world.hasChunk(n.getX() >> 4, n.getZ() >> 4))
                  .append(" 方块=").append(ns.getBlock().getName().getString()).append(" 空气=").append(ns.isAir())
                  .append(" 流体=").append(!ns.getFluidState().isEmpty()).append("] ");
            }
            // v79.64 收尾: 这条一轮上万次 ✗ ⇒ 降到 debug 级（排查时开日志即可 ✓）
            LittleMaidMoreAction.LOGGER.debug("[ORE-EXPOSE] {} 判不暴露 ✗ 本块={} 六面: {}",
                    pos.toShortString(), world.getBlockState(pos).getBlock().getName().getString(), sb);
        } catch (Throwable t) { /* 诊断不致命 */ }
        return false;
    }

    private ChainHarvestExecute() {}

    // ── 主流程: 每 tick 执行 ──

    /** TaskRegistry.TaskExecutor 入口 */
    public static TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos,
                                     CompoundTag data, Mode mode) {
        // isCancelled 防御已删 (2026-08-16 实证: cancel 同帧 clearAll, FLOW_STATE 零残留
        // 不跨 tick — 原不可达; 下方 flow 防御 (错题 #124 防线) 已覆盖终结后路径)
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
            bubble(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.inv_full").getString(), false);  // 节流由 ThrottleUtil (chain_bubble)
            keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }

        // 状态机化 (v79.61x): 显式相位替代"队列存在"判据 — 队列退化为数据 (蓄力目标)
        if (phaseOf(data) == Phase.CHARGE) {
            return charge(world, maid, data, target, tool);
        }

        BlockState state = world.getBlockState(pos);
        // v79.62.2 修「脚下矿一直在一个点看」: 脚下矿若 tryStartVein 失败已进跳过集 —
        // 但本处每 tick 重新 matches 不查跳过集 → 失败矿永远被优先尝试 → 卡住.
        // 加跳过集检查: 脚下矿在跳过集 → 不走 tryStartVein, 走 idleScan 找别的目标.
        if (target.matches(state) && allowed(maid, state)) {
            if (!st.skipped.contains(pos.asLong())) {
                return tryStartVein(world, maid, pos, data, target, tool);
            }
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
            // v79.62.2 修「一直回一个点」: 原失败递归 idleScan(firstFail=immediate) → 找到
            // 同一个不可达矿 → tryStartVein 又失败 → 又 idleScan 无限递归 (同 tick 循环).
            // 改: 记跳过 (首次) + CONTINUE — 下 tick 自然重扫, 不递归 (对齐 failAndSkip 修法).
            boolean firstFail = !skip.contains(pos.asLong());
            if (firstFail) {
                ChainScan.addSkip(st, pos.asLong(), world.getGameTime());
            }
            LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=290 出口: return TaskResult.CONTINUE;", pos.toShortString());
            return TaskResult.CONTINUE;
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
            // ★ v79.64 诊断 (用户要求 ✓): 定死"工具等级不够"这一分支 —— 看**目标矿 / 她换完工具后手上是什么**
            //   (注意 312 行之前已跑过 ensureToolFor(306 行) ⇒ 此处 tool = **换过之后的**手工具 ✓)
            LittleMaidMoreAction.LOGGER.warn("[ORE-TOOL] 目标={} 换工具后手上={}",
                    state.getBlock().getName().getString(), tool.getItem());
            bubble(maid, "工具等级不够, 暂时跳过", false);
            LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=307 出口: return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);", pos.toShortString());
            return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);
        }
        if (!target.validAt(world, pos)) {
            // WOOD 非天然树 (CHAIN_WOOD_NATURE_CHECK) — 防拆建筑, 设计静默
            LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=312 出口: return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);", pos.toShortString());
            return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);
        }
        // v79.62.2 用户裁定: 矿必须裸露才挖 — 6 邻面 (上下左右前后) 至少 1 面空气,
        // 否则进跳过集 (脚下被泥土全包的矿 → 跳过, 防女仆一直看挖不到).
        if (!exposedToAir(world, pos)) {
            LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=318 出口: return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);", pos.toShortString());
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
                LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=330 出口: return TaskResult.CONTINUE;", pos.toShortString());
                return TaskResult.CONTINUE;
            }
            case FAILED -> {
                // 无方块/无实心邻格可点/超时 → 跳过该目标 (跳过集 TTL 过期重试)
                DangerGuardCoordinator.clear(maid);
                LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=336 出口: return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);", pos.toShortString());
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
            LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=349 出口: return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);", pos.toShortString());
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
            LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=364 出口: return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);", pos.toShortString());
            return ChainScan.failAndSkip(st, pos, world, maid, data, target, tool);
        }

        if (target.consumesDurability(tool)) {
            int cropped = ChainHarvestMath.durabilityCropSize(vein.size(),
                    ToolStateReader.getRemainingDurability(tool), TOOL_RESERVE_DURABILITY);
            if (cropped < vein.size()) {
                vein = vein.subList(0, cropped);
            }
            if (vein.isEmpty()) {
                // v79.62.2 修「一直回一个点」: 原递归 idleScan(true) 无限循环 — 改 CONTINUE
                LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=376 出口: return TaskResult.CONTINUE;", pos.toShortString());
                return TaskResult.CONTINUE;
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
        LittleMaidMoreAction.LOGGER.warn("[ORE-GATE] {} 开脉被拒 行=395 出口: return TaskResult.CONTINUE;", pos.toShortString());
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
            // ★ v79.63.24 (用户裁定「开脉读完就直接挖, 不用再判有没有出去」):
            //   队列在**开脉时**已裁到 4 格球内 ✓ (见上"可达裁剪") ⇒ 破块阶段**不再判距离** ✗
            //   (原来蓄力 4~8t 期间她走动/掉落 ⇒ 块"跑出球" ⇒ 整脉破坏 0 块 ⇒ 白充能 ✗
            //    这份就是用户日志里"开脉 1 块 → 整脉破坏 0 块"的成因 ✓)
            //   TLM destroyBlock 无距离限制 ✓ ⇒ 直接按队列破 ✓
            if (!target.canHarvest(tool, state)) {
                // ★ v79.63.27 (用户日志实证「开脉 3 块 → 整脉破坏 2 块」M<N 反复出现 ✗):
                //   原实现只在**进入循环前**换一次工具 ⇒ 镐**中途耐久耗尽消失** ✗ ⇒ 仍用旧(空)引用
                //   判 canHarvest ⇒ **后续每块都被跳过** ⇒ 整脉只破一半 + 白充能 ✗
                //   现在: 一旦判不过 ⇒ **当场按方块重新换工具** ✓ ⇒ 换了还不行才跳过这块 ✓
                ensureToolFor(maid, state);
                tool = maid.getMainHandItem();
                if (!target.canHarvest(tool, state)) continue;
            }
            if (!maid.canDestroyBlock(blockPos)) continue;
            if (maid.destroyBlock(blockPos)) {
                // ★ v79.63.4 (用户裁定): 悬在她上方的矿 (墙上的/半空的) 挖掉后, 掉落物会停在下方实心块上,
                //   离开她的拾取范围 ⇒ 清"矿正下方那一列"到脚高度, 让掉落物落到脚边 (≤ CHAIN_DIG_DOWN_DEPTH)
                if (blockPos.getY() > maid.blockPosition().getY()) {
                    DigThroughCoordinator.clearFallColumn(world, maid, blockPos);
                }
                broken++;
                // ★ v79.63.3 ④ (用户裁定): **女仆自己挖掉的方块也要清附近跳过集** —
                //   地形变了 (开通道/露新矿) ⇒ 之前判"不可达"的目标可能已可达 (与玩家破坏同理)
                invalidateSkipsNear(blockPos, 8);
            }
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
            bubble(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.unbreakable").getString(), false);
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
        if (!force && !com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.ThrottleUtil
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


    /**
     * **玩家破坏方块 → 清附近跳过记录** (v79.63 用户裁定) — 返回清除条数。
     *
     * <p>女仆跟着玩家下矿: 玩家挖开挡路方块后, 之前判定"不可达"的矿应立即重新可选,
     * 而不用等 SKIP_TTL 到期 ⇒ 跳过集 TTL 保持 10 秒 (不拉长)。
     */
    public static int invalidateSkipsNear(net.minecraft.core.BlockPos pos, int radius) {
        long r2 = (long) radius * radius;
        int cleared = 0;
        for (MaidChainState st : STATES.values()) {
            cleared += st.clearSkipsNear(pos, r2);
        }
        return cleared;
    }
}

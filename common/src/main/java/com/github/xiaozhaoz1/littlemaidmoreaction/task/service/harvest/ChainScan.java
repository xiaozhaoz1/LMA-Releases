package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.sense.SenseApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.NearestBlockSearch;
import java.util.function.Predicate;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HarvestTarget;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;

/**
 * 连锁采集扫描域 (v79.61x execute 瘦身样本 4 抽取) — 原 ChainHarvestExecute 扫描决策域:
 * 空闲扫描 / 近扫 / 最近目标 / 跳过集维护 / 采集黑白名单 / 扫描参数。
 *
 * <p>无跨 tick 类内状态 (MaidChainState 经 {@link ChainHarvestExecute#state} 共享);
 * 行为零变化 (逐行搬移); 主循环 (守卫链/开脉/蓄力) 留在 {@link ChainHarvestExecute}。
 */
final class ChainScan {

    /** 跳过集容量 (用户定 10) */
    private static final int SKIP_MAX = 10;
    /** 跳过集 TTL — 60t: 失败目标过期重试, 长 TTL 防垃圾输出 (死循环实测 错题 #119; 演化史见 changelog) */
    private static final int SKIP_TTL = 200;   // v79.62.2 用户裁定: 跳过集 10 秒 (原 60=3 秒)
    /** nearPass 近扫轻节流 (tick) — 独立于 CHAIN_SCAN_INTERVAL, 寻路途中也扫周围矿 */
    private static final int NEAR_SCAN_INTERVAL = 5;
    /** v79.64 中扫 (10 格球) 节流 — 用户 2026-09-16 裁定 100t (女仆走不快, 不用每 5t 扫 16 格 ✓) */
    private static final int MID_SCAN_INTERVAL = 100;
    /** v79.64 远扫 (16 格球) 节流 — 用户裁定 250t (12.5s ✓) */
    private static final int FAR_SCAN_INTERVAL = 250;

    private ChainScan() {}

    /** 空闲扫描 — nearPass 4 格近扫优先 → 主扫描 16 格 → 目标导航 */
    static TaskResult idleScan(ServerLevel world, EntityMaid maid, CompoundTag data,
                               HarvestTarget target, ItemStack tool, boolean immediate) {
        long now = world.getGameTime();
        MaidChainState st = ChainHarvestExecute.state(maid);
        // 寻路途中也扫周围 4 格矿 (用户: "即便寻路没到指定地点依旧要搜寻周围 3 格") —
        // 独立轻节流, 不卡在 CHAIN_SCAN_INTERVAL; 有矿直接开脉 (挖脉中走 charge 分支不进来)
        // 传 scanTool (默认钻石镐/斧) — 存在性判断与手工具解耦, 挖完泥土拿铲也能扫到矿
        BlockPos near = nearPass(world, maid, target, scanTool(maid, target));
        if (near != null) {
            return ChainHarvestExecute.tryStartVein(world, maid, near, data, target, tool);
        }
        if (!immediate) {
            if (st.lastScan != 0 && st.lastScan <= now && now - st.lastScan < ActiveTaskConfig.CHAIN_SCAN_INTERVAL.get()) {
                ChainHarvestExecute.keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
        }
        st.lastScan = now;

        // 档位已删 — 扫描半径 16 + 垂直 ±vRange (v79.53 对齐挖穿深度) 唯一行为 (用户:
        // "寻路全用TLM... 全局设置/子任务设置都没用了"); 直接开脉门 4 格球 (v79.58
        // DIG_DIRECT_DIST_SQR=16 — 用户裁定可挖掘距离 4 完全覆盖, TLM destroyBlock 无
        // 距离限制实测): 4 格内 (3D) 直接挖, 4 格外走寻路走近
        // ★ v79.64 三层球扫描 (用户裁定 2026-09-16): 近 4 球/5t ✓(上面已跑) → **中 10 球/100t** → **远 16 球/250t**
        //   全部走 NearestBlockSearch (球壳精确距离序 + 命中即停 ⇒ 返回值必为**该球内最近的合格矿** ✓)
        //   旧实现 (nearPass ±4 盒子 ✗ / findNearestValid 区块环+名额截断 ✗) 已退役 ✓
        //   三个时间戳都在 MaidChainState (per-maid ✓ 错题 #272 ✓)
        ItemStack scanT = scanTool(maid, target);
        // ★ v79.64 诊断 (用户要求 ✓): 三项**分开判**，只在"确实是矿石但被拒"时打一行
        //   —— 一次就能分清是 名单(allowed) ✗ 还是 工具(canHarvest) ✗ 还是 tag(matches) ✗
        Predicate<BlockState> hit = s -> {
            boolean m = target.matches(s);
            if (!m) return false;                       // 不是矿石 ⇒ 静默 (量最大 ✓)
            boolean a = ChainHarvestExecute.allowed(maid, s);
            boolean h = target.canHarvest(scanT, s);
            if (!a || !h) {
                // v79.64 收尾: 降到 debug 级（排查时开日志 ✓ 平时不刷屏 ✓）
                LittleMaidMoreAction.LOGGER.debug(
                        "[ORE-FILTER] 矿石={} allowed={} canHarvest={} scanT={} 主手={}",
                        s.getBlock().getName().getString(), a, h, scanT.getItem(), maid.getMainHandItem().getItem());
            }
            return a && h;
        };
        LinkedHashSet<Long> skips = skippedFor(maid, maid.getMainHandItem());
        java.util.function.BiPredicate<BlockPos, BlockState> open = (p, s) -> hasOpenFace(world, p);

        boolean midDue = immediate || st.lastMidScan == 0 || now - st.lastMidScan >= MID_SCAN_INTERVAL;
        boolean farDue = immediate || st.lastFarScan == 0 || now - st.lastFarScan >= FAR_SCAN_INTERVAL;
        // ★ v79.64 **回归修复** (日志实证: `navigate=WALKING` 但女仆一步不动 ✗):
        //   节流**只作用于"重新搜索"**；**导航必须持续续期** ✓
        //   旧实现每 `CHAIN_SCAN_INTERVAL`(20t) 重扫一次 ⇒ 等于每 20t 重发 navigate ⇒ WALK_TARGET 不过期 ⇒ 她能一路走过去 ✓
        //   三层节流后若在这里直接 return，就变成"写一次走路目标后没人管" ⇒ 她站着 ✗
        //   ⇒ 下面用 MaidChainState.currentTarget (per-maid ✓ 错题 #272 ✓) 持续续期 ✓
        BlockPos prev = st.currentTarget == Long.MIN_VALUE ? null : BlockPos.of(st.currentTarget);
        if (!midDue && !farDue) {
            if (prev != null) {
                PathingApi.navigate(world, maid, prev);        // ★ 续期 (内部幂等: 目标同则不重设 ✓)
            } else {
                ChainHarvestExecute.keepAlive(world, maid);
            }
            return TaskResult.CONTINUE;
        }
        int radius = NearestBlockSearch.RADIUS_MID;
        BlockPos next = null;
        if (midDue) {
            st.lastMidScan = now;
            next = NearestBlockSearch.find(world, maid.blockPosition(), NearestBlockSearch.RADIUS_MID,
                    hit, open, skips, NearestBlockSearch.DEFAULT_BUDGET_CELLS);
        }
        if (next == null && farDue) {
            st.lastFarScan = now;
            radius = NearestBlockSearch.RADIUS_FAR;
            next = NearestBlockSearch.find(world, maid.blockPosition(), NearestBlockSearch.RADIUS_FAR,
                    hit, open, skips, NearestBlockSearch.DEFAULT_BUDGET_CELLS);
        }
        // ★ v79.64 诊断 (用户要求"加一行决策日志" ✓): 一眼看出**哪一层跑了 / 有没有命中 / 多远**
        LittleMaidMoreAction.LOGGER.warn("[ORE-TIER] 中扫{} 远扫{} 命中={} 距离={} 她={} 跳过集={}",
                midDue ? "跑" : "跳过", farDue ? "跑" : "跳过",
                next == null ? "无" : next.toShortString(),
                next == null ? "-" : String.format("%.1f", Math.sqrt(maid.blockPosition().distSqr(next))),
                maid.blockPosition().toShortString(), skips.size());
        // ★ v79.64: 记住本轮目标 ⇒ 节流期间由上面的续期逻辑持续发导航 ✓
        st.currentTarget = (next == null) ? Long.MIN_VALUE : next.asLong();
        if (next == null) {
            // v79.58: 无目标不气泡 (用户裁定 — 有矿在跳过集时误报"没有", 误导),
            // 只留 DEBUG 日志 (扫描节流 20t, 安静时低频)
            LittleMaidMoreAction.LOGGER.debug("[ChainHarvest] {} 空闲扫描无目标 radius={}",
                    target.label(), radius);
            ChainHarvestExecute.keepAlive(world, maid);
            return TaskResult.CONTINUE;
        }
        // 到达判定 4 格球 (DIG_DIRECT_DIST_SQR=16, v79.58 用户裁定可挖掘距离 4 完全覆盖):
        // 4 格内直接开脉, 4 格外走寻路走近 (卡极限格问题由寻路侧 oneAway 解决 —
        // 走动必到旁 1 格/头顶)
        // 目标驱动换工具 (用户: "挖泥土会换铲子, 挖矿换镐子") — 扫描谓词用
        // scanTool 与手工具解耦, 找到目标后按方块合适类型换 (换后 tool 更新, 后续
        // 挖穿/寻路/开脉全用新工具; 换后挖不了由 tryStartVein canHarvest + skip 兜底)
        // ★ v79.63.21 临时诊断 (用户要求): 每次选到目标时打印关键判据 — 定位"看着不挖" ✓
        // ★ v79.63.32 诊断 (用户: "搜索和走过去被区块拦住" ⇒ 查 TLM 自身的 workPos/restrict 残留 ✓)
        try {
            var sp = maid.getSchedulePos();
            BlockPos wp = sp.getWorkPos(), ip = sp.getIdlePos(), sps = sp.getSleepPos();
            BlockPos here = maid.blockPosition();
            LittleMaidMoreAction.LOGGER.warn("[ORE-DBG] TLM限制: 她={} work={}(距离{}) idle={}(距离{}) sleep={}(距离{})",
                    here, wp, wp == null ? "-" : String.format("%.0f", Math.sqrt(here.distSqr(wp))),
                    ip, ip == null ? "-" : String.format("%.0f", Math.sqrt(here.distSqr(ip))),
                    sps, sps == null ? "-" : String.format("%.0f", Math.sqrt(here.distSqr(sps))));
        } catch (Throwable t) {
            LittleMaidMoreAction.LOGGER.warn("[ORE-DBG] TLM限制读取失败: {}", t.toString());
        }
        LittleMaidMoreAction.LOGGER.warn("[ORE-DBG] 选目标 {} 距离={} 跳过集={} 手工具={} 脚Y={} 目标Y={}",
                next.toShortString(), String.format("%.1f", Math.sqrt(maid.blockPosition().distSqr(next))),
                st.skipped.contains(next.asLong()), maid.getMainHandItem().getItem(),
                maid.blockPosition().getY(), next.getY());
        ChainHarvestExecute.ensureToolFor(maid, world.getBlockState(next));
        tool = maid.getMainHandItem();
        BlockPos foot = maid.blockPosition();
        if (next.distSqr(foot) <= VanillaConstants.DIG_DIRECT_DIST_SQR) {
            return ChainHarvestExecute.tryStartVein(world, maid, next, data, target, tool);
        }
        // ★ v79.63.17 (用户实测「玩家在旁边, 女仆对着邻区块的矿看着不挖」✗ 不合理):
        //   **撤销 v79.63.3 的"当场跳过"** ✗ — 那个检查只看矿石**周围 3×3×3** 有无可站立点,
        //   而邻区块/更深的矿, 站立点常在**更远处** ⇒ 被误判"不可达" ⇒ **一步都不走** ✗✓ (元凶).
        //   现恢复"**先走过去**": 交回 TLM 走路 + 看门狗 (5s×2 ⇒ FAILED ⇒ 跳过集) 兜底 ✓;
        //   "看得见走不到"仍会被看门狗/跳过集兜住 ✓ (代价: 真不可达时多走 ~12s ✗ 可接受 ✓).
        //   (挖矿不参与区块认领 ✓, 采矿也不注册 TLM Brain ✓ ⇒ 排除"被限制拉回" ✗ — 已核实 ✓)
        // 完整寻路 API — TLM 导航 + 头顶挖穿兜底收编 PathingApi.navigate (新管道复用
        // 同一套寻路, 不再内联): 垫柱/面前挖穿/档位已删 (用户裁定简化), 走路全 TLM,
        // 头顶挖穿保留 (v79.57: 脚下挖穿退役 — 只挖裸露表面矿); canReachNear 预检防误挖,
        // 可达走正常路, 不可达 FAILED → 跳过集 (详见 PathingApi)
        // ★ v79.63.9 (用户裁定): **寻路节流 20t** — 原来每 tick 判定/下发太费 (navigate 内部虽幂等,
        //   但逐 tick 记账 + 反复写 WALK_TARGET 没必要); 20t 一次完全够 (看门狗 100t 内仍会被调用 ✓)。
        //   被节流时直接 CONTINUE: 已下发的导航目标由 TLM 继续走, 行为不变 ✓
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.ThrottleUtil
                .shouldFire(maid, "chain_nav", 20L)) {
            return TaskResult.CONTINUE;
        }
        var navOutcome = PathingApi.navigate(world, maid, next);
        LittleMaidMoreAction.LOGGER.warn("[ORE-DBG] navigate 结果={} 女仆={} 目标={}", navOutcome,
                maid.blockPosition().toShortString(), next.toShortString());
        switch (navOutcome) {
            case REACHED -> {
                // 已进 4 格球 — 下轮 execute 顶部命中直接开脉 (idleScan 兜底)
            }
            case WALKING -> {
                // v79.58: 执行中阶段反馈 — 原静默行走, GUI 看不到阶段; 节流 40t
                // (与蓄力同档, 已验证无 TLM copy 竞态)
                ChainHarvestExecute.bubble(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.going_to_target").getString(), false);
                ChainHarvestExecute.keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
            case DIGGING -> {
                // v79.58: 挖穿中阶段反馈 — 头顶挖穿 (digUp) 每 tick 一格, 原静默;
                // 气泡让用户看到net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.digging_up").getString()阶段
                ChainHarvestExecute.bubble(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.digging_up").getString(), false);
                ChainHarvestExecute.keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
            case FAILED -> {
                // TLM 不可达/导航超时 → 跳过 (跳过集 TTL 过期重试)
                // v79.53: INFO → DEBUG (60t 重试周期内每次重扫重打 = 日志风暴, 日志实证一轮 9 条)
                LittleMaidMoreAction.LOGGER.debug("[ChainHarvest] 女仆 {} 寻路放弃 目标 {} 跳过",
                        maid.getId(), next);
                // tier 维护先行 (换工具后清空旧跳过集 — 原 skipSet 语义), 再记跳过
                skippedFor(maid, maid.getMainHandItem());
                // v79.56 (错题 #184): 可见反馈 — 气泡 40t 节流 < 60t 重试周期 → 每次重试可感知
                // (原无声站桩, 用户实测 "一直看着也不去挖" 误判卡住/跳过集失效)
                ChainHarvestExecute.bubble(maid, net.minecraft.network.chat.Component.translatable(
                    "bubble.littlemaidmoreaction.target_unreachable").getString(), false);
                return failAndSkip(st, next, world, maid, data, target, tool);
            }
        }
        ChainHarvestExecute.keepAlive(world, maid);
        return TaskResult.CONTINUE;
    }

    /** 最近目标方块搜索 (含跳过集过期清理) — SenseApi.findNearestBlock + skip 集过滤 */
    @Nullable
    private static BlockPos findNearestValid(ServerLevel world, EntityMaid maid,
                                             HarvestTarget target, ItemStack tool, int radius) {
        // 跳过集过期清理 — FAILED 目标过期后重试 (用户: "16 格内有矿但不去挖",
        // 原永久跳过永久不知道; 路径可能因挖脉/搭路变化); skip 集按真实手工具分组;
        // 档位删 → 统一 SKIP_TTL=60 + 垂直范围 vRange (v79.53 对齐挖穿深度, 决策史见 changelog)
        MaidChainState st = ChainHarvestExecute.state(maid);
        LinkedHashSet<Long> skip = skippedFor(maid, maid.getMainHandItem());
        long now = world.getGameTime();
        skip.removeIf(l -> st.expire(l, now, SKIP_TTL));
        // 泛化最近搜索提升到 API 面 (SenseApi.findNearestBlock — BlockScanner + skip 集)
        // v79.62.2 用户裁定: 排除脚下 >2 格的深矿 (被泥土/石头盖住挖不到 → 女仆一直看卡住).
        // 目标Y 必须 >= 女仆脚Y - 2; 其余 (头上/旁边/远处 32 格) 不变.
        int footY = maid.blockPosition().getY();
        return SenseApi.findNearestBlock(maid,
                radius, ChainHarvestExecute.vRange(target), s -> target.matches(s) && ChainHarvestExecute.allowed(maid, s) && target.canHarvest(tool, s),
                // ★ v79.63.39 (用户实测: "4 格外/6 格内新放的矿不挖" ✓ 根因 = 候选按**距离**收集,
                //   埋死的矿把名额吃光 ⇒ 露头的矿排不进候选 ✗ — 见 BlockScanner 收集点发生在
                //   posFilter 之前)。此处**只在挖矿侧**放大候选名额 (256) ⇒ 先治"扫不到" ✓
                //   (不动共享系数 ✗ — v79.63.38 动共享系数把 lmaFarmPlant/lmaChainOreFar 搞红)。
                //   彻底方案(待办): 把 hasOpenFace 下推到 BlockScanner 收集阶段 ⇒ 名额可回落
                //   到用户裁定的 **16** ✓
                skip, Math.max(256, ChainHarvestMath.scanBudget(radius)),
                // v79.63.3 (用户裁定): **删掉旧的"脚下 >2 格深矿"过滤** — 用户: 不如直接靠
                //   "女仆挖矿 ⇒ 重置跳过集" + "远目标站立点检查" 兜底 (过滤器会误杀相邻矿堆)
                (p, s) -> hasOpenFace(world, p));   // 只保留: 无开口(埋死)矿不选
    }

    /**
     * **近扫 = 4 格球**（v79.64 ✓ 用户 2026-09-16 裁定："近扫是 4 格圆不是盒子，4 格高以外算中扫" ✓）
     * —— 用途 = **她站在原地就能直接挖的**矿 ✓（正是 4 格破块球 `MINE_DIG_DIST_SQR` ✓ 与"要不要走"一一对应 ✓）。
     *
     * <p>实现走 {@link NearestBlockSearch}：**精确 d² 逐桶 = 真球** ✓（不是 ±4 盒子 ✗）+ **命中即停 = 最近** ✓。
     * 节流沿用 {@link #NEAR_SCAN_INTERVAL}（5t ✓）。
     *
     * <p>两条保留的历史裁定：① **近扫不做可挖面(裸露)过滤** ✓（v79.63：TLM destroyBlock 无遮挡限制 ⇒
     * 薄墙后的矿本来就挖得穿 ✓，只有远距离目标要求开口 ✓）；② **不再设"脚下 >2 格深矿"限制** ✗
     * （v79.63.3 用户已裁定删除该类预过滤 ✓）。
     */
    @Nullable
    private static BlockPos nearPass(ServerLevel world, EntityMaid maid, HarvestTarget target, ItemStack tool) {
        long now = world.getGameTime();
        MaidChainState st = ChainHarvestExecute.state(maid);
        if (st.lastNearScan != 0 && now - st.lastNearScan < NEAR_SCAN_INTERVAL) {
            return null;
        }
        st.lastNearScan = now;
        // skip 集按真实手工具分组 (tool 参数 = scanTool 扫描谓词工具)
        LinkedHashSet<Long> skip = skippedFor(maid, maid.getMainHandItem());
        return NearestBlockSearch.find(world, maid.blockPosition(), NearestBlockSearch.RADIUS_NEAR,
                s -> target.matches(s) && ChainHarvestExecute.allowed(maid, s) && target.canHarvest(tool, s),
                null,   // ★ 近扫不做裸露过滤 ✓ (v79.63 裁定: 薄墙后也能挖穿 ✓)
                skip, NearestBlockSearch.DEFAULT_BUDGET_CELLS);
    }

    /** 跳过集 (tier 分组维护) — 换工具等级变化时清空 (v79.52: 原 SKIP_AT 全局共享
     *  连带清语义由 per-maid 归属内化, 孤儿时间戳根治; 错题 P-2 语义保留) */
    static LinkedHashSet<Long> skippedFor(EntityMaid maid, ItemStack tool) {
        MaidChainState st = ChainHarvestExecute.state(maid);
        st.maintainTier(ToolStateReader.getTierLevel(tool));
        return st.skipped;
    }

    /** 记跳过 — 容量上限 SKIP_MAX 淘汰最旧 (per-maid 时间戳, 只影响本女仆; 逻辑在 MaidChainState) */
    static void addSkip(MaidChainState st, long pos, long now) {
        st.addSkip(pos, now, SKIP_MAX);
    }

    /** 失败出口单点 — 记跳过 + 下 tick 重扫 (v79.62.2 修爆栈: 原递归 idleScan(immediate=true)
     *  → 找到下一目标又 tryStartVein 失败 → failAndSkip 无限递归 StackOverflow (用户切砍树任务崩溃).
     *  改为记跳过 + CONTINUE — 下 tick idleScan 自然重扫, 不递归.) */
    static TaskResult failAndSkip(MaidChainState st, BlockPos pos, ServerLevel world,
                                  EntityMaid maid, CompoundTag data,
                                  HarvestTarget target, ItemStack tool) {
        addSkip(st, pos.asLong(), world.getGameTime());
        return TaskResult.CONTINUE;
    }

    private static int searchRadius(EntityMaid maid) {
        // v79.62.2 用户裁定: chain 搜索半径固定 32 (原 ENV_DEFAULT_RADIUS=16 → findNearestBlock
        // 16格=1 chunk → 16格外矿/树找不到 → 女仆不去; 32格=2 chunk 覆盖更远目标)
        return 32;
    }

    /** 扫描谓词用模式默认工具 (与手工具解耦) — 手拿铲也能扫到矿 (挖完泥土换目标),
     *  手拿镐也能扫到泥土。钻石镐/斧: 等级门槛够绝大多数矿; 等级不够的矿由
     *  tryStartVein 真实工具 canHarvest + skip 兜底 */
    private static ItemStack scanTool(EntityMaid maid, HarvestTarget target) {
        // ★ v79.63.26 (用户裁定 2026-09-16): 扫描判定改用「**她背包里最好的**镐/斧」✓
        //   原因: 原来固定钻石镐 ⇒ 会选中"她**根本挖不动**"的矿 ✗ ⇒ 走过去/开脉再失败 ⇒ **白走一趟** ✗
        //   现在: 没有可用工具 ⇒ 返回 EMPTY ⇒ canHarvest 恒 false ⇒ **这类矿一开始就不选** ✓✓
        //   恢复时机 ✓: 她捡到更好的工具时 — maintainTier 清跳过集 ✓ + 每 20t 重扫 ⇒ 自动重新可见 ✓
        boolean axe = target == HarvestTarget.WOOD;
        ItemStack best = ItemStack.EMPTY;
        int bestTier = Integer.MIN_VALUE;
        ItemStack hand = maid.getMainHandItem();
        var inv = maid.getAvailableInv(true);
        for (int i = -1; i < inv.getSlots(); i++) {
            ItemStack s = i < 0 ? hand : inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (!(axe ? com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader.isAxe(s)
                      : com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader.isPickaxe(s))) continue;
            if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader.getRemainingDurability(s) <= 0) continue;
            int tier = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader.getTierLevel(s);
            if (tier > bestTier) {
                bestTier = tier;
                best = s;
            }
        }
        // ★ v79.63.28 兜底 (用户实测「连 4 格内的矿都不挖」✗ — 我上版返回 EMPTY 把"没工具"变成"全不选",
        //   比旧行为更糟 ✗): 找不到可用工具时**回退默认钻石镐/斧** ⇒ 恢复旧行为 ✓
        //   (仍保留"有好工具就用她的 ✓"这一改进; 「挖不动的别选」由下面 canHarvest 与开脉门兜 ✓)
        if (best.isEmpty()) {
            return target == HarvestTarget.WOOD
                    ? new ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE)
                    : new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        }
        // 诊断 (临时): 用哪把镐做扫描判定
        LittleMaidMoreAction.LOGGER.warn("[ORE-DBG] scanTool={} tier={} 主手={} (用于近扫/远扫 canHarvest 判定)",
                best.getItem(), bestTier, maid.getMainHandItem().getItem());
        return best;
    }

    /**
     * **可挖面判定** — 相邻 6 格中至少一格不阻挡移动 (空气/水/植物等)。
     *
     * <p>v79.63 (用户实测「隔墙矿一直卡住」): 被石层**埋死**的矿 (连一个开口都没有) 女仆走不到、
     * 也挖不到, 选中它只会让导航/挖穿空转 ⇒ **选目标阶段就排除** (与既有「脚下 >2 格深矿」同款启发式)。
     * 只排除"无开口"; 洞窟另一侧可见但走不过去的矿仍会选中, 由导航 FAILED → 跳过集兜底。
     */
    static boolean hasOpenFace(net.minecraft.world.level.Level world, BlockPos pos) {
        // ★ v79.63.30 (用户日志实证: 矿在 x=-3072 = 区块边界 ⇒ **扫描选中了, 开脉却拒绝** ✗):
        //   原先本方法与开脉门 ChainHarvestExecute.exposedToAir 的**判据不一致** ✗:
        //     · 本方法: !blocksMotion() ⇒ 空气/水/草/雪都算开口 ✓ (宽松)
        //     · 开脉门: 只认**空气或流体** ∧ **邻居区块必须已加载** ✗ (严格)
        //   ⇒ 边界方块朝"未加载邻居"那一面 ⇒ 扫描说开放 ✓ / 开脉说不暴露 ✗ ⇒ 选中后被拒 ✓✓
        //   现**统一口径**: 本方法直接委托开脉门 ✓ (单一真相 ⇒ 不会再"选中却挖不到" ✓)
        if (world instanceof net.minecraft.server.level.ServerLevel sl) {
            return ChainHarvestExecute.exposedToAir(sl, pos);
        }
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (!world.getBlockState(pos.relative(d)).blocksMotion()) return true;
        }
        return false;
    }
}

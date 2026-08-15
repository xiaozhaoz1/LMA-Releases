package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.sense.SenseApi;
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
    private static final int SKIP_TTL = 60;
    /** nearPass 近扫轻节流 (tick) — 独立于 CHAIN_SCAN_INTERVAL, 寻路途中也扫周围矿 */
    private static final int NEAR_SCAN_INTERVAL = 5;

    private ChainScan() {}

    /** 空闲扫描 — nearPass 4 格近扫优先 → 主扫描 16 格 → 目标导航 */
    static TaskResult idleScan(ServerLevel world, EntityMaid maid, CompoundTag data,
                               HarvestTarget target, ItemStack tool, boolean immediate) {
        long now = world.getGameTime();
        MaidChainState st = ChainHarvestExecute.state(maid);
        // 寻路途中也扫周围 4 格矿 (用户: "即便寻路没到指定地点依旧要搜寻周围 3 格") —
        // 独立轻节流, 不卡在 CHAIN_SCAN_INTERVAL; 有矿直接开脉 (挖脉中走 charge 分支不进来)
        // 传 scanTool (默认钻石镐/斧) — 存在性判断与手工具解耦, 挖完泥土拿铲也能扫到矿
        BlockPos near = nearPass(world, maid, target, scanTool(target));
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
        int radius = searchRadius(maid);
        // 扫描用 scanTool (默认钻石镐/斧) 做 canHarvest — 目标存在性判定与手工具解耦
        BlockPos next = findNearestValid(world, maid, target, scanTool(target), radius);
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
        ChainHarvestExecute.ensureToolFor(maid, world.getBlockState(next));
        tool = maid.getMainHandItem();
        BlockPos foot = maid.blockPosition();
        if (next.distSqr(foot) <= VanillaConstants.DIG_DIRECT_DIST_SQR) {
            return ChainHarvestExecute.tryStartVein(world, maid, next, data, target, tool);
        }
        // 完整寻路 API — TLM 导航 + 头顶挖穿兜底收编 PathingApi.navigate (新管道复用
        // 同一套寻路, 不再内联): 垫柱/面前挖穿/档位已删 (用户裁定简化), 走路全 TLM,
        // 头顶挖穿保留 (v79.57: 脚下挖穿退役 — 只挖裸露表面矿); canReachNear 预检防误挖,
        // 可达走正常路, 不可达 FAILED → 跳过集 (详见 PathingApi)
        switch (PathingApi.navigate(world, maid, next)) {
            case REACHED -> {
                // 已进 4 格球 — 下轮 execute 顶部命中直接开脉 (idleScan 兜底)
            }
            case WALKING -> {
                // v79.58: 执行中阶段反馈 — 原静默行走, GUI 看不到阶段; 节流 40t
                // (与蓄力同档, 已验证无 TLM copy 竞态)
                ChainHarvestExecute.bubble(maid, "正在前往目标", false);
                ChainHarvestExecute.keepAlive(world, maid);
                return TaskResult.CONTINUE;
            }
            case DIGGING -> {
                // v79.58: 挖穿中阶段反馈 — 头顶挖穿 (digUp) 每 tick 一格, 原静默;
                // 气泡让用户看到"正在向上挖穿"阶段
                ChainHarvestExecute.bubble(maid, "正在向上挖穿", false);
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
                ChainHarvestExecute.bubble(maid, "目标不可达, 暂时跳过", false);
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
        return SenseApi.findNearestBlock(maid,
                radius, ChainHarvestExecute.vRange(target), s -> target.matches(s) && ChainHarvestExecute.allowed(maid, s) && target.canHarvest(tool, s),
                skip, ChainHarvestMath.scanBudget(radius));
    }

    /**
     * 4 格球近扫 (x/z/y ∈ [-4,4], 3D distSqr ≤ 16 ≈ 257 格) — 寻路途中经过的矿直接开脉。
     * SenseApi.findNearestBlock 的 radius 是 chunk 半径语义 (radius/16+1, 最小 1 chunk=16 格) —
     * 不可用于近扫。只做快过滤 (skip/matches/allowed/canHarvest); validAt 慢校验留给
     * tryStartVein (失败已 addSkip + immediate 重扫)。
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
        BlockPos foot = maid.blockPosition();
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d > VanillaConstants.MINE_DIG_DIST_SQR) continue;
                    BlockPos p = foot.offset(dx, dy, dz);
                    if (skip.contains(p.asLong())) continue;
                    BlockState state = world.getBlockState(p);
                    if (!target.matches(state) || !ChainHarvestExecute.allowed(maid, state) || !target.canHarvest(tool, state)) continue;
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
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

    /** 失败出口单点 — 记跳过 + 重扫 (v79.56 结构整理: 原 5 处散落出口收敛; 跳过集逻辑集中) */
    static TaskResult failAndSkip(MaidChainState st, BlockPos pos, ServerLevel world,
                                  EntityMaid maid, CompoundTag data,
                                  HarvestTarget target, ItemStack tool) {
        addSkip(st, pos.asLong(), world.getGameTime());
        return idleScan(world, maid, data, target, tool, true);
    }

    private static int searchRadius(EntityMaid maid) {
        return maid.hasRestriction()
                ? Math.max(4, (int) maid.getRestrictRadius())
                : PassiveTaskConfig.ENV_DEFAULT_RADIUS.get();
    }

    /** 扫描谓词用模式默认工具 (与手工具解耦) — 手拿铲也能扫到矿 (挖完泥土换目标),
     *  手拿镐也能扫到泥土。钻石镐/斧: 等级门槛够绝大多数矿; 等级不够的矿由
     *  tryStartVein 真实工具 canHarvest + skip 兜底 */
    private static ItemStack scanTool(HarvestTarget target) {
        return target == HarvestTarget.ORE
                ? new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)
                : new ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE);
    }
}

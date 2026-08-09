package com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DigThroughCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 女仆导航 API (v79.23 重构 — 走路全 TLM 原版导航, 自研寻路引擎退役)。
 *
 * <p>maid_useful_task 模式 (零自研引擎): TLM Brain 导航 (setWalkAndLookTargetMemories
 * 写 WALK_TARGET/LOOK_TARGET) 负责走路, 到达判定/可达预检由调用方负责。
 * TLM 导航不挖方块 — 垂直挖穿兜底 ({@link DigThroughCoordinator#digVertical} /
 * {@link DigThroughCoordinator#digUp})。
 *
 * <p>v79.26.7: {@link #navigate} 完整寻路门面 — TLM 导航 + 挖穿兜底一键调用,
 * 管道不再内联兜底动作 (新管道复用同一套寻路能力)。
 * <p>v79.26.8: 到达判定 1 格邻域 (卡极限距离也走过去, 至少走到矿旁边)。
 * <p>v79.26.8b: 可达预检修复 — TLM canPathReach 对实心目标格 (矿) 语义脆弱
 * (BFS 只访问行走格) → 预检改矿旁可站立格邻域判定 + 预检移到目标切换时。
 * <p>v79.26.8e: 用户裁定简化 — 垫柱 (BlockUpCoordinator) 全删, 面前挖穿 (digFront)
 * 删 (只挖垂直: 脚下 digVertical + 头顶 2.83 球直接开脉), 档位 (PathingModes)
 * 全删 (mode 参数/全局/子任务 GUI 三处退役)。
 * <p>v79.26.8f: 只用 TLM 寻路 — 桥/阶梯 (BridgeCoordinator) + 拉拽看门狗
 * (NavWatchdog) 删, 走路纯 TLM 无干预; 垂直挖穿保留; 危险堵护 + 卡方块自救保留。
 * <p>v79.26.8g: 上下双向挖穿 — 头顶目标 ≤6 格 TLM 不可达 → 向上挖穿
 * ({@link DigThroughCoordinator#digUp}), 矿正下方整列挖通, 掉落物落脚下。
 */
public final class PathingApi {

    private PathingApi() {}

    /**
     * 幂等 TLM 导航 — 目标已设置 (WALK_TARGET == pos) 不重写 (防每 tick 打断导航)。
     *
     * @param speed 移动速度 (TLM 惯例 0.5F)
     */
    public static void navigateTo(EntityMaid maid, BlockPos pos, float speed) {
        WalkTarget wt = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (wt != null && wt.getTarget().currentBlockPosition().equals(pos)) {
            return;
        }
        BehaviorUtils.setWalkAndLookTargetMemories(maid, pos, speed, 0);
    }

    /** 当前导航目标 (WALK_TARGET 位置); 无导航 → null */
    @Nullable
    public static BlockPos navTarget(EntityMaid maid) {
        WalkTarget wt = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        return wt == null ? null : wt.getTarget().currentBlockPosition();
    }

    /**
     * TLM 可达预检 — maid.canPathReach (TLM MaidPathFindingBFS, 不挖方块)。
     * 注意: 对实心目标格 (矿) 语义脆弱 — 应传可站立格 (见 {@link #canReachNear})。
     */
    public static boolean canReach(EntityMaid maid, BlockPos pos) {
        return maid.canPathReach(pos);
    }

    /** 清除导航 — erase WALK_TARGET + 清导航看门狗记录 (LOOK_TARGET 由 TLM LookAtTargetSink 自清) */
    public static void clearNav(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        NAV_START.remove(maid.getId());
    }

    /** 到达判定 — 女仆脚格到目标 distSqr ≤ 阈值 (调用方按需传) */
    public static boolean reached(EntityMaid maid, BlockPos pos, double distSqr) {
        return maid.blockPosition().distSqr(pos) <= distSqr;
    }

    /**
     * 可达预检 (v79.26.8b) — 目标格 3x3x3 邻域任一可站立格可达即走过去。
     * TLM canPathReach(pos) = isVis(pos) || isVis(pos.above()) — BFS 只访问行走格,
     * 实心目标格 (矿) 本身不可行走 → 水平矿也误判不可达 → 跳过。短路优先:
     * 目标自身 (含矿上方) → 矿顶 → 四水平邻 (格 + 其上方)。
     */
    private static boolean canReachNear(EntityMaid maid, BlockPos target) {
        if (canReach(maid, target)) return true;
        if (canReach(maid, target.above())) return true;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = target.relative(dir);
            if (canReach(maid, p) || canReach(maid, p.above())) return true;
        }
        return false;
    }

    // ── v79.26.7: 完整寻路门面 (TLM 导航 + 垂直挖穿兜底) ──

    /** TLM 导航看门狗起始 tick (maidId → 目标切换时重置); 超时 → FAILED */
    private static final ConcurrentMap<Integer, Long> NAV_START = new ConcurrentHashMap<>();

    /** v79.26.7: 导航结果 — WALKING=TLM 导航中 / DIGGING=本 tick 挖了方块 (挖穿中) /
     *  REACHED=1 格邻域 (调用方开脉) / FAILED=放弃 (TLM 不可达/导航超时 — 调用方跳过目标) */
    public enum NavOutcome { WALKING, DIGGING, REACHED, FAILED }

    /**
     * v79.26.7: 完整寻路 — TLM 导航 + 垂直挖穿兜底一键调用。
     * 管道只调本方法, 不再内联兜底。
     *
     * <p>v79.26.8e: 用户裁定简化 — 垫柱/面前挖穿/档位全删, 走路全 TLM:
     * 1 格邻域 → REACHED; 目标在脚下且 TLM 不可达 → 垂直挖穿 (深度配置
     * CHAIN_DIG_DOWN_DEPTH, 默认 6); 头顶目标由 idleScan 2.83 球直接开脉 (不垫柱);
     * 其余 TLM 导航 + 看门狗。
     *
     * <p>v79.26.8g: 上下双向 — 头顶目标 (3-6 格, 2.83 球外) TLM 不可达 →
     * 向上挖穿 (矿正下方整列, 掉落物落脚边)。
     *
     * <p>决策链: 1 格邻域 → REACHED (走到矿旁保证开脉); 目标在脚下/头顶且
     * TLM 不可达 → 垂直/向上挖穿; TLM 不可达/导航超时 → FAILED。挖穿中 →
     * 调用方 CONTINUE, 下轮重新决策。破块门 3 格球由调用方 charge 分支负责。
     */
    public static NavOutcome navigate(ServerLevel world, EntityMaid maid, BlockPos target) {
        // 到达判定 1 格邻域 (v79.26.8): 3 格球只在边缘 → 卡极限挖不到; 走到矿旁
        // 1 格 (3x3x3) 再开脉, 破块距离 3 格稳挖
        if (reached(maid, target, VanillaConstants.ONE_AWAY_DIST_SQR)) {
            return NavOutcome.REACHED;
        }
        // 垂直挖穿: 目标在脚下 (深度/水平/TLM 可达门控在协调器内) — 挖了 → DIGGING
        // (v79.26.8e: 深度默认 3→6; v79.26.8g: 上下双向 — digUp 头顶同深度挖穿)
        if (DigThroughCoordinator.digVertical(world, maid, target)) {
            return NavOutcome.DIGGING;
        }
        // v79.26.8g: 向上挖穿 — 头顶 ≤6 格目标 TLM 不可达 → 挖穿矿正下方整列
        // (含矿), 掉落物落脚边 (背包满也不卡石头)
        if (DigThroughCoordinator.digUp(world, maid, target)) {
            return NavOutcome.DIGGING;
        }
        // TLM 导航段: 幂等导航 + 看门狗; 可达预检只在目标切换时做一次 (每 tick 预检
        // = 每 tick 全 BFS 浪费; v79.26.8b: 预检目标改矿旁可站立格 — TLM canPathReach
        // 对实心目标格语义脆弱 (BFS 只访问行走格 — 矿格本身不可行走 → 水平矿误判
        // 不可达 → 跳过 → 女仆不走过去); 矿旁可站即走过去)
        long now = world.getGameTime();
        int id = maid.getId();
        BlockPos cur = navTarget(maid);
        if (cur == null || !cur.equals(target)) {
            if (!canReachNear(maid, target)) {
                clearNav(maid);
                // v79.26.8f: 只用 TLM 寻路 — 桥/阶梯 (BridgeCoordinator) 删, 垂直挖穿
                // 保留。TLM 不可达 → FAILED → 跳过集 60t 过期重试
                return NavOutcome.FAILED;
            }
            navigateTo(maid, target, 0.5F);
            NAV_START.put(id, now);
        }
        if (now - NAV_START.getOrDefault(id, now) >= ActiveTaskConfig.CHAIN_NAV_TIMEOUT.get()) {
            clearNav(maid);
            return NavOutcome.FAILED;
        }
        return NavOutcome.WALKING;
    }
}

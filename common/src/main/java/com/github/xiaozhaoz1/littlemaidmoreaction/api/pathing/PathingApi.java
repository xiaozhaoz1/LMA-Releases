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
 * TLM 导航不挖方块 — 头顶挖穿兜底 ({@link DigThroughCoordinator#digUp})。
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
 * <p>v79.26.8g: 头顶双向挖穿 — 头顶目标 ≤6 格 TLM 不可达 → 向上挖穿
 * ({@link DigThroughCoordinator#digUp}), 矿正下方整列挖通, 掉落物落脚下。
 * <p>v79.57: 脚下挖穿 (digVertical) 退役 — 下挖挖泥土换主手铲 → 主手非镐 →
 * ORE 管线卡住 (用户实测); 女仆只挖裸露表面矿, 头顶 ≤6 格 digUp 保留 (用户裁定)。
 * <p>v79.58: 头顶斜上矿可达 — {@link #canReachAround} 拆出 (挖穿门控专用, 不含正下方
 * 列 — 防头顶正上矿门控恒通过 digUp 恒拒绝死循环); {@link #canReachNear} 含目标正下方
 * 列 ({@link #standUnder}); navigate 斜上目标导航到矿下地面格 → 走到后 digUp 水平门
 * 归零自然触发 (用户裁定 "走到下面然后进行 6 格头顶挖矿")。
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

    /**
     * 到达对齐 — 水平速度朝当前格中心衰减 (v79.25 NavWatchdog 近程减速语义恢复, v79.53)。
     * 每 tick 幂等: 距中心 dist ≤ 0.05 完全停止; 否则朝中心 min(0.2, d*0.5) 近减速收敛。
     * 垂直分量保留 (防干扰跳跃/下落)。reached 分支每 tick 调用, 收敛后自然停稳。
     */
    private static void alignToCenter(EntityMaid maid) {
        BlockPos center = maid.blockPosition();
        double dx = center.getX() + 0.5 - maid.getX();
        double dz = center.getZ() + 0.5 - maid.getZ();
        double[] v = alignVelocity(dx, dz);
        maid.setDeltaMovement(v[0], maid.getDeltaMovement().y, v[1]);
    }

    /** 对齐速度纯函数 (v79.53) — 朝格中心衰减 min(0.2, d*0.5); dist ≤ 0.05 → 停止。
     *  返回 {vx, vz}; 纯 JVM 可测 (无 MC 依赖)。 */
    static double[] alignVelocity(double dx, double dz) {
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= 0.05) {
            return new double[]{0, 0};
        }
        double speed = Math.min(0.2, dist * 0.5);
        return new double[]{dx / dist * speed, dz / dist * speed};
    }

    /** 到达判定 — 女仆脚格到目标 distSqr ≤ 阈值 (调用方按需传) */
    public static boolean reached(EntityMaid maid, BlockPos pos, double distSqr) {
        return maid.blockPosition().distSqr(pos) <= distSqr;
    }

    /**
     * 矿旁可达判定 — 目标格 3x3x3 邻域任一可站立格可达即走过去 (不含目标正下方列)。
     * TLM canPathReach(pos) = isVis(pos) || isVis(pos.above()) — BFS 只访问行走格,
     * 实心目标格 (矿) 本身不可行走 → 水平矿也误判不可达 → 跳过。短路优先:
     * 目标自身 (含矿上方) → 矿顶 → 四水平邻 (格 + 其上方)。
     * 调用方: DigThroughCoordinator 挖穿门控 (错题 D1 — 原 canReach 对实心矿恒 false →
     * 门控恒失效恒挖穿)。不含正下方: 头顶矿的女仆脚下恒可达 → 含正下方会使头顶正上矿
     * 门控通过 → 挖穿拒绝 → 死循环 (v79.58 拆出)。
     */
    public static boolean canReachAround(EntityMaid maid, BlockPos target) {
        if (canReach(maid, target)) return true;
        if (canReach(maid, target.above())) return true;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = target.relative(dir);
            if (canReach(maid, p) || canReach(maid, p.above())) return true;
        }
        return false;
    }

    /**
     * 目标正下方列可站格 (v79.58) — 从 target.Y-1 往下 depth 格 (CHAIN_DIG_DOWN_DEPTH,
     * 与 digUp 垂直门一致) 找第一个 TLM 可达格。头顶斜上矿 (digUp 水平门外): 矿下地面
     * 可站 → navigate 导航到该格 → 走到后 digUp 水平门归零自然触发 (用户裁定
     * "走到下面然后进行 6 格头顶挖矿"); null = 矿下 depth 内无可站格。
     */
    @Nullable
    public static BlockPos standUnder(EntityMaid maid, BlockPos target) {
        int depth = ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get();
        for (int y = target.getY() - 1; y >= target.getY() - depth; y--) {
            BlockPos p = new BlockPos(target.getX(), y, target.getZ());
            if (canReach(maid, p)) return p;
        }
        return null;
    }

    /**
     * 可达预检 (v79.58 扩展) — 矿旁邻域 + 目标正下方列 ({@link #standUnder}).
     * 调用方: navigate 目标切换预检 — 头顶斜上矿矿下可站也算可达 (走到矿下再 digUp).
     * 挖穿门控不可用本方法 (正下方可达会误判"走正常路" — 见 {@link #canReachAround}).
     */
    public static boolean canReachNear(EntityMaid maid, BlockPos target) {
        return canReachAround(maid, target) || standUnder(maid, target) != null;
    }

    // ── 完整寻路门面 (TLM 导航 + 垂直挖穿兜底) ──

    /** TLM 导航看门狗起始 tick (maidId → 目标切换时重置); 超时 → FAILED */
    private static final ConcurrentMap<Integer, Long> NAV_START = new ConcurrentHashMap<>();

    /** 导航结果 — WALKING=TLM 导航中 / DIGGING=本 tick 挖了方块 (挖穿中) /
     *  REACHED=1 格邻域 (调用方开脉) / FAILED=放弃 (TLM 不可达/导航超时 — 调用方跳过目标) */
    public enum NavOutcome { WALKING, DIGGING, REACHED, FAILED }

    /**
     * 完整寻路 — TLM 导航 + 头顶挖穿兜底一键调用。
     * 管道只调本方法, 不再内联兜底。
     *
     * <p>用户裁定简化 — 垫柱/面前挖穿/档位全删, 走路全 TLM:
     * 1 格邻域 → REACHED; 头顶目标 (3-6 格, 2.83 球外) TLM 不可达 → 向上挖穿
     * (深度配置 CHAIN_DIG_DOWN_DEPTH, 默认 6; 矿正下方整列, 掉落物落脚边);
     * 其余 TLM 导航 + 看门狗。v79.57: 脚下挖穿退役 — 女仆只挖裸露表面矿
     * (用户裁定, 下挖换工具卡管线)。
     *
     * <p>决策链: 1 格邻域 → REACHED (走到矿旁保证开脉); 头顶目标且 TLM 不可达 →
     * 向上挖穿; TLM 不可达/导航超时 → FAILED。挖穿中 → 调用方 CONTINUE,
     * 下轮重新决策。破块门 3 格球由调用方 charge 分支负责。
     */
    public static NavOutcome navigate(ServerLevel world, EntityMaid maid, BlockPos target) {
        // 到达判定 1 格邻域: 3 格球只在边缘 → 卡极限挖不到; 走到矿旁
        // 1 格 (3x3x3) 再开脉, 破块距离 3 格稳挖
        if (reached(maid, target, VanillaConstants.ONE_AWAY_DIST_SQR)) {
            // v79.53: 到达对齐恢复 — v79.26.8f 删 NavWatchdog 时连带删除近程减速,
            // 女仆全速冲过头 → TLM 折返摆动 ("移动太快飞出去/到不了附近", 用户实测;
            // v79.26.8c 教训: "对齐归零水平速度 — 惯性冲过柱底 = 走太快表现, 0.5F 本身正确")
            clearNav(maid);
            alignToCenter(maid);
            return NavOutcome.REACHED;
        }
        // 向上挖穿 — 头顶 ≤6 格裸露矿 TLM 不可达 → 挖穿矿正下方整列
        // (含矿), 掉落物落脚边 (背包满也不卡石头)。v79.57: 脚下挖穿 (digVertical)
        // 退役 (下挖换主手铲 → 主手非镐 → 管线卡住, 用户裁定只挖裸露表面矿)。
        // 先于 navGoal REACHED 判定: 头顶正上矿 standUnder = 女仆脚下 → navGoal 恒
        // REACHED 死循环 (v79.58); 门控 canReachAround 不含正下方 (同款原因)。
        if (DigThroughCoordinator.digUp(world, maid, target)) {
            return NavOutcome.DIGGING;
        }
        // 头顶斜上目标 (digUp 水平门外) — 导航目标改矿正下方可站格 (v79.58 用户裁定
        // "走到下面然后进行 6 格头顶挖矿"): 原导航目标=矿格 (高空实心) TLM 走不到 →
        // 240t 看门狗 FAILED 且 canReachNear 不含正下方 → 直接 FAILED 零机会;
        // 走到矿下 → 下轮 digUp 水平门归零自然触发
        BlockPos under = null;
        if (target.getY() > maid.blockPosition().getY()) {
            under = standUnder(maid, target);
        }
        BlockPos navGoal = under != null ? under : target;
        if (reached(maid, navGoal, VanillaConstants.ONE_AWAY_DIST_SQR)) {
            clearNav(maid);
            alignToCenter(maid);
            return NavOutcome.REACHED;
        }
        // TLM 导航段: 幂等导航 + 看门狗; 可达预检只在目标切换时做一次 (每 tick 预检
        // = 每 tick 全 BFS 浪费; 预检目标改矿旁可站立格 — TLM canPathReach
        // 对实心目标格语义脆弱 (BFS 只访问行走格 — 矿格本身不可行走 → 水平矿误判
        // 不可达 → 跳过 → 女仆不走过去); 矿旁可站即走过去; 头顶斜上矿矿下可站也算
        // 可达 (canReachNear 含正下方列, v79.58))
        long now = world.getGameTime();
        int id = maid.getId();
        BlockPos cur = navTarget(maid);
        if (cur == null || !cur.equals(navGoal)) {
            if (!canReachNear(maid, target)) {
                clearNav(maid);
                // 只用 TLM 寻路 — 桥/阶梯 (BridgeCoordinator) 删, 垂直挖穿
                // 保留。TLM 不可达 → FAILED → 跳过集 60t 过期重试
                return NavOutcome.FAILED;
            }
            navigateTo(maid, navGoal, 0.5F);
            NAV_START.put(id, now);
        }
        if (now - NAV_START.getOrDefault(id, now) >= ActiveTaskConfig.CHAIN_NAV_TIMEOUT.get()) {
            clearNav(maid);
            return NavOutcome.FAILED;
        }
        return NavOutcome.WALKING;
    }
}

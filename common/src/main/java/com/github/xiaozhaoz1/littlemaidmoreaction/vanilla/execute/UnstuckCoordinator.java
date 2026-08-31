package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 卡住脱困协调器 (v79.61x 移植自 Numen UnstuckChain, dwinovo/minecraft-numen LGPL) —
 * 导航一直推但位置不动 (被地形楔住) 时, 转个新方向向前冲一小段 (30t 封顶) 挣脱出来。
 *
 * <p>女仆适配: "尝试移动" = TLM 导航激活态 ({@code !maid.getNavigation().isDone()});
 * 挣脱动作 = 转 137° 新朝向 + 前方 3 格 moveTo + 每 5t 跳一下 (Numem 同款参数)。
 * 有界尽力: 30t 封顶后停导航放手, 原任务导航下轮重设 — 绝不无限接管。
 *
 * <p>检测逻辑纯内核见 {@link UnstuckDetector} (JVM 可测); 本类持 per-maid 弱引用
 * 状态表 (SelfRescueState 同款模式), 卸载经 MaidUnloadRegistry 登记清理。
 */
public final class UnstuckCoordinator {

    /** 挣脱 burst 时长 (tick) — Numen 原值 */
    private static final int WANDER_TICKS = 30;
    /** 换向角度 — 每次 +137°, 反复尝试时扇形展开 */
    private static final float TURN_DEGREES = 137.0f;
    /** 跳跃间隔 (tick) — 每 5t 跳一下清除台阶/嘴唇地形 */
    private static final int JUMP_INTERVAL = 5;
    /** burst 前进距离 (格) */
    private static final double BURST_DISTANCE = 3.0;

    /** per-maid 状态 — 检测器 + burst 进度 (弱引用表不阻止实体 GC) */
    private static final class BurstState {
        final UnstuckDetector detector = new UnstuckDetector();
        int burstTicksLeft;
        float wanderYaw;
    }

    private static final ConcurrentMap<UUID, BurstState> STATES = new ConcurrentHashMap<>();

    private UnstuckCoordinator() {}

    /**
     * 每 tick 采样 + 脱困驱动。
     *
     * @return true = 本 tick 在处理 (burst 中 / 刚触发) — 调用方跳过其余自救动作
     */
    public static boolean tick(ServerLevel world, EntityMaid maid) {
        BurstState s = STATES.computeIfAbsent(maid.getUUID(), k -> new BurstState());
        // 每 tick 采样 — 女仆导航激活态 = "尝试移动" (TLM SendMaidDebugDataEvent 同款判定)
        boolean trying = !maid.getNavigation().isDone();
        s.detector.record(maid.getX(), maid.getZ(), trying);

        if (s.burstTicksLeft > 0) {
            // 完成 burst
            driveWander(maid, s);
            if (--s.burstTicksLeft <= 0) {
                maid.getNavigation().stop();
            }
            return true;
        }
        if (!s.detector.isStuck()) return false;
        // 触发: 换新朝向 + 清窗重评 (Numen 同款)
        s.wanderYaw = maid.getYRot() + TURN_DEGREES;
        s.burstTicksLeft = WANDER_TICKS;
        s.detector.reset();
        driveWander(maid, s);
        return true;
    }

    /** 朝选定朝向前进 + 周期跳 — 女仆版 (导航代替假人 zza 输入) */
    private static void driveWander(EntityMaid maid, BurstState s) {
        maid.setYRot(s.wanderYaw);
        maid.setYHeadRot(s.wanderYaw);
        float rad = s.wanderYaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(rad) * BURST_DISTANCE;
        double fz = Mth.cos(rad) * BURST_DISTANCE;
        maid.getNavigation().moveTo(maid.getX() + fx, maid.getY(), maid.getZ() + fz, 1.0);
        if (s.burstTicksLeft % JUMP_INTERVAL == 0) {
            // JumpControl.jump() — jumpFromGround 在 LivingEntity 是 protected (编译实证)
            maid.getJumpControl().jump();
        }
    }

    /** 任务终结/自终结清理 — 下一周期从干净窗口重新评估 */
    public static void clear(EntityMaid maid) {
        STATES.remove(maid.getUUID());
    }

    /** 卸载清理 (MaidUnloadRegistry 登记) */
    public static void onMaidUnload(EntityMaid maid) {
        STATES.remove(maid.getUUID());
    }
}

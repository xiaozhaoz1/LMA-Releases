package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.PassiveSignalSkeleton;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.HeatSourceQuery;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WaterQuery;

/**
 * v63: 温度自适应被动任务。
 *
 * <p>COLD → 寻找篝火/岩浆, 导航 → 目标。
 * HOT  → 寻找水源, 导航 → 目标。
 * NORMAL → 停止。
 *
 * <p>所有状态存 pipelineData (lma_pl_temp_adapt) — clearPipelineData 自动清理。
 */
public final class TempAdaptPipeline implements PassiveSignalSkeleton, TaskConfigurable {

    @Override public String taskType() { return "temp_adapt"; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return okSignals(Set.of(Signals.ENV_TEMP_COLD, Signals.ENV_TEMP_HOT, Signals.ENV_TEMP_NORMAL));
    }

    /** NORMAL 重触发冷却 (tick) — 阈值边沿抖动压制 (日志实证 7s 内 cancel→submit) */
    private static final int NORMAL_COOLDOWN_TICKS = 200;
    private static final String THROTTLE_GUARD = "temp_adapt_guard";
    /** 不可达背压: 同目标连续无进展次数上限 (≥上限 → interval 600) */
    private static final int STUCK_LIMIT = 3;
    /** 到达判定: 4 格内视为已接近 (不背压 — 已到目标旁等温度恢复) */
    private static final double ARRIVE_DIST_SQR = 16.0;

    // ── v79.61x 用户裁定: 取暖/降温停留 + 长冷却 (防女仆一直往火旁跑不去工作) ──
    /** 到达热源/水源后停留时长 (tick, 30 秒) — 取暖/降温完成的体感停留 */
    private static final int WARM_TICKS = 600;
    /** 触发后全局冷却 (tick, 5 分钟) — v79.62.2 用户裁定: 触发一次走半分钟, 5 分钟内不再触发 (不持续走) */
    private static final int TEMP_CD_TICKS = 6000;
    /** 取暖完成冷却截止键 (maid PersistentData 根, 存绝对截止 tick; cancelPassive 不清根键) */
    static final String KEY_CD_DEADLINE = "lma_temp_cd";
    /** 取暖计时键 (pipelineData — cancelPassive 自动清理) */
    private static final String KEY_WARM_TICKS = "warm_ticks";
    private static final String KEY_WARM_LAST = "warm_last";

    /** CD 判定纯函数 (JVM 可测) — cd<=now 或 cd==0 (未设置) → 已冷却可触发 */
    static boolean isCooled(long cdDeadline, long now) {
        return cdDeadline <= now;
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, String signal) {
        var pd = pipelineData(maid);
        switch (signal) {
            case Signals.ENV_TEMP_COLD -> {
                // v79.62.2 用户裁定: 触发即写 5 分钟 CD (触发一次走半分钟, 不持续走)
                if (isCooled(maid.getPersistentData().getLong(KEY_CD_DEADLINE), maid.level().getGameTime())) {
                    // v79.61x 冷却门: NORMAL 后 200t 内忽略重提交 (阈值抖动压制)
                    if (!coolingDown(maid)) {
                        pd.putString("goal", "warm");
                        // 触发即 CD: 5 分钟内不再响应 (防一直往火旁跑/持续走)
                        maid.getPersistentData().putLong(KEY_CD_DEADLINE,
                                maid.level().getGameTime() + TEMP_CD_TICKS);
                        TaskDispatcher.submitPassive(maid, taskType());
                    }
                }
            }
            case Signals.ENV_TEMP_HOT -> {
                if (isCooled(maid.getPersistentData().getLong(KEY_CD_DEADLINE), maid.level().getGameTime())) {
                    if (!coolingDown(maid)) {
                        pd.putString("goal", "cool");
                        // 触发即 CD: 5 分钟内不再响应
                        maid.getPersistentData().putLong(KEY_CD_DEADLINE,
                                maid.level().getGameTime() + TEMP_CD_TICKS);
                        TaskDispatcher.submitPassive(maid, taskType());
                    }
                }
            }
            case Signals.ENV_TEMP_NORMAL -> {
                // 冷却门写戳 (shouldFire 副作用) — COLD/HOT 冷却期内被忽略
                com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                        .shouldFire(maid, THROTTLE_GUARD, NORMAL_COOLDOWN_TICKS);
                TaskDispatcher.cancelPassive(maid, taskType());
            }
        }
    }

    /** 冷却门判定 — NORMAL 后 200t 内 COLD/HOT 重提交被忽略 (时间戳自过期, 残留无害) */
    private static boolean coolingDown(EntityMaid maid) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                .cooldownRemaining(maid, THROTTLE_GUARD, NORMAL_COOLDOWN_TICKS) > 0;
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        var pd = pipelineData(maid);
        // v79.62.2 修「每几秒往火/水边跑」: CD 未冷却 (触发即写 5 分钟 CD) → 立即停止本 pipeline
        // (onSignal 的 CD 只挡重提交, 不 cancel 已运行的 tick — 原 tick 每 100t 重新 moveTo 目标
        // 且到不了 4 格内永不完成 → 一直导航 = 用户实证). CD 内不导航.
        if (!isCooled(maid.getPersistentData().getLong(KEY_CD_DEADLINE), world.getGameTime())) {
            TaskDispatcher.cancelPassive(maid, taskType());
            return;
        }
        String goal = pd.getString("goal");
        if (goal.isEmpty()) { TaskDispatcher.cancelPassive(maid, taskType()); return; }

        // v79.61x S3: 扫描节奏改 ThrottleUtil 时戳节流 (原 "Cd" 递减自管); 间隔存 pipelineData
        // (默认 100, 无目标时 600 背压) — 行为与原先 CD=100/600 语义一致
        int interval = pd.getInt("interval");
        if (interval <= 0) interval = 100;
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                .shouldFire(maid, "temp_adapt", interval)) {
            return;
        }

        int radius = PassiveTaskConfig.ENV_DEFAULT_RADIUS.get();
        BlockPos center = maid.blockPosition();
        BlockPos target = switch (goal) {
            case "warm" -> HeatSourceQuery.nearestHeatSource(world, center, radius);
            case "cool" -> WaterQuery.nearestWaterSource(world, center, radius);
            default -> null;
        };

        if (target != null) {
            double distSqr = center.distSqr(target);
            // v79.62.2 用户裁定: 到火/水旁 4 格内 → 立即写 5 分钟 CD + 结束 (不停留 30 秒取暖)
            if (distSqr <= ARRIVE_DIST_SQR) {
                resetWarm(pd);
                long now = world.getGameTime();
                maid.getPersistentData().putLong(KEY_CD_DEADLINE, now + TEMP_CD_TICKS);
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                        "[LMA/Temp] {} 到达 {} (写 5min CD)", goal, target.toShortString());
                TaskDispatcher.cancelPassive(maid, taskType());
                return;
            }
            // v79.61x 不可达背压: 同目标连续 STUCK_LIMIT 次无进展 → interval 600
            // (目标变化/距离减少/到达 4 格内 → 重置 100) — 原隔墙热源反复 100t 重寻路
            resetWarm(pd);
            boolean sameTarget = pd.getInt("last_tx") == target.getX()
                    && pd.getInt("last_ty") == target.getY()
                    && pd.getInt("last_tz") == target.getZ();
            double lastDist = pd.getDouble("last_dist");
            int stuck = pd.getInt("stuck");
            if (sameTarget && distSqr > ARRIVE_DIST_SQR && distSqr >= lastDist - 0.5) {
                stuck++;
            } else {
                stuck = 0;
            }
            pd.putInt("stuck", stuck);
            pd.putInt("last_tx", target.getX());
            pd.putInt("last_ty", target.getY());
            pd.putInt("last_tz", target.getZ());
            pd.putDouble("last_dist", distSqr);
            pd.putInt("interval", stuck >= STUCK_LIMIT ? 600 : 100);
            // v79.62 气泡 (借鉴 soulcraft 可爱动作 — 节流 600t=30s, 目标变化时各发一次):
            // 走向篝火/岩浆 (warm) 或 走向水源 (cool) — 让主人知道女仆在被动调温
            if (!sameTarget) {
                boolean fired = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                        .shouldFire(maid, "temp_adapt_bubble", 600);
                if (fired) {
                    String msg = "warm".equals(goal) ? "好冷…去烤火" : "太热了，去水边凉快一下";
                    com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi
                            .showInfo(maid, msg);
                }
            }
            // v79.61x 用户裁定 B: 导航指向热源旁安全落点 (防岩浆块烫脚/岩浆贴边);
            // 找不到安全格 → 兜底热源本身 (原行为)
            BlockPos stand = HeatSourceQuery.safeStand(world, target);
            BlockPos navTarget = stand != null ? stand : target;
            maid.getNavigation().moveTo(navTarget.getX() + 0.5, navTarget.getY(),
                    navTarget.getZ() + 0.5, 0.8);
        } else {
            resetWarm(pd);
            pd.putInt("interval", 600);
        }
    }

    /** 移动离开/无目标 → 重置取暖计时 (下次重新累计) */
    private static void resetWarm(CompoundTag pd) {
        pd.remove(KEY_WARM_TICKS);
        pd.remove(KEY_WARM_LAST);
    }
    // onCleanup 用接口默认 (clearPipelineData) — 删除冗余覆写

}

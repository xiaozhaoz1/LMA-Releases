package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.MlgRescueCoordinator;

/**
 * 自救摔落预触发 (v79.61x) — 掉血事件通道 (MaidDamageListener) 之外的第二触发口。
 *
 * <p>MLG 摔落自救必须在<b>摔落中</b>启动 (落地掉血才提交 = 已摔完, 放水无意义) —
 * 与哈气独立触发 (HaqiTrigger) 同款设计: TaskTickHandler 主循环内联调用
 * {@link #tryTrigger} (零额外实体遍历), 便宜判定先行 (grounded 四态 + 速度阈值,
 * 2 字段读), 只在真在快速下落的女仆身上扫背包查救命物。
 */
public final class SelfRescueTrigger {

    private SelfRescueTrigger() {}

    /** 单女仆判定 — TaskTickHandler 主循环内联调用 (每 tick, 零额外遍历) */
    public static void tryTrigger(EntityMaid maid) {
        if (!PassiveTaskConfig.SELF_RESCUE_ENABLED.get()) return;
        String key = TaskKeys.passiveKey("self_rescue");
        boolean alreadyRunning = TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(key));
        // v79.62.1 蜘蛛网缠住触发 — 女仆脚格/上方是蜘蛛网 (不窒息不掉血, 但 makeStuckInBlock 困住)
        // 便宜判定先行 (2 方块读), 触发后 SelfRescueCoordinator 挖掉
        if (isInCobweb(maid)) {
            if (!alreadyRunning) TaskDispatcher.submitPassive(maid, "self_rescue");
            return;
        }
        // 便宜判定先行 — 不在快速下落直接走 (每 tick 全女仆 2 字段读)
        if (!MlgRescueCoordinator.fallingFast(maid)) return;
        // 防重复 (自身运行中 — 掉血通道已提交的也不重提)
        if (alreadyRunning) return;
        // 救命物 (贵判定 — 只对真在摔的女仆扫背包)
        if (!MlgRescueCoordinator.hasSaveItem(maid)) return;
        TaskDispatcher.submitPassive(maid, "self_rescue");
    }

    /** 女仆是否被蜘蛛网缠住 — 脚格 或 脚上一格 (头/身) 是 COBWEB */
    private static boolean isInCobweb(EntityMaid maid) {
        var level = maid.level();
        if (level == null) return false;
        var pos = maid.blockPosition();
        return level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.COBWEB)
                || level.getBlockState(pos.above()).is(net.minecraft.world.level.block.Blocks.COBWEB);
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.task.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/**
 * v79.48: 自动修复行为 — 非 idle 按时间慢慢修 (core = 所有 activity 都跑)。
 *
 * <p>100t ≈ 5 秒修 1 点耐久 (1 分钟 12 点); 优先级 5 (低, 不抢主行为);
 * 无打断: 只改 ItemStack + 经验, 无动画。总开关 REPAIR_AUTO_ENABLED (ActiveTaskConfig)。
 * 消耗: max(1, 4 × 好感度消耗乘区) XP/点 (原版 Mending 2, LMA 基数 2 倍; Lv3 0.5 → 原版水平)。
 */
public final class AutoRepairBehavior extends MaidCheckRateTask {

    public AutoRepairBehavior() {
        super(Map.of(), 200);   // 2 参构造 (Map, duration) — 双平台同签名 (javap 实证)
        setMaxCheckRate(100);   // ≈ 5 秒 1 点
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid owner, long gameTimeIn) {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.REPAIR_AUTO_ENABLED.get()) {
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter.repairOneWithXp(owner);
        }
    }
}

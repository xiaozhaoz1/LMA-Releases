package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.Random;

/**
 * 哈气独立触发 (v79.11) — 不依赖 EnvSense 广播 (ENVSENSE_ENABLED 默认关 → 信号永不产生,
 * 用户实测哈气永不触发的根因)。每 20t 直接扫描女仆周围 2 格内的其他女仆 → 概率掷骰 →
 * 锁定目标 + submitPassive。onSignal (EnvSense 通道) 保留 — 双通道。
 *
 * <p>v79.20 节流顺序 (用户裁定, 对女仆优先): 先查 2 格内女仆 → 命中概率 → 锁女仆;
 * 未触发 (无女仆或概率未中) → 再查 2 格内主人 → 独立概率 (HAQI_CHANCE_TO_OWNER) →
 * 锁主人 (目标类型 target_type=owner, 与对女仆区分)。
 */
public final class HaqiTrigger {

    /** 触发距离: 3 格 (distSqr <= 9, v79.14c 2→3) */
    private static final double TRIGGER_DIST_SQR = 9.0;
    /** v79.20: 对主人触发距离: 2 格 (distSqr <= 4) */
    private static final double OWNER_TRIGGER_DIST_SQR = 4.0;

    private static final Random RANDOM = new Random();

    private HaqiTrigger() {}

    /** 每 20t 调 — TaskTickHandler 挂载; 遍历维度内女仆触发 */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) return;
        // v79.20: 总开关 + 对主人二级开关, 任一开则扫描 (tryTrigger 内部分别门控)
        if (!PassiveTaskConfig.HAQI_ENABLED.get() && !PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.get()) return;
        if (TaskRegistry.get("haqi") == null) return;
        for (var e : level.getAllEntities()) {
            if (e instanceof EntityMaid maid && maid.isAlive()) {
                tryTrigger(level, maid);
            }
        }
    }

    /**
     * 触发判定 — v79.20 节流: 先 2 格内其他女仆 → 概率 → 锁女仆; 未触发 → 对主人变体。
     * (对女仆优先, 用户裁定)
     */
    public static void tryTrigger(ServerLevel level, EntityMaid maid) {
        // 防重复 (自身运行中)
        String key = TaskKeys.passiveKey("haqi");
        if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(key))) return;

        // 2 格内其他女仆 (AABB 直接扫描 — 独立于 EnvSense 快照)
        EntityMaid target = null;
        double best = Double.MAX_VALUE;
        BlockPos center = maid.blockPosition();
        for (EntityMaid m : level.getEntitiesOfClass(EntityMaid.class,
                new AABB(center).inflate(2, 2, 2))) {
            if (m == maid || !m.isAlive()) continue;
            double d = m.blockPosition().distSqr(center);
            if (d <= TRIGGER_DIST_SQR && d < best) {
                best = d;
                target = m;
            }
        }

        // v79.20: 有女仆且概率命中 → 锁女仆 (显式写 target_type=maid)
        if (target != null && RANDOM.nextDouble() < PassiveTaskConfig.HAQI_CHANCE.get()) {
            // 锁定目标 + 提交 (v79.12: 写 lma_pl_haqi compound — 原写根导致状态机读空立即取消)
            var data = HaqiPipeline.stateData(maid);
            data.putString(HaqiPipeline.KEY_TARGET, target.getStringUUID());
            data.putString(HaqiPipeline.KEY_TARGET_TYPE, HaqiPipeline.TARGET_MAID);
            data.putString(HaqiPipeline.KEY_STATE, "MOVE");
            data.putInt(HaqiPipeline.KEY_TIMER, 0);
            TaskDispatcher.submitPassive(maid, "haqi");
            return;
        }
        // 女仆未触发 (无目标或概率未中) → 对主人变体 (独立开关/概率/距离)
        tryTriggerOwner(maid);
    }

    /**
     * v79.20: 对主人变体触发 — 2 格内主人 (TLM getOwner, 在线玩家) → 独立概率掷骰 →
     * 锁定主人 (target_type=owner) + submitPassive。主人不反击 (玩家无自动反击)。
     */
    public static void tryTriggerOwner(EntityMaid maid) {
        if (!PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.get()) return;
        // TLM getOwner 覆写已返回 LivingEntity (在线玩家), 无需 instanceof
        LivingEntity owner = maid.getOwner();
        if (owner == null || !owner.isAlive()) return;
        if (owner.blockPosition().distSqr(maid.blockPosition()) > OWNER_TRIGGER_DIST_SQR) return;
        // 独立概率 (默认 10%)
        if (RANDOM.nextDouble() >= PassiveTaskConfig.HAQI_CHANCE_TO_OWNER.get()) {
            return;
        }
        var data = HaqiPipeline.stateData(maid);
        data.putString(HaqiPipeline.KEY_TARGET, owner.getStringUUID());
        data.putString(HaqiPipeline.KEY_TARGET_TYPE, HaqiPipeline.TARGET_OWNER);
        data.putString(HaqiPipeline.KEY_STATE, "MOVE");
        data.putInt(HaqiPipeline.KEY_TIMER, 0);
        TaskDispatcher.submitPassive(maid, "haqi");
    }
}

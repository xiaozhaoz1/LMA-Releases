package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 自救上下文记录 (v79.58 预留) — 伤害事件 → 被动 tick 通道无参数传递,
 * 未来自救方法 (低血进食/岩浆脱困/逃跑寻路 等) 按此分发。
 *
 * <p>v1 仅记录血量快照 (掉血后比例 + 受伤 tick); 伤害类型 (DamageSource tag)
 * 双平台 API 有差异 (1.20 getMsgId / 1.21 type().msgId()), 待扩展方法需要时
 * 再按双平台条件化接入。
 *
 * <p>per-maid 内存态 (弱引用表 — 不阻止实体 GC); 卸载经 MaidUnloadRegistry
 * 登记清理 (红线 #8); 消费完由 SelfRescuePipeline.tick 显式 clear。
 */
public final class SelfRescueState {

    /** 快照 — 掉血后血量比例 (0-1) + 受伤 tick */
    public record State(float healthRatio, long hurtTick) {}

    private static final ConcurrentMap<UUID, State> STATES = new ConcurrentHashMap<>();

    private SelfRescueState() {}

    /** 记录掉血快照 (事件在扣血前触发 — 掉血后比例 = (当前血 - 本次伤害) / 最大血) */
    public static void record(EntityMaid maid, float amount) {
        if (amount <= 0) return;
        float after = Math.max(0f, maid.getHealth() - amount);
        STATES.put(maid.getUUID(), new State(after / Math.max(1f, maid.getMaxHealth()),
                maid.level().getGameTime()));
    }

    /** 最近快照; 无 → null */
    public static State snapshot(EntityMaid maid) {
        return STATES.get(maid.getUUID());
    }

    /** 消费完清除 (SelfRescuePipeline 自终结时) */
    public static void clear(EntityMaid maid) {
        STATES.remove(maid.getUUID());
    }

    /** 卸载清理 (MaidUnloadRegistry 登记) */
    public static void onMaidUnload(EntityMaid maid) {
        STATES.remove(maid.getUUID());
    }
}

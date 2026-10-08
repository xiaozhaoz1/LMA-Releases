package com.github.xiaozhaoz1.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import net.minecraft.server.level.ServerPlayer;

/**
 * 任务信号监听维度接口 (v79.28 接口瘦身 — 从 TaskPipeline 拆分)。
 *
 * <p>管线按需实现本接口获得: 环境信号回调 (EnvSenseBroadcaster 全局广播) /
 * 玩家按键触发 (v79.51 KeyTrigger 线路: C→S InteractTriggerPacket(keyId) →
 * {@link com.github.xiaozhaoz1.littlemaidmoreaction.network.KeyTriggerRegistry} 引擎分发)。
 * 不实现 = 无信号面, 零样板。
 */
public interface TaskSignalListener {

    /**
     * 环境信号回调。EnvSenseBroadcaster 在全球广播命中匹配信号时调用。
     * 仅在 toggle 开启 且 validate().needsSignals() 包含该信号时触发。
     *
     * <p>信号泛化 — 第 3 参由 EnvSignal 枚举改为 String 信号 id
     * (event:/env: 前缀, 见 {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals})。
     */
    default void onSignal(EntityMaid maid, EnvSnapshot snap, String signalId) {
        // 子类覆写
    }

    /**
     * 玩家按键触发回调 (v79.51 KeyTrigger 线路: C→S {@code InteractTriggerPacket(keyId)}
     * → {@link com.github.xiaozhaoz1.littlemaidmoreaction.network.KeyTriggerRegistry} 引擎分发)。
     *
     * <p>客户端按键 → 携带 keyId 发包 → 服务端扫描玩家周围 owned 女仆 (范围
     * ActiveTaskConfig.BI_TRIGGER_RANGE) → 对每个女仆查注册表 handler (首个消费者
     * block_interact: 按当前任务查 {@link TaskRegistry} → 调用本方法)。
     * 默认 no-op — 支持按键触发的任务覆写。
     */
    default void onPlayerTrigger(EntityMaid maid, ServerPlayer player) {
        // 子类覆写
    }
}

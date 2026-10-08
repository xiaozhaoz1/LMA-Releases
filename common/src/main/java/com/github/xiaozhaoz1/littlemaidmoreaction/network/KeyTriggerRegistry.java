package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 按键触发回调 — keyId 注册后, 按键命中时对玩家周围每个 owned 女仆调用。
 * (package-private: 与 {@link KeyTriggerRegistry} 同文件, 引用方同包)
 */
@FunctionalInterface
interface KeyTriggerHandler {
    void handle(EntityMaid maid, ServerPlayer player);
}

/**
 * 通用按键触发注册表 (v79.51 KeyTrigger 线路) — 客户端按键 (KeyMapping) → C2S
 * {@link InteractTriggerPacket} (keyId) → 服务端查本表 → 对范围内 owned 女仆逐一回调。
 *
 * <p>首个消费者 {@code block_interact} (恢复 v67 手动触发语义, BlockInteractPipeline 覆写
 * {@link TaskSignalListener#onPlayerTrigger})。任意任务/系统调用
 * {@link #register(String, KeyTriggerHandler)} 注册即可响应按键, 无需改网络包。
 *
 * <p>静态表只存 handler 代码引用 (TaskRegistry 同模式) — 不存实体, 无泄漏,
 * 免 MaidUnloadRegistry 登记 (红线 #7 语义不触)。
 */
public final class KeyTriggerRegistry {

    private static final Map<String, KeyTriggerHandler> HANDLERS = new HashMap<>();
    private static boolean initialized = false;

    private KeyTriggerRegistry() {}

    /** 内置按键注册 (LmaRegistrar.init 挂载) — 幂等, 只执行一次 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // block_interact: 引擎级任务分发 — 仅实现 TaskSignalListener 的管线响应按键
        register("block_interact", (maid, player) -> {
            TaskRegistry.TaskHandler h = TaskRegistry.get(FlowTaskData.getTask(maid));
            if (h != null && h.pipeline() instanceof TaskSignalListener l) {
                l.onPlayerTrigger(maid, player);
            }
        });
    }

    /** 通用注册 — 重复 keyId 直接抛 (防静默覆盖) */
    public static void register(String keyId, KeyTriggerHandler handler) {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.containsKey(keyId)) {
            throw new IllegalArgumentException("KeyTrigger keyId 重复注册: " + keyId);
        }
        HANDLERS.put(keyId, handler);
    }

    /** 查 — 未注册返回 null (包 handle 层容错静默) */
    @Nullable
    public static KeyTriggerHandler get(String keyId) {
        return HANDLERS.get(keyId);
    }
}

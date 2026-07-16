package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.ServerTaskQueue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 桥接: 将 ServerTaskQueue 注入服务端 Tick 事件 (v35.1)。
 *
 * <p>init() 必须在 FMLCommonSetupEvent 中调用。
 */
public final class ForgeTaskQueueBridge {

    private static volatile boolean initialized = false;

    /** 启动桥接 — 幂等 */
    public static void init() {
        if (initialized) return;
        synchronized (ForgeTaskQueueBridge.class) {
            if (initialized) return;
            MinecraftForge.EVENT_BUS.register(new TickHandler());
            ServerTaskQueue.markInitialized();
            initialized = true;
            if (MoreActionConfig.DEBUG_MODE.get()) {
                LittleMaidMoreAction.LOGGER.debug("[ForgeTaskQueueBridge] 已初始化");
            }
        }
    }

    /** Tick 事件处理器 — 每 ServerTickEvent.END 消费队列 */
    static class TickHandler {
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            ServerTaskQueue.pollAll();
        }
    }

    private ForgeTaskQueueBridge() {}
}

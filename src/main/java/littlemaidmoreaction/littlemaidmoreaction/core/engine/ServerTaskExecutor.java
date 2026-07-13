package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 服务端主线程任务执行器。
 *
 * <p>所有修改游戏状态的 CompletableFuture 必须通过 submit() 提交，
 * 确保实际修改在 Tick 线程串行执行。
 *
 * <p>init() 必须在 FMLCommonSetupEvent 中主动调用（不采用延迟注册）。
 */
public final class ServerTaskExecutor {

    private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile boolean initialized = false;

    /**
     * 主动初始化。必须在 FMLCommonSetupEvent 中调用。
     * 幂等操作。
     */
    public static void init() {
        if (initialized) return;
        synchronized (ServerTaskExecutor.class) {
            if (initialized) return;
            MinecraftForge.EVENT_BUS.register(new TickHandler());
            initialized = true;
            if (MoreActionConfig.DEBUG_MODE.get()) {
                LittleMaidMoreAction.LOGGER.debug("[ServerTaskExecutor] 已初始化");
            }
        }
    }

    /**
     * 提交任务到主线程执行。线程安全。
     *
     * @throws IllegalStateException 若 init() 未被调用
     */
    public static CompletableFuture<Void> submit(Runnable task) {
        if (!initialized) {
            throw new IllegalStateException(
                "ServerTaskExecutor 未初始化。请在 FMLCommonSetupEvent 中调用 init()");
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        QUEUE.add(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** Tick 事件处理器 — 每 ServerTickEvent.END 消费队列 */
    static class TickHandler {
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Runnable task;
            while ((task = QUEUE.poll()) != null) {
                task.run();
            }
        }
    }

    /**
     * <strong>仅供测试使用。</strong>重置初始化状态，使后续 submit() 调用抛出
     * {@link IllegalStateException}，除非再次调用 {@link #init()}。
     */
    static void resetForTest() {
        initialized = false;
    }

    private ServerTaskExecutor() {}
}

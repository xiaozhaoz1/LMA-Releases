package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 服务端主线程任务队列 (v35.1: 零 MC 依赖, Forge 事件注册已提取到 ForgeTaskQueueBridge)。
 *
 * <p>所有修改游戏状态的 CompletableFuture 必须通过 submit() 提交。
 * 桥接层负责在 Tick 线程串行消费队列。
 */
public final class ServerTaskQueue {

    private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile boolean initialized = false;

    /** 标记已初始化 (由桥接层调用) */
    public static void markInitialized() { initialized = true; }

    /**
     * 提交任务到队列。线程安全。
     *
     * @throws IllegalStateException 若未初始化
     */
    public static CompletableFuture<Void> submit(Runnable task) {
        if (!initialized) {
            throw new IllegalStateException(
                "ServerTaskQueue 未初始化。请在 FMLCommonSetupEvent 中调用 ForgeTaskQueueBridge.init()");
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        QUEUE.add(() -> {
            try { task.run(); future.complete(null); }
            catch (Exception e) { future.completeExceptionally(e); }
        });
        return future;
    }

    /** 消费队列中所有任务 (由桥接层 Tick 事件调用) */
    public static void pollAll() {
        Runnable task;
        while ((task = QUEUE.poll()) != null) {
            task.run();
        }
    }

    /** ★ 仅供测试使用 */
    static void resetForTest() { initialized = false; }

    private ServerTaskQueue() {}
}

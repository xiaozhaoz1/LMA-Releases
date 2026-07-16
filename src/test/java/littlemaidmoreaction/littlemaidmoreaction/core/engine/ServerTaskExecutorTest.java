package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ServerTaskQueue} 的单元测试 (v35.1: 从 ServerTaskExecutor 重命名)。
 *
 * <p>测试纯 Java 队列逻辑，不依赖 Forge/MC 运行时。
 */
class ServerTaskExecutorTest {

    @BeforeEach
    void reset() {
        ServerTaskQueue.resetForTest();
    }

    @Test
    @DisplayName("submit() throws IllegalStateException if not initialized")
    void submit_throwsIfNotInitialized() {
        assertThrows(IllegalStateException.class, () -> ServerTaskQueue.submit(() -> {}));
    }

    @Test
    @DisplayName("markInitialized() is idempotent")
    void markInitialized_isIdempotent() {
        setInitialized(true);
        ServerTaskQueue.markInitialized();
        assertDoesNotThrow(() -> ServerTaskQueue.submit(() -> {}));
    }

    @Test
    @DisplayName("submit() returns a CompletableFuture that is not null and not completed")
    void submit_returnsIncompleteFuture() {
        setInitialized(true);

        var future = ServerTaskQueue.submit(() -> {});

        assertNotNull(future);
        // 测试环境无 tick 消费，future 应保持未完成
        assertFalse(future.isDone());
    }

    /** 通过反射设置 initialized 标志位 */
    private static void setInitialized(boolean value) {
        try {
            var field = ServerTaskQueue.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set initialized via reflection", e);
        }
    }
}

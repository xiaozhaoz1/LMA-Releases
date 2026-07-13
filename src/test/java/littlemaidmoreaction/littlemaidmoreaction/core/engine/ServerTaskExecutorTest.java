package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ServerTaskExecutor} 的单元测试。
 *
 * <p>注意：测试环境不加载 MinecraftForge，因此 {@link ServerTaskExecutor#init()}
 * 无法正常执行。对于需要 {@code initialized = true} 的测试用例，通过反射直接设置标志位。
 */
class ServerTaskExecutorTest {

    @BeforeEach
    void reset() {
        ServerTaskExecutor.resetForTest();
    }

    @Test
    @DisplayName("submit() throws IllegalStateException if init() was not called")
    void submit_throwsIfNotInitialized() {
        assertThrows(IllegalStateException.class, () -> ServerTaskExecutor.submit(() -> {}));
    }

    @Test
    @DisplayName("init() is idempotent (calling twice doesn't throw)")
    void init_isIdempotent() {
        // 通过反射设置 initialized = true，模拟已初始化状态
        setInitialized(true);

        // 再次调用 init()，应因幂等性检查而不抛出异常
        ServerTaskExecutor.init();
        ServerTaskExecutor.init();
    }

    @Test
    @DisplayName("submit() returns a CompletableFuture that is not null and not completed")
    void submit_returnsIncompleteFuture() {
        setInitialized(true);

        var future = ServerTaskExecutor.submit(() -> {});

        assertNotNull(future);
        // 由于测试环境中没有 tick 事件消费队列，future 应保持未完成状态
        assertFalse(future.isDone());
    }

    /** 通过反射设置 {@code initialized} 标志位，绕过 Forge 测试环境限制。 */
    private static void setInitialized(boolean value) {
        try {
            var field = ServerTaskExecutor.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set initialized via reflection", e);
        }
    }
}

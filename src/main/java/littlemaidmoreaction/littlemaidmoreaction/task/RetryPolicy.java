package littlemaidmoreaction.littlemaidmoreaction.task;

/**
 * v45: 任务重试策略 — 从 TaskPipeline 内部枚举提取为独立类。
 *
 * <p>使用方式:
 * <pre>{@code
 * // Pipeline 中覆写:
 * public RetryPolicy retryPolicy() { return RetryPolicy.always(); }
 * public RetryPolicy retryPolicy() { return RetryPolicy.fixed(3); }
 *
 * // Dispatcher 中判断:
 * RetryPolicy rp = pipeline.retryPolicy();
 * if (rp.shouldRetry(retryCount)) { ... }
 * }</pre>
 *
 * <p>NEVER: 不重试 (默认). ALWAYS: 无限重试. fixed(N): 最多 N 次.
 */
public final class RetryPolicy {
    /** 不重试 */
    public static final RetryPolicy NEVER = new RetryPolicy(0);
    /** 无限重试 */
    public static final RetryPolicy ALWAYS = new RetryPolicy(Integer.MAX_VALUE);

    private final int maxRetries;

    private RetryPolicy(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public static RetryPolicy never() { return NEVER; }
    public static RetryPolicy always() { return ALWAYS; }
    /** 固定 N 次重试 */
    public static RetryPolicy fixed(int maxRetries) { return new RetryPolicy(maxRetries); }

    /** 是否应该重试 (attemptCount 从 0 开始: 第 1 次重试=0, 第 2 次=1, ...) */
    public boolean shouldRetry(int attemptCount) {
        return attemptCount < maxRetries;
    }

    public int maxRetries() { return maxRetries; }
}

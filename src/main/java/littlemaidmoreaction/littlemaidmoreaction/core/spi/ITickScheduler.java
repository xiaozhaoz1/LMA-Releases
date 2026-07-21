package littlemaidmoreaction.littlemaidmoreaction.core.spi;

/**
 * Tick-based task scheduler interface.
 * Zero Minecraft dependency — uses only JDK types.
 * Implementation lives in adapter/ layer (MC-dependent).
 */
public interface ITickScheduler {
    /**
     * Schedule a callback to run after {@code delayTicks} server ticks.
     * @param delayTicks  ticks to wait before executing
     * @param callback    the action to run (captures MC objects in lambda)
     * @param maidId      maid entity ID for cancellation/grouping
     */
    void schedule(int delayTicks, Runnable callback, int maidId);

    /**
     * Cancel all pending tasks for a given maid.
     * @param maidId  maid entity ID
     */
    void cancel(int maidId);
}

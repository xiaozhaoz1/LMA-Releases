package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * 多 tick 状态机抽象基类。
 *
 * <p>子类实现 {@link #tick()}，框架自动管理 PersistentData 中的 phase/tick/心跳。
 * 使用方式：每 tick 调用 {@code new SubClass(maid, world).run()}.
 *
 * <pre>
 * class MyExecute extends BaseStateMachine {
 *     public MyExecute(EntityMaid m, ServerLevel w) { super(m, w, "lma_my_phase"); }
 *     protected boolean tick() {
 *         return switch (getPhase()) {
 *             case "A" -&gt; { if (hasElapsed(100)) setPhase("B"); yield true; }
 *             case "B" -&gt; { doWork(); setPhase(""); yield true; }  // empty = done
 *             default -&gt; { setPhase("A"); yield true; }  // init
 *         };
 *     }
 * }
 * </pre>
 */
public abstract class BaseStateMachine {
    protected final EntityMaid maid;
    protected final ServerLevel world;
    private final String stateKey;
    private final String tickKey;

    protected BaseStateMachine(EntityMaid maid, ServerLevel world, String stateKey) {
        this.maid = maid;
        this.world = world;
        this.stateKey = stateKey;
        this.tickKey = stateKey + "_tick";
    }

    /** 执行一次状态机 tick。首次调用自动初始化。 */
    public final void run() {
        heartbeat();
        boolean meaningful = tick();
        if (!meaningful) return;
        // 调用方可检查 phase 是否为空来判断完成
    }

    /** 当前阶段名，首次为空则自动设 INIT */
    protected String getPhase() {
        String p = pd().getString(stateKey);
        return p.isEmpty() ? "" : p;
    }
    /** 切换到新阶段（同时记录时间戳） */
    protected void setPhase(String phase) {
        pd().putString(stateKey, phase);
        pd().putLong(tickKey, world.getGameTime());
    }
    /** 当前阶段的开始 game tick */
    protected long getPhaseTick() { return pd().getLong(tickKey); }
    /** 当前阶段是否已过 >= ticks 个 tick */
    protected boolean hasElapsed(int ticks) {
        return world.getGameTime() - getPhaseTick() >= ticks;
    }
    /** 心跳 — 防止 TaskEngine 超时 */
    protected void heartbeat() {
        pd().putLong("lma_flow_tick", world.getGameTime());
    }

    private CompoundTag pd() { return maid.getPersistentData(); }

    /** 子类实现 — 返回 true 表示执行了有意义操作 */
    protected abstract boolean tick();
}

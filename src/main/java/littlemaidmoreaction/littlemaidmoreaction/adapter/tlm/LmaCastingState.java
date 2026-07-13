package littlemaidmoreaction.littlemaidmoreaction.adapter.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * LMA 的 IMagicCastingState 简单实现 — 可变状态持有者。
 */
@OnlyIn(Dist.CLIENT)
final class LmaCastingState implements IMagicCastingState {
    private CastingPhase phase;
    private boolean cancelled;

    LmaCastingState(CastingPhase phase) { this.phase = phase; }

    void setPhase(CastingPhase p) { this.phase = p; }

    @Override public CastingPhase getCurrentPhase() { return phase; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean v) { cancelled = v; }
}

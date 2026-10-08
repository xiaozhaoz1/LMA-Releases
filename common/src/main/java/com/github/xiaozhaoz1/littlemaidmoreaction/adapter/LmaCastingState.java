package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
//?} else {
import net.neoforged.api.distmarker.Dist;
//?}
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.OnlyIn;
//?}

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

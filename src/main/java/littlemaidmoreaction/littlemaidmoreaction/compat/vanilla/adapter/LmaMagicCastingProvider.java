package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingAnimationProvider;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState.CastingPhase;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType.EDefaultLoopTypes;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * LMA 动画提供器 — 接入 TLM magic_casting 控制器。
 *
 * <p>INSTANT 模式：返回 CastingPhase.INSTANT，TLM 播放一次后自动过渡到 NONE。
 * FULL 模式：tick 计数驱动 START→CASTING→END→NONE 四阶段自动切换。
 * 每阶段默认 20 tick (1秒)，从 animationsetup/ 读取动画元数据。
 *
 * <p>TLM 每帧调用 getMagicCastingState() + getAnimationBuilder()，
 * phase 改变时自动播放新动画（源码: AnimationManager.java L361-421）。
 *
 * <p><b>线程安全</b>：所有状态按女仆实体 ID 隔离存储于 {@link #maidStates}，
 * 避免单例 Provider 被多个女仆共享时状态互相覆盖。
 */
@OnlyIn(Dist.CLIENT)
public final class LmaMagicCastingProvider implements IMagicCastingAnimationProvider {

    // ── 按女仆隔离的内部状态 (key = entityId) ──
    private final Map<Integer, MaidAnimState> maidStates = new HashMap<>();
    private final java.util.Set<Integer> seenMaids = new java.util.HashSet<>();

    /**
     * 单个女仆的动画追踪状态。
     *
     * <p>每个女仆拥有独立的 seq 计数器、FULL 阶段状态和 IMagicCastingState 实例，
     * 避免单例 Provider 在多个女仆同时请求时状态互相覆盖。
     */
    private static final class MaidAnimState {
        final LmaCastingState castingState = new LmaCastingState(CastingPhase.NONE);
        CastingPhase fullPhase = CastingPhase.NONE;
        int phaseTicks = 0;
        int phaseDuration = 20;
        int lastAnimSeq = -1;
    }

    @Override
    public IMagicCastingState getMagicCastingState(IMaid maid) {
        int maidId = maid.asEntity().getId();
        var data = maid.asEntity().getPersistentData();
        // ★ 仅首次渲染此女仆时记录 — 确认 TLM 调用了 Provider
        if (seenMaids.add(maidId)) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Provider] FIRST CALL maid={} mode=[{}]", maidId, data.getString("lma_anim_mode"));
        }
        String mode = data.getString("lma_anim_mode");
        if (mode.isEmpty()) {
            if (maidStates.containsKey(maidId)) {
                maidStates.remove(maidId);
            }
            return null;
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/Provider] getState CALLED maid={} mode={} seq={}", maidId, mode, data.getInt("lma_anim_seq"));

        MaidAnimState ms = maidStates.computeIfAbsent(maidId, k -> new MaidAnimState());

        if ("INSTANT".equals(mode)) {
            String anim = data.getString("lma_anim");
            if (anim.isEmpty()) return null;

            // ★ 序列号检测 — 避免动画死循环
            // 服务器每次写新的动画请求时递增 lmma_anim_seq
            // Provider 只在序列号变化时返回 INSTANT（仅1帧）
            int seq = data.getInt("lma_anim_seq");
            if (seq == ms.lastAnimSeq) {
                // 已处理过此请求。getAnimationBuilder 已在上一帧调用，
                // 动画已提交到 TLM 控制器，此时可以安全清理 PersistentData。
                // 不清理则 RuleEngine 认为动画仍在播放，永久阻塞后续事件。
                cleanup(maid);
                maidStates.remove(maidId);
                return null;
            }
            ms.lastAnimSeq = seq;  // 标记已处理

            ms.castingState.setPhase(CastingPhase.INSTANT);
            ms.castingState.setCancelled(false);
            return ms.castingState;
        }

        // FULL 模式 — tick 驱动阶段切换
        if ("FULL".equals(mode)) {
            // ★ 序列号检测 — 同 INSTANT，防止 FULL 完成后重播
            //    FULL 的 cleanup() 在客户端执行，PersistentData 中 mode 仍为 "FULL"
            //    不用 seq 则每帧重入 START 阶段
            int seq = data.getInt("lma_anim_seq");
            if (seq == ms.lastAnimSeq) {
                // 已处理过或已完成 — 不重新启动
                if (ms.fullPhase == CastingPhase.NONE) return null;
            } else {
                // 新请求 — 重置状态
                ms.lastAnimSeq = seq;
                ms.fullPhase = CastingPhase.NONE;
                ms.phaseTicks = 0;
            }

            // 初始化：从 PersistentData 读取 phase + 时长
            if (ms.fullPhase == CastingPhase.NONE) {
                String dataPhase = data.getString("lma_anim_phase");
                ms.fullPhase = parsePhase(dataPhase);
                ms.phaseDuration = readPhaseDuration(data, ms.fullPhase);  // 读用户配置的时长
                ms.phaseTicks = 0;
            }

            ms.phaseTicks++;

            // 阶段切换
            if (ms.phaseTicks > ms.phaseDuration) {
                ms.fullPhase = nextPhase(ms.fullPhase);
                ms.phaseTicks = 0;
                ms.phaseDuration = readPhaseDuration(data, ms.fullPhase);  // 下一阶段时长

                if (ms.fullPhase == CastingPhase.NONE) {
                    cleanup(maid);
                    maidStates.remove(maidId);
                    return null;
                }
            }

            // 锁定移动
            if (data.getBoolean("lma_lock_move")) {
                maid.asEntity().getNavigation().stop();
            }

            ms.castingState.setPhase(ms.fullPhase);
            ms.castingState.setCancelled(false);
            return ms.castingState;
        }

        return null;
    }

    /** 阶段推进: START → CASTING → END → NONE */
    private static CastingPhase nextPhase(CastingPhase p) {
        return switch (p) {
            case START -> CastingPhase.CASTING;
            case CASTING -> CastingPhase.END;
            case END -> CastingPhase.NONE;
            default -> CastingPhase.NONE;
        };
    }

    @Override
    public AnimationBuilder getAnimationBuilder(IMaid maid, IMagicCastingState s) {
        int maidId = maid.asEntity().getId();
        LittleMaidMoreAction.LOGGER.info("[LMA/Provider] getAnimationBuilder CALLED maid={} phase={}", maidId, s != null ? s.getCurrentPhase() : "null");
        var data = maid.asEntity().getPersistentData();
        String mode = data.getString("lma_anim_mode");
        String animName;

        if ("INSTANT".equals(mode)) {
            animName = data.getString("lma_anim");
        } else if ("FULL".equals(mode)) {
            MaidAnimState ms = maidStates.get(maidId);
            if (ms == null) return null;
            // 根据当前阶段选动画名
            animName = switch (ms.fullPhase) {
                case START -> data.getString("lma_anim_start");
                case CASTING -> data.getString("lma_anim_casting");
                case END -> data.getString("lma_anim_end");
                default -> "";
            };
        } else {
            return null;
        }

        if (animName.isEmpty()) return null;

        // TLM wiki: CASTING → LOOP, 其余阶段 → PLAY_ONCE
        EDefaultLoopTypes loopType = EDefaultLoopTypes.PLAY_ONCE;
        if ("FULL".equals(mode)) {
            MaidAnimState ms = maidStates.get(maidId);
            if (ms != null && ms.fullPhase == CastingPhase.CASTING) {
                loopType = EDefaultLoopTypes.LOOP;
            }
        }
        return new AnimationBuilder()
                .addAnimation(animName, loopType);
    }

    @Override public int getPriority() { return 100; }

    // ── 工具方法 ──

    /** 清理 PersistentData 中的动画请求 */
    private static void cleanup(IMaid maid) {
        var data = maid.asEntity().getPersistentData();
        data.remove("lma_anim");
        data.remove("lma_anim_mode");
        data.remove("lma_anim_phase");
        data.remove("lma_anim_start");
        data.remove("lma_anim_casting");
        data.remove("lma_anim_end");
        data.remove("lma_anim_priority");
        data.remove("lma_lock_move");
        data.remove("lma_anim_tick");
        data.remove("lma_dur_start");
        data.remove("lma_dur_casting");
        data.remove("lma_dur_end");
        data.remove("lma_anim_dur");
        // 注意：不删除 lmma_anim_seq — 保留用于新请求的对比
    }

    /** 从 PersistentData 读取指定阶段的时长 (tick)，缺省 20 */
    private static int readPhaseDuration(net.minecraft.nbt.CompoundTag data, CastingPhase phase) {
        String key = switch (phase) {
            case START   -> "lma_dur_start";
            case CASTING -> "lma_dur_casting";
            case END     -> "lma_dur_end";
            default      -> "";
        };
        if (key.isEmpty()) return 20;
        int v = data.getInt(key);
        return v > 0 ? v : 20;  // 最小 1 tick
    }

    private static CastingPhase parsePhase(String s) {
        try { return CastingPhase.valueOf(s); }
        catch (Exception e) { return CastingPhase.START; }
    }
}

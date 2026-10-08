package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.DataKey;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingAnimationProvider;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState.CastingPhase;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType.EDefaultLoopTypes;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
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
 *
 * <p><b>⚠ 接口契约 (错题 #366, 崩溃修复)</b>：{@link IMaid} <b>不是</b> {@link EntityMaid} ✗ ——
 * 该接口被**其它模组的实体**也实现（如 `touhou_little_maid_spell` 的 `MagicalWinefoxBossEntity` 星之魔女 Boss），
 * 因为 Spell 的 Boss 也走 TLM 动画系统 ⇒ TLM 渲染它时会把**它**传进本 Provider ✗。
 * ⇒ 凡是形参类型为 {@code IMaid} 的入口，**必须先做类型守卫** ✓：
 * <pre>{@code
 * if (!(maid.asEntity() instanceof EntityMaid entityMaid)) return null;   // 非女仆 ⇒ 交回 TLM 默认逻辑
 * }</pre>
 * 之后**全程使用 entityMaid**、**禁止**再写 {@code (EntityMaid) maid.asEntity()} ✗
 * （原实现在日志行/各分支里共 11 处强转 ⇒ Boss 触发 ClassCastException 崩溃 ✗）。
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
        // ★ 类型守卫 (错题 #366): IMaid 可能是别的模组的实体 (如 Spell 的星之魔女 Boss) ⇒ 不能当女仆用 ✗
        if (!(maid.asEntity() instanceof EntityMaid entityMaid)) {
            return null;   // 非女仆实体 ⇒ 返回 null, 交回 TLM 默认逻辑 ✓ (绝不强转 ✗)
        }
        int maidId = entityMaid.getId();
        var data = entityMaid.getPersistentData();
        // ★ 仅首次渲染此女仆时记录 — 确认 TLM 调用了 Provider
        if (seenMaids.add(maidId)) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Provider] FIRST CALL maid={} mode=[{}]", maidId, MaidData.get(entityMaid, DataKey.ANIM_MODE));
        }
        String mode = MaidData.get(entityMaid, DataKey.ANIM_MODE);
        if (mode.isEmpty()) {
            if (maidStates.containsKey(maidId)) {
                maidStates.remove(maidId);
            }
            return null;
        }
        // 卡顿修复: TLM 每帧调 getMagicCastingState — INFO 日志 = 每帧刷屏日志风暴 (用户实测 419 行/5 秒)
        LittleMaidMoreAction.LOGGER.debug("[LMA/Provider] getState CALLED maid={} mode={} seq={}", maidId, mode, MaidData.get(entityMaid, DataKey.ANIM_SEQ));

        MaidAnimState ms = maidStates.computeIfAbsent(maidId, k -> new MaidAnimState());

        if ("INSTANT".equals(mode)) {
            String anim = MaidData.get(entityMaid, DataKey.ANIM_NAME);
            if (anim.isEmpty()) return null;

            // ★ 序列号检测 — 避免动画死循环
            // 服务器每次写新的动画请求时递增 lmma_anim_seq
            // Provider 只在序列号变化时返回 INSTANT（仅1帧）
            int seq = MaidData.get(entityMaid, DataKey.ANIM_SEQ);
            if (seq == ms.lastAnimSeq) {
                // 已处理过此请求。getAnimationBuilder 已在上一帧调用，
                // 动画已提交到 TLM 控制器，此时可以安全清理 PersistentData。
                // 不清理则动画 PersistentData 残留 (原 RuleEngine 动画护卫已删, 仍按惯例清理)。
                cleanup(entityMaid);
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
            int seq = MaidData.get(entityMaid, DataKey.ANIM_SEQ);
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
                String dataPhase = MaidData.get(entityMaid, DataKey.ANIM_PHASE);
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
                    cleanup(entityMaid);
                    maidStates.remove(maidId);
                    return null;
                }
            }

            // 锁定移动
            if (MaidData.get(entityMaid, DataKey.LOCK_MOVE)) {
                entityMaid.getNavigation().stop();
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
        // ★ 类型守卫 (错题 #366): 同上 — 非女仆实体直接返回 null ✓ (不查任何 MaidData ✗)
        if (!(maid.asEntity() instanceof EntityMaid entityMaid)) {
            return null;
        }
        int maidId = entityMaid.getId();
        // 卡顿修复: 同 getState — 每帧调用, DEBUG 级
        LittleMaidMoreAction.LOGGER.debug("[LMA/Provider] getAnimationBuilder CALLED maid={} phase={}", maidId, s != null ? s.getCurrentPhase() : "null");
        var data = entityMaid.getPersistentData();
        String mode = MaidData.get(entityMaid, DataKey.ANIM_MODE);
        String animName;

        if ("INSTANT".equals(mode)) {
            animName = MaidData.get(entityMaid, DataKey.ANIM_NAME);
        } else if ("FULL".equals(mode)) {
            MaidAnimState ms = maidStates.get(maidId);
            if (ms == null) return null;
            // 根据当前阶段选动画名
            animName = switch (ms.fullPhase) {
                case START -> MaidData.get(entityMaid, DataKey.ANIM_START);
                case CASTING -> MaidData.get(entityMaid, DataKey.ANIM_CASTING);
                case END -> MaidData.get(entityMaid, DataKey.ANIM_END);
                default -> "";
            };
        } else {
            return null;
        }

        if (animName.isEmpty()) return null;

        // TLM wiki: CASTING → LOOP, 其余阶段 → PLAY_ONCE
        // ⚠ INSTANT 不能用 LOOP — INSTANT→NONE 是"播完为止", LOOP 永播不完 = 停不了 (实测, 哈气已改 FULL)
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

    /**
     * 清理 PersistentData 中的动画请求。
     *
     * <p>⚠ 形参保持 {@link IMaid} 是**安全**的 ✓ —— 本方法只调用 {@code asEntity().getPersistentData()}，
     * 而 {@code getPersistentData()} 是 {@code Entity} 上的方法 ✓ (任何实现 IMaid 的实体都有 ✓)
     * ⇒ 无需类型守卫 ✓ (调用方也都已守卫 ✓)。
     */
    private static void cleanup(IMaid maid) {
        var data = maid.asEntity().getPersistentData();
        // 单一来源 = TaskKeys.ANIM_CLEANUP_KEYS (ANIM_RUNTIME_KEYS 去 ANIM_SEQ)
        for (String key : TaskKeys.ANIM_CLEANUP_KEYS) {
            data.remove(key);
        }
        // 注意：不删除 lma_anim_seq — 保留用于新请求的对比 (刻意例外, 见 TaskKeys.ANIM_CLEANUP_KEYS)
    }

    /** 从 PersistentData 读取指定阶段的时长 (tick)，缺省 20 */
    private static int readPhaseDuration(net.minecraft.nbt.CompoundTag data, CastingPhase phase) {
        String key = switch (phase) {
            case START   -> TaskKeys.DUR_START;
            case CASTING -> TaskKeys.DUR_CASTING;
            case END     -> TaskKeys.DUR_END;
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

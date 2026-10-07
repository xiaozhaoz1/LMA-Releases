package com.github.xiaozhaoz1.littlemaidmoreaction.api.output;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * YSM 动画输出门面 (2026-09-21 接口倒置 — 清 {@code task → compat} 反向依赖)。
 *
 * <p>背景: {@code task/pipeline/sense/HaqiPipeline} (stopRoulette) 与 {@code task/service/AnimExecute}
 * (playRoulette ×2) 原直接调 {@code compat.ysm.YsmOutput} ⇒ 核心层反向依赖兼容层 (3 处越层)。
 * 现改由实现方 (compat) 在启动期注入, 核心层只调本门面。
 *
 * <p><b>语义</b> (与实现方逐字一致): 未装 YSM 时 TLM {@code isYsmModel()} 恒 false
 * ⇒ 两方法都是 **no-op**; 实现方另有 {@code ModList} 门控做短路 (省调用, 非语义变化)。
 *
 * <p>注入: {@code CompatRegistry.scanAllCompatEarly()} (mod 构造期) 调 {@link #install};
 * 未注入时本门面为 **静默 no-op** (兼容层缺席属正常状态, 不是错误)。
 */
public final class YsmAnimationProvider {

    /** 实现方注入的供应器 — 启动期写入 (见 {@link #install}); 运行期只读 */
    private static volatile Impl impl;

    private YsmAnimationProvider() {}

    /** 由 compat 实现方 (YsmOutput) 在启动期注入; 幂等 */
    public static void install(Impl implementation) {
        if (implementation != null) impl = implementation;
    }

    /** 播放轮盘动画 (实现方责任: 仅 YSM 模型时生效) */
    public static void playRoulette(EntityMaid maid, String animName) {
        Impl p = impl;
        if (p != null) p.play(maid, animName);
    }

    /** 停止轮盘动画 (实现方责任: 仅 YSM 模型时生效) */
    public static void stopRoulette(EntityMaid maid) {
        Impl p = impl;
        if (p != null) p.stop(maid);
    }

    /** 实现接口 — 由 {@code compat.ysm.YsmOutput} 实现 (其静态壳保留以兼容既有调用方) */
    public interface Impl {
        void play(EntityMaid maid, String animName);

        void stop(EntityMaid maid);
    }
}

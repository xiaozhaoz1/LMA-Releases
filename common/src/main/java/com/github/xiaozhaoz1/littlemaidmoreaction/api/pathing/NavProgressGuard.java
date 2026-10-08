package com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 导航进展守护 (v79.61x) — 心跳证明"活着"不证明"有进展"。
 *
 * <p>问题: GMPM 看门狗 (1200t) 只兜「心跳断流」(tick 停摆), 导航卡死时
 * (目标有效但 TLM 走不到 — 墙后/悬崖/被围) 心跳每 20t 续命 → 永久挂起。
 * 本类补「进展维度」: 目标未变 + 位移 &lt; 阈值持续超过窗口 → 判定 nav_stuck,
 * 由调用方 (Brain behavior / FSM 状态机) 走 RecoveryLadder 兜底阶梯。
 *
 * <p>状态存 {@code MaidData.pl(maid, "navguard")} 内存态 (零 NBT, 随心跳 flush;
 * 任务终结残留自愈 — 下次 track 目标不同即重置, 无消费方误读风险)。
 * 纯函数核心 {@link #shouldFlagStuck} JVM 可测 (错题 #200 纯块抽取)。
 *
 * <p>挂接: {@link com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaFlowCoordinationBehavior}
 * (TLM Brain behavior — 工作站族 TARGET_POS 路径) + ArmTransferPipeline
 * TO_TAKE/TO_DEPOSIT (FSM WALK_TARGET 路径)。PathingApi.navigate 路径已有
 * CHAIN_NAV_TIMEOUT 240t 兜底, 不重复挂接。
 */
public final class NavProgressGuard {

    /** 卡死判定窗口 (tick) — 正常导航远快于此, 误报窗口极小 */
    public static final long WINDOW_TICKS = 100;
    /** 位移阈值² (格) — 0.5 格内视为"无进展" (含蓄力原地小位移的保守带) */
    public static final double PROGRESS_THRESHOLD_SQ = 0.25;

    /** 状态键前缀 (MaidData.pl 独立域, 非任务 pipelineData) */
    private static final String PL_KEY = "navguard";
    private static final String KEY_TARGET = "t";   // 目标 CSV (BlockPos.toShortString)
    private static final String KEY_LAST_POS = "l"; // 上次记录位置 CSV
    private static final String KEY_LAST_PROGRESS = "p"; // 最后进展 tick

    private NavProgressGuard() {}

    /**
     * 每 tick 调用 (导航中) — 目标变化 → 重置记录; 目标未变 → 按位移更新最后进展。
     *
     * @param target 当前导航目标 (TARGET_POS / WALK_TARGET 位置); null = 不跟踪
     */
    public static void track(EntityMaid maid, BlockPos target, long now) {
        if (target == null) return;
        CompoundTag tag = pl(maid);
        String tKey = target.toShortString();
        String cur = maid.blockPosition().toShortString();
        if (!tKey.equals(tag.getString(KEY_TARGET))) {
            // 目标切换 → 重置记录 (重搜/换目标即重新计时)
            tag.putString(KEY_TARGET, tKey);
            tag.putLong(KEY_LAST_PROGRESS, now);
            tag.putString(KEY_LAST_POS, cur);
            return;
        }
        BlockPos last = parsePos(tag.getString(KEY_LAST_POS));
        if (last == null || !tag.contains(KEY_LAST_PROGRESS)) {
            tag.putLong(KEY_LAST_PROGRESS, now);
            tag.putString(KEY_LAST_POS, cur);
            return;
        }
        if (hasProgress(last, maid.blockPosition(), PROGRESS_THRESHOLD_SQ)) {
            tag.putLong(KEY_LAST_PROGRESS, now); // 有进展 → 刷新
        }
        tag.putString(KEY_LAST_POS, cur); // 记录当前位置 (防缓慢漂移累计)
    }

    /** 卡死判定 — 距最后进展超过窗口 (含时钟回绕守卫) */
    public static boolean isStuck(EntityMaid maid, long now) {
        return shouldFlagStuck(pl(maid).getLong(KEY_LAST_PROGRESS), now, WINDOW_TICKS);
    }

    /** 重置 — 调用方在卡死处理 (重搜/重试) 后调用, 重新计时 */
    public static void reset(EntityMaid maid) {
        pl(maid).remove(KEY_LAST_PROGRESS);
    }

    /** 终结清理 — 任务 onCleanup 调用 (防跨任务残留) */
    public static void clear(EntityMaid maid) {
        MaidData.removePl(maid, PL_KEY);
    }

    /** 纯函数 — 卡死判定: 有记录 && 未回绕 && 距最后进展 &gt; 窗口 */
    static boolean shouldFlagStuck(long lastProgressTick, long now, long windowTicks) {
        return lastProgressTick != 0 && lastProgressTick <= now
                && now - lastProgressTick > windowTicks;
    }

    /** 纯函数 — 位移是否构成进展 (≥ 阈值; BlockPos 纯数据无注册表链, JVM 可测) */
    static boolean hasProgress(BlockPos last, BlockPos cur, double thresholdSq) {
        return last.distSqr(cur) >= thresholdSq;
    }

    private static CompoundTag pl(EntityMaid maid) {
        return MaidData.pl(maid, PL_KEY);
    }

    /** CSV 坐标解析 (与 BlockTargetNavigation.parseTarget 同款) — 空/坏数据 → null */
    static BlockPos parsePos(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            return new BlockPos(Integer.parseInt(p[0].trim()),
                    Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return null;
        }
    }
}

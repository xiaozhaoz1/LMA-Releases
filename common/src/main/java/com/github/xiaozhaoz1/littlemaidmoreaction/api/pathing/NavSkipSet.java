package com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 导航跳过集静态门面 (v79.61x) — 卡死目标临时跳过, 对齐 ChainHarvest SKIP_TTL 语义。
 *
 * <p>从 {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BlockTargetNavigation}
 * default 抽出的通用能力 (CannonLoad 等非 BlockTargetNavigation 管线复用)。
 * 状态存 {@code MaidData.pl(maid, plKey)} 两对齐 longArray:
 * {@link #KEY_SKIP_POS} (BlockPos.asLong) + {@link #KEY_SKIP_AT} (加入 tick)。
 * 任务终结随 pl 自动清理 (TaskConfigurable → clearPipelineData)。
 */
public final class NavSkipSet {

    /** 跳过集目标位置键 (longArray) */
    public static final String KEY_SKIP_POS = "skip_pos";
    /** 跳过集加入时间戳键 (longArray, 与 KEY_SKIP_POS 对齐) */
    public static final String KEY_SKIP_AT = "skip_t";
    /** 跳过有效期 (tick) — 对齐 ChainHarvest SKIP_TTL=60 */
    public static final long SKIP_TTL = 60;
    /** 跳过集容量上限 — 淘汰最旧 */
    public static final int SKIP_MAX = 8;

    private NavSkipSet() {}

    /** 目标是否在跳过集内 (有效期内) */
    public static boolean isSkipped(EntityMaid maid, String plKey, BlockPos pos, long now) {
        var tag = pl(maid, plKey);
        long[] posArr = tag.getLongArray(KEY_SKIP_POS);
        long[] atArr = tag.getLongArray(KEY_SKIP_AT);
        if (posArr == null || atArr == null || posArr.length != atArr.length) return false;
        for (int i = 0; i < posArr.length; i++) {
            if (posArr[i] == pos.asLong() && atArr[i] != 0 && atArr[i] <= now
                    && now - atArr[i] < SKIP_TTL) {
                return true;
            }
        }
        return false;
    }

    /** 目标加入跳过集 (去重 + 过期清理 + 容量淘汰) */
    public static void addSkip(EntityMaid maid, String plKey, BlockPos pos, long now) {
        var tag = pl(maid, plKey);
        long[] posArr = tag.getLongArray(KEY_SKIP_POS);
        long[] atArr = tag.getLongArray(KEY_SKIP_AT);
        if (posArr == null || atArr == null || posArr.length != atArr.length) {
            posArr = new long[0];
            atArr = new long[0];
        }
        java.util.List<Long> posList = new java.util.ArrayList<>();
        java.util.List<Long> atList = new java.util.ArrayList<>();
        for (int i = 0; i < posArr.length; i++) {
            // 保留: 未过期 + 非重复目标
            if (atArr[i] != 0 && atArr[i] <= now && now - atArr[i] < SKIP_TTL
                    && posArr[i] != pos.asLong()) {
                posList.add(posArr[i]);
                atList.add(atArr[i]);
            }
        }
        posList.add(pos.asLong());
        atList.add(now);
        while (posList.size() > SKIP_MAX) { posList.remove(0); atList.remove(0); } // 淘汰最旧
        tag.putLongArray(KEY_SKIP_POS, posList.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray(KEY_SKIP_AT, atList.stream().mapToLong(Long::longValue).toArray());
    }

    private static CompoundTag pl(EntityMaid maid, String plKey) {
        return MaidData.pl(maid, plKey);
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import javax.annotation.Nullable;

/**
 * NBT 编解码工具 (v79.5) — 双平台 BlockPos↔NBT 样板收敛。
 *
 * <p>格式兼容: 各平台保留自身存储格式 (1.20.1 = NbtUtils x/y/z 三元组,
 * 1.21.1 = "pos" long) — 现有数据零迁移, 只是样板单点化。
 */
public final class NbtCodecs {

    private NbtCodecs() {}

    /** 读 BlockPos (缺键/损坏 → null) — 双平台格式兼容 */
    @Nullable
    public static BlockPos readBlockPos(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        CompoundTag sub = tag.getCompound(key);
//? if 1.20.1 {
        return NbtUtils.readBlockPos(sub);
//?} else {
        return sub.contains("pos") ? BlockPos.of(sub.getLong("pos")) : null;
//?}
    }

    /**
     * 写 BlockPos — 各平台自身格式 (与读对称, 错题 #183)。
     * 1.20.1: tag[key] = NbtUtils 三元组 {x,y,z} (读侧 readBlockPos 同款解析);
     * 1.21.1: tag[key] = {"pos": long}。原 1.20.1 分支包 "pos" 子键与读侧不对称 —
     * 死代码掩盖 (0 调用方), v79.55 修对称 + 接入 2 写侧调用方。
     */
    public static void writeBlockPos(CompoundTag tag, String key, BlockPos pos) {
//? if 1.20.1 {
        tag.put(key, NbtUtils.writeBlockPos(pos));
//?} else {
        CompoundTag sub = new CompoundTag();
        sub.putLong("pos", pos.asLong());
        tag.put(key, sub);
//?}
    }

    /** 读坐标键 (pipelineData/persistentData 顶层 long 键 — 1.21 "pos" 同格式) */
    public static long readPosLong(CompoundTag tag, String key) {
        return tag.getLong(key);
    }
}

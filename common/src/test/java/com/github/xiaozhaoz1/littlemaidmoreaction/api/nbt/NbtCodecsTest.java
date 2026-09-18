package com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link NbtCodecs} 标记编解码**对称性**守卫 (v79.64.2 用户实测「绑定后方块信息丢失」的直接教训)。
 *
 * <p><b>为什么必须锁</b>: 木棍标记路径原**绕过**本编解码器, 用裸
 * {@code tag.put(MARK1, NbtUtils.writeBlockPos(pos))} 与 {@code NbtUtils.readBlockPos(tag, key)} 配对。
 * 1.21.1 下写侧返回 IntArrayTag、读侧空 ⇒ 绑定坐标被 ⚠{@code .orElse(maid.blockPosition())} 兜底成
 * **女仆自己脚下的格子** ⇒ 下一 tick 该格不是目标方块 ⇒ 走"方块被破坏"分支把绑定清掉 ✗
 * (实测: 标记 -3092,63,2269 → 却绑成 -3091,63,2270)。
 *
 * <p>本测锁死"**写进去必然读得出、且逐字段相等**"这条契约 — 任何一侧改动破坏对称即红 ✓
 * (纯 JVM: CompoundTag/BlockPos 均为纯数据类, 无 MC 注册表依赖)。
 */
class NbtCodecsTest {

    @Test
    @DisplayName("writeBlockPos → readBlockPos 往返: 坐标逐字段相等 (含负坐标/极值)")
    void roundTrip() {
        CompoundTag tag = new CompoundTag();
        BlockPos pos = new BlockPos(-3092, 63, 2269);
        NbtCodecs.writeBlockPos(tag, "m", pos);
        BlockPos back = NbtCodecs.readBlockPos(tag, "m");
        assertNotNull(back, "写进去必须读得出 (本次 bug 的契约面)");
        assertEquals(pos, back, "往返必须逐字段相等");

        // 负值/大值边界 (MC 坐标范围可负)
        CompoundTag t2 = new CompoundTag();
        BlockPos far = new BlockPos(-30000000, -64, 29999999);
        NbtCodecs.writeBlockPos(t2, "m", far);
        assertEquals(far, NbtCodecs.readBlockPos(t2, "m"));
    }

    @Test
    @DisplayName("读缺键/坏数据 → null (不抛); 不伪造坐标 (防静默绑到别处)")
    void missingKeyReturnsNull() {
        CompoundTag tag = new CompoundTag();
        assertNull(NbtCodecs.readBlockPos(tag, "nope"), "缺键必须 null (调用方据此报错, 不得兜底)");
        tag.putIntArray("bad", new int[]{1, 2});   // 长度 != 3
        assertNull(NbtCodecs.readBlockPos(tag, "bad"), "坏数据必须 null");
    }

    @Test
    @DisplayName("同一个 tag 上多键互不干扰 (MARK1 / STICK_KEY 兼容读场景)")
    void multipleKeys() {
        CompoundTag tag = new CompoundTag();
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(4, 5, 6);
        NbtCodecs.writeBlockPos(tag, "MARK1", a);
        NbtCodecs.writeBlockPos(tag, "STICK", b);
        assertEquals(a, NbtCodecs.readBlockPos(tag, "MARK1"));
        assertEquals(b, NbtCodecs.readBlockPos(tag, "STICK"));
    }
}

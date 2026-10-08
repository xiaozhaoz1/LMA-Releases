package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MaidData#defaultValue} 契约测试 (2026-08-15 数据层检查) —
 * DataKey.def 是缺键唯一真相, COMPOUND 缺省返回副本 (防共享默认被改脏)。
 */
public class MaidDataDefaultsTest {

    @Test
    @DisplayName("非默认 def 各类型兑现")
    void customDefaults_honored() {
        assertEquals(42, MaidData.defaultValue(new DataKey<>("t_int", DataKey.DataType.INT, 42)));
        assertEquals(7L, MaidData.defaultValue(new DataKey<>("t_long", DataKey.DataType.LONG, 7L)));
        assertEquals("hi", MaidData.defaultValue(new DataKey<>("t_str", DataKey.DataType.STRING, "hi")));
        assertTrue(MaidData.defaultValue(new DataKey<>("t_bool", DataKey.DataType.BOOLEAN, true)));
    }

    @Test
    @DisplayName("现有 DataKey 的 def 与 NBT 类型默认一致 (行为零变化)")
    void existingKeys_matchNbtDefaults() {
        assertEquals("", MaidData.defaultValue(DataKey.FLOW_TASK));
        assertEquals(0L, MaidData.defaultValue(DataKey.FLOW_COUNTER));
        assertEquals(false, MaidData.defaultValue(DataKey.AI_CONTROL));
        assertTrue(((CompoundTag) MaidData.defaultValue(DataKey.ARM_TAKE)).isEmpty());
    }

    @Test
    @DisplayName("COMPOUND 缺省返回副本 — 改结果不改共享默认")
    void compoundDefault_isCopy() {
        CompoundTag def = new CompoundTag();
        def.putString("k", "v");
        DataKey<CompoundTag> key = new DataKey<>("t_cmp", DataKey.DataType.COMPOUND, def);
        CompoundTag got1 = MaidData.defaultValue(key);
        CompoundTag got2 = MaidData.defaultValue(key);
        assertNotSame(got1, got2);
        assertNotSame(got1, def);
        got1.putString("k", "mutated");
        assertEquals("v", def.getString("k"));
        assertEquals("v", got2.getString("k"));
    }
}

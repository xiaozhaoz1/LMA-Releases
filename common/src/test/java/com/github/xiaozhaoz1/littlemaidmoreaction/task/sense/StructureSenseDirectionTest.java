package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构气泡 6 向方位 + 语气词模板纯函数测试 (v79.6x)。
 * 纯 JVM: relativeDir (参数注入 yaw) / dirKeyOf / tipComponent / joinComponents —
 * Component.translatable 仅构造组件树 (不触语言加载, 与 CompoundTag 同类安全, 错题 #174)。
 */
class StructureSenseDirectionTest {

    // MC yaw: 0=朝南(+Z), 90=朝西(-X), 180=朝北(-Z), 270=朝东(+X)

    @Test
    void 相对方位_朝南前后左右() {
        // 面向南 (yaw=0): 南=前, 北=后, 东=左, 西=右
        assertEquals(StructureSense.RelativeDir.FRONT, StructureSense.relativeDir(0, 10, 0, 0F));
        assertEquals(StructureSense.RelativeDir.BACK, StructureSense.relativeDir(0, -10, 0, 0F));
        assertEquals(StructureSense.RelativeDir.LEFT, StructureSense.relativeDir(10, 0, 0, 0F));
        assertEquals(StructureSense.RelativeDir.RIGHT, StructureSense.relativeDir(-10, 0, 0, 0F));
    }

    @Test
    void 相对方位_朝东时左转() {
        // 面向东 (yaw=270): 东=前, 西=后, 南=右, 北=左
        assertEquals(StructureSense.RelativeDir.FRONT, StructureSense.relativeDir(10, 0, 0, 270F));
        assertEquals(StructureSense.RelativeDir.BACK, StructureSense.relativeDir(-10, 0, 0, 270F));
        assertEquals(StructureSense.RelativeDir.RIGHT, StructureSense.relativeDir(0, 10, 0, 270F));
        assertEquals(StructureSense.RelativeDir.LEFT, StructureSense.relativeDir(0, -10, 0, 270F));
    }

    @Test
    void 相对方位_垂直主导上下() {
        // 垂直差 > 水平距离 → 上/下 (地下要塞/天空结构)
        assertEquals(StructureSense.RelativeDir.DOWN, StructureSense.relativeDir(3, 0, -30, 0F));
        assertEquals(StructureSense.RelativeDir.UP, StructureSense.relativeDir(3, 0, 30, 0F));
    }

    @Test
    void 方向键_六向映射() {
        assertEquals("structure_sense.dir.front", StructureSense.dirKeyOf(StructureSense.RelativeDir.FRONT));
        assertEquals("structure_sense.dir.down", StructureSense.dirKeyOf(StructureSense.RelativeDir.DOWN));
    }

    @Test
    void 语气组件_near单参数() {
        Component c = StructureSense.tipComponent("structure_sense.tip.near.0", null,
                "structure_sense.structure.village");
        assertNotNull(c);
        assertTrue(c.getContents() instanceof TranslatableContents tc
                && "structure_sense.tip.near.0".equals(tc.getKey())
                && tc.getArgs().length == 1);
    }

    @Test
    void 语气组件_far双参数() {
        Component c = StructureSense.tipComponent("structure_sense.tip.far.1", "structure_sense.dir.front",
                "structure_sense.structure.village");
        assertNotNull(c);
        assertTrue(c.getContents() instanceof TranslatableContents tc
                && "structure_sense.tip.far.1".equals(tc.getKey())
                && tc.getArgs().length == 2);
    }

    @Test
    void 模板池_远六近六进三() {
        assertEquals(6, StructureSense.TIP_FAR_KEYS.size());
        assertEquals(6, StructureSense.TIP_NEAR_KEYS.size());
        assertEquals(3, StructureSense.TIP_ENTER_KEYS.size());
    }

    @Test
    void 合并组件_顿号连接() {
        Component c1 = Component.translatable("structure_sense.structure.village");
        Component c2 = Component.translatable("structure_sense.structure.mineshaft");
        Component joined = StructureSense.joinComponents(List.of(c1, c2));
        assertNotNull(joined);
        assertNull(StructureSense.joinComponents(List.of()));
    }
}

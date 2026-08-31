package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MaidInventoryReader} 读取测试 (NEW-M9: 从 task/service/InventoryReaderTest 迁入,
 * 与主类包路径镜像 — 原文件名/包不镜像已修正)。
 *
 * <p><b>可测性边界</b>: readAll/readBackpack 强耦合 {@link EntityMaid}
 * (maid.getAvailableInv(true) 返回 TLM 物品栏) — 非纯 JVM 可构造, 仅 null 守卫可测;
 * 算法级验证 (槽位遍历/合并计数) 需 gametest 或 TLM 测试基建。
 */
class MaidInventoryReaderTest {

    @Test
    @DisplayName("null 女仆 → NPE (readAll)")
    void readAll_nullMaid_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> MaidInventoryReader.readAll(null));
    }

    @Test
    @DisplayName("null 女仆 → NPE (readBackpack)")
    void readBackpack_nullMaid_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> MaidInventoryReader.readBackpack(null));
    }
}

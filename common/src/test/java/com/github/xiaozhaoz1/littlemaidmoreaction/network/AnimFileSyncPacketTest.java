package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v79.18: AnimFileSyncPacket 文件名校验 — 纯 JVM 测试 (无 MC 依赖)。
 * 白名单后缀 + 路径遍历防护 (.. / \\ / : / 分隔符)。
 */
class AnimFileSyncPacketTest {

    @Test
    void 合法动画文件名放行() {
        assertTrue(AnimFileSyncPacket.isValidFileName("haqi.animation.json"));
        assertTrue(AnimFileSyncPacket.isValidFileName("my_anim.animation.json"));
        assertTrue(AnimFileSyncPacket.isValidFileName("Animation.ANIMATION.JSON"));
    }

    @Test
    void 非动画后缀拒绝() {
        assertFalse(AnimFileSyncPacket.isValidFileName("haqi.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("haqi.animation.txt"));
        assertFalse(AnimFileSyncPacket.isValidFileName("haqi"));
        assertFalse(AnimFileSyncPacket.isValidFileName("haqi.animation.json.exe"));
    }

    @Test
    void 路径遍历拒绝() {
        assertFalse(AnimFileSyncPacket.isValidFileName("../haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("..\\haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("a/b/haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("a\\b\\haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("C:/x/haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("x..y.animation.json"));
    }

    @Test
    void 空值与null拒绝() {
        assertFalse(AnimFileSyncPacket.isValidFileName(null));
        assertFalse(AnimFileSyncPacket.isValidFileName(""));
    }
}

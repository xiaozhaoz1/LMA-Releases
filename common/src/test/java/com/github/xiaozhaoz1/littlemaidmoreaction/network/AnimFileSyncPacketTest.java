package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v79.18: AnimFileSyncPacket 文件名校验 — 纯 JVM 测试 (无 MC 依赖)。
 * 白名单后缀 + 路径遍历防护 (.. / \\ / : / 分隔符)。
 */
class AnimFileSyncPacketTest {

    @Test
    @DisplayName("合法动画文件名放行 (.animation.json 后缀)")
    void 合法动画文件名放行() {
        assertTrue(AnimFileSyncPacket.isValidFileName("haqi.animation.json"));
        assertTrue(AnimFileSyncPacket.isValidFileName("my_anim.animation.json"));
        assertTrue(AnimFileSyncPacket.isValidFileName("Animation.ANIMATION.JSON"));
    }

    @Test
    @DisplayName("非动画后缀拒绝 (缺 .animation.json)")
    void 非动画后缀拒绝() {
        assertFalse(AnimFileSyncPacket.isValidFileName("haqi.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("haqi.animation.txt"));
        assertFalse(AnimFileSyncPacket.isValidFileName("haqi"));
        assertFalse(AnimFileSyncPacket.isValidFileName("haqi.animation.json.exe"));
    }

    @Test
    @DisplayName("路径遍历拒绝 (.. / \\ / : / 分隔符)")
    void 路径遍历拒绝() {
        assertFalse(AnimFileSyncPacket.isValidFileName("../haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("..\\haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("a/b/haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("a\\b\\haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("C:/x/haqi.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("x..y.animation.json"));
    }

    @Test
    @DisplayName("Windows 保留设备名拒绝 (CON/PRN/AUX/NUL/COM1-9/LPT1-9, 大小写不敏感)")
    void windows保留设备名拒绝() {
        assertFalse(AnimFileSyncPacket.isValidFileName("CON.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("con.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("PRN.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("nul.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("com1.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("COM9.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("LPT3.animation.json"));
        assertFalse(AnimFileSyncPacket.isValidFileName("com1.foo.animation.json"));
    }

    @Test
    @DisplayName("超长文件名拒绝 (>64)")
    void 超长文件名拒绝() {
        assertFalse(AnimFileSyncPacket.isValidFileName("x".repeat(65) + ".animation.json"));
        assertTrue(AnimFileSyncPacket.isValidFileName("x".repeat(64 - ".animation.json".length()) + ".animation.json"));
    }

    @Test
    @DisplayName("空值与 null 拒绝")
    void 空值与null拒绝() {
        assertFalse(AnimFileSyncPacket.isValidFileName(null));
        assertFalse(AnimFileSyncPacket.isValidFileName(""));
    }
}

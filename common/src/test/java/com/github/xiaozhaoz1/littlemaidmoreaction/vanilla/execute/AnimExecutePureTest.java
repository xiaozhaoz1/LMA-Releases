package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AnimExecute} 纯函数测试 (审计 T1): pickRandom CSV 语义 / parseInt / safeIncrementSeq。
 * 主类含 MC/TLM 依赖不做 mock (错题 #174 铁律) — 只测 package-private 纯函数。
 */
public class AnimExecutePureTest {

    @Test
    @DisplayName("单动画名 → 原样返回")
    void single_returnsTrimmed() {
        assertEquals("anim", AnimExecute.pickRandom(" anim "));
    }

    @Test
    @DisplayName("CSV → 随机命中合法集合 (多次抽样)")
    void csv_picksFromValidSet() {
        for (int i = 0; i < 50; i++) {
            String pick = AnimExecute.pickRandom("a, b, , c");
            assertTrue(pick.equals("a") || pick.equals("b") || pick.equals("c"), "非法选择: " + pick);
        }
    }

    @Test
    @DisplayName("空/全空 CSV → 空串")
    void empty_returnsEmpty() {
        assertEquals("", AnimExecute.pickRandom(null));
        assertEquals("", AnimExecute.pickRandom(""));
        assertEquals("", AnimExecute.pickRandom(" , , "));
    }

    @Test
    @DisplayName("parseInt: 正常/空串/非法 → 默认值")
    void parseInt_fallsBack() {
        assertEquals(20, AnimExecute.parseInt("20", 20));
        assertEquals(20, AnimExecute.parseInt("", 20));
        assertEquals(20, AnimExecute.parseInt("abc", 20));
        assertEquals(20, AnimExecute.parseInt(null, 20));
    }

    @Test
    @DisplayName("safeIncrementSeq: 溢出回绕 1")
    void seq_wrapsAtMax() {
        assertEquals(2, AnimExecute.safeIncrementSeq(1));
        assertEquals(1, AnimExecute.safeIncrementSeq(Integer.MAX_VALUE));
        assertEquals(1, AnimExecute.safeIncrementSeq(Integer.MAX_VALUE - 1));
    }
}

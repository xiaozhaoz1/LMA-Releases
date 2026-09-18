package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Signals} 纯 JVM 测试 (v79.49) — env: 信号 id 映射纯函数。
 */
class SignalsTest {

    @Test
    @DisplayName("envOf 往返: 枚举 → 信号 id 前缀 env: + 名称")
    void envof_roundtrip() {
        assertEquals("env:SNOWING", Signals.envOf(EnvSignal.SNOWING));
        assertEquals("env:FESTIVAL_ENTER", Signals.envOf(EnvSignal.FESTIVAL_ENTER));
    }

    @Test
    @DisplayName("parseEnv 往返: envOf → parseEnv 回原枚举")
    void parse_roundtrip() {
        for (EnvSignal s : EnvSignal.values()) {
            assertEquals(s, Signals.parseEnv(Signals.envOf(s)), "往返 " + s);
        }
    }

    @Test
    @DisplayName("parseEnv 非 env: 前缀 → null")
    void parse_non_env() {
        assertNull(Signals.parseEnv("event:maid_attack"), "event: 前缀非 env");
        assertNull(Signals.parseEnv("SNOWING"), "无前缀");
        assertNull(Signals.parseEnv(""), "空串");
    }

    @Test
    @DisplayName("parseEnv 未知值 → null")
    void parse_unknown() {
        assertNull(Signals.parseEnv("env:NOT_A_SIGNAL"), "未知枚举名");
        assertNull(Signals.parseEnv("env:SNOWINGX"), "前缀匹配但名不符");
    }

    @Test
    @DisplayName("parseEnv null → null")
    void parse_null() {
        assertNull(Signals.parseEnv(null));
    }
}

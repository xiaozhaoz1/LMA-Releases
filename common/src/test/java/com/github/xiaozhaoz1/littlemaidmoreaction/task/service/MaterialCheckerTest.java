package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MaterialChecker} 材料充足性检查测试。
 *
 * <p>Uses String keys instead of Item to avoid Forge class initialization in unit test environment.</p>
 */
public class MaterialCheckerTest {

    @Test
    @DisplayName("材料充足 → sufficient=true")
    void sufficient_returnsTrue() {
        Map<String, Integer> required = Map.of("log", 2);
        Map<String, Integer> available = Map.of("log", 5);
        MaterialReport<String> report = MaterialChecker.check(required, available);
        assertTrue(report.sufficient());
    }

    @Test
    @DisplayName("材料不足 → missing 报告差额")
    void short2_reportsMissing() {
        Map<String, Integer> required = Map.of("log", 5);
        Map<String, Integer> available = Map.of("log", 3);
        MaterialReport<String> report = MaterialChecker.check(required, available);
        assertFalse(report.sufficient());
        assertEquals(2, report.missing().get("log"));
    }

    @Test
    @DisplayName("材料恰好相等 → sufficient=true")
    void exactlyEnough_returnsTrue() {
        Map<String, Integer> required = Map.of("log", 3);
        Map<String, Integer> available = Map.of("log", 3);
        MaterialReport<String> report = MaterialChecker.check(required, available);
        assertTrue(report.sufficient());
    }

    @Test
    @DisplayName("多余材料被忽略 (不影响充足性)")
    void extraItems_ignored() {
        Map<String, Integer> required = Map.of("log", 2);
        Map<String, Integer> available = Map.of("log", 5, "stick", 10);
        MaterialReport<String> report = MaterialChecker.check(required, available);
        assertTrue(report.sufficient());
    }

    @Test
    @DisplayName("空需求 → sufficient=true")
    void emptyRequired_returnsTrue() {
        MaterialReport<String> report = MaterialChecker.check(Map.of(), Map.of());
        assertTrue(report.sufficient());
    }
}

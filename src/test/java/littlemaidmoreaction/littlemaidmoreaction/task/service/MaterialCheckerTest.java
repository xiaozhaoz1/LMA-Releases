package littlemaidmoreaction.littlemaidmoreaction.task.service;

import littlemaidmoreaction.littlemaidmoreaction.core.MaterialChecker;
import littlemaidmoreaction.littlemaidmoreaction.core.model.MaterialReport;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MaterialChecker}.
 *
 * <p>Uses String keys instead of Item to avoid Forge class initialization in unit test environment.</p>
 */
public class MaterialCheckerTest {

    @Test void sufficient_returnsTrue() {
        Map<String, Integer> required = Map.of("log", 2);
        Map<String, Integer> available = Map.of("log", 5);
        MaterialReport<String> report = MaterialChecker.check(required, available);
        assertTrue(report.sufficient());
    }

    @Test void short2_reportsMissing() {
        Map<String, Integer> required = Map.of("log", 5);
        Map<String, Integer> available = Map.of("log", 3);
        MaterialReport<String> report = MaterialChecker.check(required, available);
        assertFalse(report.sufficient());
        assertEquals(2, report.missing().get("log"));
    }

    @Test void exactlyEnough_returnsTrue() {
        Map<String, Integer> required = Map.of("log", 3);
        Map<String, Integer> available = Map.of("log", 3);
        MaterialReport<String> report = MaterialChecker.check(required, available);
        assertTrue(report.sufficient());
    }

    @Test void extraItems_ignored() {
        Map<String, Integer> required = Map.of("log", 2);
        Map<String, Integer> available = Map.of("log", 5, "stick", 10);
        MaterialReport<String> report = MaterialChecker.check(required, available);
        assertTrue(report.sufficient());
    }

    @Test void emptyRequired_returnsTrue() {
        MaterialReport<String> report = MaterialChecker.check(Map.of(), Map.of());
        assertTrue(report.sufficient());
    }
}

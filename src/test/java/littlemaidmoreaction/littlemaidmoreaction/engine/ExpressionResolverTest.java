package littlemaidmoreaction.littlemaidmoreaction.engine;

import littlemaidmoreaction.littlemaidmoreaction.core.expression.ExpressionResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** v35.1: 更新为 Function-based resolve() — MC 重载已移至 EngineUtils */
class ExpressionResolverTest {

    @Test
    @DisplayName("non-expression strings pass through unchanged")
    void nonExpression_passesThrough() {
        assertEquals("just_a_value", ExpressionResolver.resolve("just_a_value", k -> "0"));
    }

    @Test
    @DisplayName("null input returns null")
    void nullInput_returnsNull() {
        assertNull(ExpressionResolver.resolve(null, k -> "0"));
    }

    @Test
    @DisplayName("string without dollar passes through")
    void withoutDollar_passesThrough() {
        assertEquals("hello", ExpressionResolver.resolve("hello", k -> "0"));
    }

    @Test
    @DisplayName("mvel not available by default")
    void mvelNotAvailableByDefault() {
        assertFalse(ExpressionResolver.isMvelAvailable());
    }

    @Test
    @DisplayName("setMvelAvailable toggles flag")
    void setMvelAvailable() {
        ExpressionResolver.setMvelAvailable(true);
        assertTrue(ExpressionResolver.isMvelAvailable());
        ExpressionResolver.setMvelAvailable(false); // reset
    }
}

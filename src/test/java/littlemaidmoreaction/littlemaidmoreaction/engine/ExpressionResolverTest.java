package littlemaidmoreaction.littlemaidmoreaction.engine;

import littlemaidmoreaction.littlemaidmoreaction.core.expression.ExpressionResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionResolverTest {

    @Test
    @DisplayName("non-expression strings pass through unchanged")
    void nonExpression_passesThrough() {
        String result = ExpressionResolver.resolve("just_a_value", null, null, null);
        assertEquals("just_a_value", result);
    }

    @Test
    @DisplayName("null input returns null")
    void nullInput_returnsNull() {
        assertNull(ExpressionResolver.resolve(null, null, null, null));
    }

    @Test
    @DisplayName("string without dollar passes through")
    void withoutDollar_passesThrough() {
        assertEquals("hello", ExpressionResolver.resolve("hello", null, null, null));
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

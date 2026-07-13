package littlemaidmoreaction.littlemaidmoreaction.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActionStepTest {

    @Test
    @DisplayName("of() factory creates ActionStep from key-value pairs")
    void of_factory() {
        var step = ActionStep.of("play_anim", "anim_name", "execution", "speed", "1.5");
        assertEquals("play_anim", step.typeId());
        assertEquals("execution", step.params().get("anim_name"));
        assertEquals("1.5", step.params().get("speed"));
    }

    @Test
    @DisplayName("of() with odd kv count ignores trailing key")
    void of_oddKv() {
        var step = ActionStep.of("cmd", "command", "say hi", "extra");
        assertEquals("cmd", step.typeId());
        assertEquals("say hi", step.params().get("command"));
        assertNull(step.params().get("extra")); // trailing key has no value
    }

    @Test
    @DisplayName("of() with empty kv returns no params")
    void of_empty() {
        var step = ActionStep.of("reset_anim");
        assertEquals("reset_anim", step.typeId());
        assertTrue(step.params().isEmpty());
    }

    @Test
    @DisplayName("params are immutable")
    void params_immutable() {
        var step = new ActionStep("test", Map.of("key", "val"));
        assertThrows(UnsupportedOperationException.class, () -> step.params().put("new", "val"));
    }
}

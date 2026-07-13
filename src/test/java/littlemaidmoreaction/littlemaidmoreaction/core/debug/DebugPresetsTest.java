package littlemaidmoreaction.littlemaidmoreaction.core.debug;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DebugPresets} 的单元测试。
 */
class DebugPresetsTest {

    @Test
    @DisplayName("DEBUG_ID_START is 800")
    void debugIdStart() {
        assertEquals(800, DebugPresets.DEBUG_ID_START);
    }

    @Test
    @DisplayName("deleteAll removes only 800+ ID files")
    void deleteAll_onlyRemovesDebugFiles(@TempDir Path tempDir) throws IOException {
        create(tempDir, 7);
        create(tempDir, 200);
        create(tempDir, 800);
        create(tempDir, 801);
        create(tempDir, 999);

        DebugPresets.deleteAll(tempDir);

        assertTrue(Files.exists(tempDir.resolve("7.rule.json")));
        assertTrue(Files.exists(tempDir.resolve("200.rule.json")));
        assertFalse(Files.exists(tempDir.resolve("800.rule.json")));
        assertFalse(Files.exists(tempDir.resolve("801.rule.json")));
        assertFalse(Files.exists(tempDir.resolve("999.rule.json")));
    }

    @Test
    @DisplayName("deleteAll handles empty directory")
    void deleteAll_emptyDir(@TempDir Path tempDir) {
        DebugPresets.deleteAll(tempDir);  // no exception
    }

    @Test
    @DisplayName("debugPresetsExist returns true when 800+ file exists")
    void debugPresetsExist_true(@TempDir Path tempDir) throws IOException {
        create(tempDir, 800);
        assertTrue(DebugPresets.debugPresetsExist(tempDir));
    }

    @Test
    @DisplayName("debugPresetsExist returns false without 800+ files")
    void debugPresetsExist_false(@TempDir Path tempDir) throws IOException {
        create(tempDir, 7);
        create(tempDir, 200);
        assertFalse(DebugPresets.debugPresetsExist(tempDir));
    }

    @Test
    @DisplayName("non-numeric filenames are ignored")
    void nonNumeric_ignored(@TempDir Path tempDir) throws IOException {
        create(tempDir, 1);
        Files.writeString(tempDir.resolve("abc.rule.json"), "{}");
        Files.writeString(tempDir.resolve("readme.txt"), "hello");

        DebugPresets.deleteAll(tempDir);

        assertTrue(Files.exists(tempDir.resolve("1.rule.json")));
        assertTrue(Files.exists(tempDir.resolve("abc.rule.json")));
        assertTrue(Files.exists(tempDir.resolve("readme.txt")));
    }

    private void create(Path dir, int id) throws IOException {
        Files.writeString(dir.resolve(id + ".rule.json"), "{\"id\":" + id + "}");
    }
}

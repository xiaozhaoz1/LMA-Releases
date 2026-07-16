package littlemaidmoreaction.littlemaidmoreaction.task.service;

import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.MaterialChecker;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.MaterialReport;

import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidInventoryReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InventoryReaderTest {
    @Test void read_nullMaid_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> MaidInventoryReader.readAll(null));
    }
}

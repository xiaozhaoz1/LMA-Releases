package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.xiaozhaoz1.littlemaidmoreaction.core.MaterialChecker;
import com.github.xiaozhaoz1.littlemaidmoreaction.core.model.MaterialReport;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.MaidInventoryReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InventoryReaderTest {
    @Test void read_nullMaid_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> MaidInventoryReader.readAll(null));
    }
}

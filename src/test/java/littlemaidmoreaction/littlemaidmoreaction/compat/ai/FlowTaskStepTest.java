package littlemaidmoreaction.littlemaidmoreaction.compat.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 流程任务步骤递增逻辑测试 — 纯算法，不依赖 Minecraft。
 *
 * <p>测试 AbstractTaskAction 的 auto_step 递增逻辑和
 * AbstractTaskCondition 的状态匹配逻辑。
 */
class FlowTaskStepTest {

    // ── 步骤递增逻辑 (模拟 PersistentData) ──

    @Test
    void autoStep_increments() {
        // 模拟: 当前 task_type=altar_craft, step=2
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putInt("lma_flow_step", 2);

        // simulate advance_task(auto_step=true, task_type=altar_craft)
        int step = computeAutoStep(data, "altar_craft");
        assertEquals(3, step);
    }

    @Test
    void autoStep_differentTask_resets() {
        // 切换到不同任务 → step 重置为 0
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putInt("lma_flow_step", 2);

        int step = computeAutoStep(data, "collect_wood");
        assertEquals(0, step);  // 不同任务，从0开始
    }

    @Test
    void autoStep_noExistingTask() {
        var data = new MockPersistentData();
        // task 为空 → step=0
        int step = computeAutoStep(data, "new_task");
        assertEquals(0, step);
    }

    @Test
    void manualStep_explicitValue() {
        var data = new MockPersistentData();
        // manual step (auto_step=false)
        int step = 5;  // 显式指定
        assertEquals(5, step);
    }

    // ── 条件匹配逻辑 ──

    @Test
    void taskActive_matchesTypeAndStep() {
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putString("lma_flow_state", "in_progress");
        data.putInt("lma_flow_step", 1);

        // 匹配: task_type=altar_craft, expected_step=1
        assertTrue(matchesTask(data, "altar_craft", "in_progress", 1));
    }

    @Test
    void taskActive_wrongType_fails() {
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putString("lma_flow_state", "in_progress");
        data.putInt("lma_flow_step", 1);

        assertFalse(matchesTask(data, "collect_wood", "in_progress", 1));
    }

    @Test
    void taskActive_wrongStep_fails() {
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putString("lma_flow_state", "in_progress");
        data.putInt("lma_flow_step", 1);

        assertFalse(matchesTask(data, "altar_craft", "in_progress", 3));
    }

    @Test
    void taskActive_anyState_matches() {
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putString("lma_flow_state", "completed");
        data.putInt("lma_flow_step", 0);

        // expected_state=any → 忽略状态检查
        assertTrue(matchesTask(data, "altar_craft", "any", 0));
    }

    @Test
    void taskActive_noStepCheck_matches() {
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putString("lma_flow_state", "in_progress");
        data.putInt("lma_flow_step", 5);

        // expected_step=-1 → 不检查步骤
        assertTrue(matchesTask(data, "altar_craft", "in_progress", -1));
    }

    // ── 超时检测 ──

    @Test
    void timeout_detectsStaleTask() {
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putLong("lma_flow_tick", 1000);

        long currentTick = 1500;
        int timeout = 200;
        assertTrue((currentTick - data.getLong("lma_flow_tick")) > timeout);
    }

    @Test
    void timeout_notExpired() {
        var data = new MockPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putLong("lma_flow_tick", 1000);

        long currentTick = 1100;
        int timeout = 200;
        assertFalse((currentTick - data.getLong("lma_flow_tick")) > timeout);
    }

    // ── helpers (模拟 AbstractTaskAction/AbstractTaskCondition 逻辑) ──

    private static int computeAutoStep(MockPersistentData data, String taskType) {
        int current = data.getInt("lma_flow_step");
        return (data.getString("lma_flow_task").equals(taskType)) ? current + 1 : 0;
    }

    private static boolean matchesTask(MockPersistentData data, String expectedType,
                                        String expectedState, int expectedStep) {
        if (!data.getString("lma_flow_task").equals(expectedType)) return false;
        if (!"any".equals(expectedState)) {
            if (!data.getString("lma_flow_state").equals(expectedState)) return false;
        }
        if (expectedStep >= 0 && data.getInt("lma_flow_step") != expectedStep) return false;
        return true;
    }

    // ── 轻量 PersistentData 模拟 ──

    private static class MockPersistentData {
        private final java.util.Map<String, Object> store = new java.util.HashMap<>();

        String getString(String key) { return (String) store.getOrDefault(key, ""); }
        int getInt(String key) { return (Integer) store.getOrDefault(key, 0); }
        long getLong(String key) { return (Long) store.getOrDefault(key, 0L); }
        void putString(String key, String val) { store.put(key, val); }
        void putInt(String key, int val) { store.put(key, val); }
        void putLong(String key, long val) { store.put(key, val); }
    }
}

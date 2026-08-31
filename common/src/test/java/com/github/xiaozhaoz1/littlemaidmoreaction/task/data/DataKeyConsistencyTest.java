package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataKey 键表一致性守护测试 (v79.38) — 新增 DataKey 键必须声明清理归属:
 * 要么进 {@link DataKey#CLEAR_ALL_KEYS}(任务终结随 clearAll 清), 要么进 EXPLICIT_KEEP
 * (消费即删/独立生命周期/刻意持久/自过期 — 注释原因)。
 * 防 v79.37 类"漏键跨任务残留"回归 (v67 #67 类 bug)。
 */
class DataKeyConsistencyTest {

    /** 明确"不随任务终结清理"的键 — 每个都要写原因 */
    private static final Set<String> EXPLICIT_KEEP = Set.of(
            "TLM_SWITCH",   // 消费即删 (GameTickPipelineManager tickActive)
            "GUI_INIT",     // 消费即删 (同上)
            "AI_CONTROL",   // onCleanup 删 (AiControlPipeline.onCleanup → AiControlGate.disable)
            "ARM_TAKE",     // 刻意持久 (玩家只设一次, ArmTransferPipeline 注释)
            "ARM_DEPOSIT",  // 同上
            "ARM_ITEM",     // cleanup 删 (ArmTransferPipeline.cleanup)
            "ASSEMBLY_INV"  // 独立生命周期 (MaidAssemblyInventory CACHE/槽位)
    );

    @Test
    @DisplayName("每个 DataKey 静态键必须在 CLEAR_ALL_KEYS 或 EXPLICIT_KEEP")
    void everyKeyIsEitherClearedOrExplicitlyKept() throws IllegalAccessException {
        for (Field f : DataKey.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (f.getName().equals("CLEAR_ALL_KEYS")) continue;
            Object v = f.get(null);
            if (!(v instanceof DataKey<?> dk)) continue;
            boolean inClear = DataKey.CLEAR_ALL_KEYS.contains(dk);
            boolean kept = EXPLICIT_KEEP.contains(f.getName());
            assertTrue(inClear || kept,
                    "DataKey." + f.getName() + " 未在 CLEAR_ALL_KEYS 也未声明 EXPLICIT_KEEP — "
                            + "新增键必须声明清理归属 (终结残留防护)");
        }
    }
}

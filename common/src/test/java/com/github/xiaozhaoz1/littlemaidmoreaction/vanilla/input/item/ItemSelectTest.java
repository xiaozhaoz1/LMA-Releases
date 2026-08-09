package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ItemSelect 泛型纯核心测试 (v77.5) — 谓词/评分器注入, List&lt;Integer&gt; 零 MC 依赖。
 *
 * <p>ItemStack/BlockState 版不可纯 JVM 测 (MC 类 mock 插桩失败 — 注册表陷阱家族) —
 * ToolSelectExecute.miningScore 逻辑由客户端手动验证。
 */
class ItemSelectTest {

    @Test
    @DisplayName("评分最高的候选胜出")
    void selectBestFrom_highestScore_wins() {
        var pick = ItemSelect.selectBestFrom(List.of(10, 20, 30),
                v -> true, v -> v == 10 ? 1.0 : (v == 20 ? 5.0 : 2.0));
        assertTrue(pick.isPresent());
        assertEquals(1, pick.get().slot());   // 20 (idx 1) 最高分
        assertEquals(20, pick.get().value());
    }

    @Test
    @DisplayName("谓词过滤排除候选")
    void selectBestFrom_predicateFilters() {
        var pick = ItemSelect.selectBestFrom(List.of(1, 2, 3), v -> v >= 3, v -> v);
        assertTrue(pick.isPresent());
        assertEquals(2, pick.get().slot());
        assertEquals(3, pick.get().value());
    }

    @Test
    @DisplayName("空列表/全过滤 → empty")
    void selectBestFrom_emptyOrFiltered_empty() {
        assertTrue(ItemSelect.selectBestFrom(List.of(), v -> true, v -> 1.0).isEmpty());
        assertTrue(ItemSelect.selectBestFrom(List.of(1, 2), v -> false, v -> 1.0).isEmpty());
    }

    @Test
    @DisplayName("负分候选可被选中 (唯一候选)")
    void selectBestFrom_negativeScore_singleCandidate() {
        var pick = ItemSelect.selectBestFrom(List.of(5), v -> true, v -> -10.0);
        assertTrue(pick.isPresent());
        assertEquals(0, pick.get().slot());
    }

    @Test
    @DisplayName("平分取先者 (稳定性)")
    void selectBestFrom_tie_firstWins() {
        var pick = ItemSelect.selectBestFrom(List.of(1, 2, 3), v -> true, v -> 0.0);
        assertTrue(pick.isPresent());
        assertEquals(0, pick.get().slot());
    }
}

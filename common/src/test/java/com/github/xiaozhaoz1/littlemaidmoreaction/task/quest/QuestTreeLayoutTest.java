package com.github.xiaozhaoz1.littlemaidmoreaction.task.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link QuestTreeLayout} 布局单测 — 纯 JVM (无 MC 依赖, 错题 #174)。
 */
class QuestTreeLayoutTest {

    private static QuestNode node(String id, QuestNode... children) {
        QuestNode n = new QuestNode(id, null, null, null, "minecraft:stone", "task", null);
        for (QuestNode c : children) n.children().add(c);
        return n;
    }

    @Test
    void 单根_坐标原点() {
        QuestNode root = node("root");
        Map<String, QuestTreeLayout.Pos> pos = QuestTreeLayout.layout(List.of(root));
        assertEquals(0, pos.get("root").x());
        assertEquals(0, pos.get("root").y());
    }

    @Test
    void 两层_父居中于子() {
        QuestNode a = node("a");
        QuestNode b = node("b");
        QuestNode root = node("root", a, b);
        Map<String, QuestTreeLayout.Pos> pos = QuestTreeLayout.layout(List.of(root));
        // 子 y = 1 层
        assertEquals(QuestTreeLayout.NODE_H + QuestTreeLayout.GAP_Y, pos.get("a").y());
        assertEquals(pos.get("a").y(), pos.get("b").y());
        // 父 x 居中: 子 a x=0, b x=NODE_W+GAP_X=36 → 父 x=(0+36)/2=18
        assertEquals(18, pos.get("root").x());
        // 父 y = 0
        assertEquals(0, pos.get("root").y());
    }

    @Test
    void 三层_深度对齐() {
        QuestNode leaf = node("leaf");
        QuestNode mid = node("mid", leaf);
        QuestNode root = node("root", mid);
        Map<String, QuestTreeLayout.Pos> pos = QuestTreeLayout.layout(List.of(root));
        assertEquals(0, pos.get("root").y());
        assertEquals(QuestTreeLayout.NODE_H + QuestTreeLayout.GAP_Y, pos.get("mid").y());
        assertEquals(2 * (QuestTreeLayout.NODE_H + QuestTreeLayout.GAP_Y), pos.get("leaf").y());
    }

    @Test
    void 多根_依次排开() {
        QuestNode r1 = node("r1");
        QuestNode r2 = node("r2");
        Map<String, QuestTreeLayout.Pos> pos = QuestTreeLayout.layout(List.of(r1, r2));
        assertEquals(0, pos.get("r1").x());
        assertTrue(pos.get("r2").x() > pos.get("r1").x());
    }

    @Test
    void 空树_空坐标() {
        Map<String, QuestTreeLayout.Pos> pos = QuestTreeLayout.layout(List.of());
        assertTrue(pos.isEmpty());
    }
}

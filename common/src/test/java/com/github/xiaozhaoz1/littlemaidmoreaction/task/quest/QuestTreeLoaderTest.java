package com.github.xiaozhaoz1.littlemaidmoreaction.task.quest;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link QuestTreeLoader} 解析单测 — 纯 JVM (Gson 编程式构造 JSON, 零转义, 不触碰 MC 注册表, 错题 #174)。
 */
class QuestTreeLoaderTest {

    private static JsonObject display(String title, String icon, String frame) {
        JsonObject t = new JsonObject();
        t.addProperty("translate", title);
        JsonObject d = new JsonObject();
        d.add("title", t);
        JsonObject ic = new JsonObject();
        ic.addProperty("id", icon);
        d.add("icon", ic);
        d.addProperty("frame", frame);
        return d;
    }

    private static JsonObject quest(String parent, JsonObject display) {
        JsonObject o = new JsonObject();
        if (parent != null) o.addProperty("parent", parent);
        if (display != null) o.add("display", display);
        return o;
    }

    private static Map<String, JsonObject> sample() {
        Map<String, JsonObject> m = new LinkedHashMap<>();
        JsonObject rootDisp = display("advancements.story.root.title", "minecraft:grass_block", "task");
        rootDisp.addProperty("background", "minecraft:textures/gui/advancements/backgrounds/stone.png");
        m.put("minecraft:story/root", quest(null, rootDisp));
        m.put("minecraft:story/mine_stone", quest("minecraft:story/root",
                display("advancements.story.mine_stone.title", "minecraft:wooden_pickaxe", "task")));
        m.put("minecraft:story/hidden_mid", quest("minecraft:story/root", null));
        m.put("minecraft:story/visible_leaf", quest("minecraft:story/hidden_mid",
                display("x", "minecraft:stone", "goal")));
        return m;
    }

    @Test
    void 解析根节点_含背景() {
        List<QuestNode> roots = QuestTreeLoader.buildTree(sample());
        assertEquals(1, roots.size());
        QuestNode root = roots.get(0);
        assertEquals("minecraft:story/root", root.id());
        assertEquals("advancements.story.root.title", root.titleKey());
        assertEquals("minecraft:grass_block", root.iconId());
        assertEquals("minecraft:textures/gui/advancements/backgrounds/stone.png", root.background());
        assertTrue(root.isRoot());
    }

    @Test
    void 子节点挂到父() {
        List<QuestNode> roots = QuestTreeLoader.buildTree(sample());
        QuestNode root = roots.get(0);
        // hidden_mid 无 display 被提升, visible_leaf 变根直接子 → root 有 2 子
        assertEquals(2, root.children().size());
        assertEquals("minecraft:story/mine_stone", root.children().get(0).id());
        assertEquals("minecraft:story/visible_leaf", root.children().get(1).id());
        assertEquals("goal", root.children().get(1).frame());
    }

    @Test
    void 隐藏无display_整枝删() {
        Map<String, JsonObject> m = new LinkedHashMap<>();
        m.put("a", quest(null, display("t", "minecraft:stone", "task")));
        m.put("a_hidden_child", quest("a", null));
        List<QuestNode> roots = QuestTreeLoader.buildTree(m);
        assertEquals(1, roots.size());
        assertTrue(roots.get(0).children().isEmpty());
    }
}

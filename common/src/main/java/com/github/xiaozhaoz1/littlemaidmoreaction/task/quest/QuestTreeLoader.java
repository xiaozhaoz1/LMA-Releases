package com.github.xiaozhaoz1.littlemaidmoreaction.task.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * 任务树加载器 — 从数据包资源读取原版成就 JSON (client jar 内 data/minecraft/advancement/*.json),
 * 解析成 {@link QuestNode} 树 (M0)。
 *
 * <p>数据来源: 运行时读数据包 (零资源复制, 自动跟随版本 — 用户裁定 2026-08-31)。
 * 双平台路径差异: 1.20.1 = {@code data/minecraft/advancements/} (复数) /
 * 1.21.1 = {@code data/minecraft/advancement/} (单数, 1.20.5+ 目录单数化, 错题 #245 同族)。
 *
 * <p>解析规则 (对齐原版 advancement JSON 结构, 已源码实证):
 * <ul>
 *   <li>{@code parent} — 依赖父 id (缺省 = 根/章节根)</li>
 *   <li>{@code display.title/description} — translate key</li>
 *   <li>{@code display.icon.id} — 物品 id; {@code display.frame} — task/goal/challenge</li>
 *   <li>{@code display.background} — 章节背景 (根节点)</li>
 *   <li>无 display 的 (纯隐藏/进度型) 跳过 — 不展示 (原版有隐藏成就)</li>
 * </ul>
 *
 * <p>纯解析 (JSON→树) 可 JVM 单测 — 传 JSON 字符串/文件即可 (错题 #174);
 * 数据包资源读取 (ResourceManager) 在服务端可用, 双平台差异在路径常量。
 */
public final class QuestTreeLoader {

    /** 数据包根路径 — 1.20.1 复数 / 1.21.1 单数 (stonecutter 条件化) */
    public static final String ADVANCEMENT_PATH =
//? if 1.20.1 {
            "data/minecraft/advancements";
//?} else {
            "data/minecraft/advancement";
//?}

    private QuestTreeLoader() {}

    /**
     * 从资源管理器读取全部原版成就 JSON 并构建任务树 (根集合)。
     * 资源管理器未就绪/读取失败 → 空树 (不崩)。
     */
    public static List<QuestNode> loadFromResources(ResourceManager manager) {
        if (manager == null) return List.of();
        Map<String, JsonObject> raw = new LinkedHashMap<>();
        // 遍历数据包内所有 achievement json (含命名空间前缀)
        for (String ns : manager.getNamespaces()) {
            // 双平台: 1.20.1 目录 "advancements", 1.21.1 "advancement" — 统一尝试两个
            for (String dir : new String[]{"advancements", "advancement"}) {
                try {
                    // 双平台 API 差异: 1.20.1 listResources 返回 Map<ResourceLocation,Resource>,
                    // 1.21.1 返回 Collection<ResourceLocation> — 统一取 keys()/直接遍历
                    for (ResourceLocation loc : manager.listResources(dir, s -> s.getPath().endsWith(".json")).keySet()) {
                        Optional<Resource> opt = manager.getResource(loc);  // 1.20.1 Optional
                        if (opt.isEmpty()) continue;
                        try (Reader reader = opt.get().openAsReader()) {
                            JsonElement el = JsonParser.parseReader(reader);
                            if (el.isJsonObject()) {
                                // id = 去后缀 + 去目录前缀 (minecraft:story/mine_stone)
                                String id = loc.getPath().replace(".json", "")
                                        .replace("advancements/", "")
                                        .replace("advancement/", "");
                                raw.put(ns + ":" + id, el.getAsJsonObject());
                            }
                        }
                    }
                } catch (Exception ignore) {
                    // 目录不存在/无权限 — 跳过 (1.20.1 无 "advancement" 单数目录)
                }
            }
        }
        return buildTree(raw);
    }

    /**
     * 从 JSON 映射构建树 (纯函数, JVM 可测)。
     *
     * @param raw 成就 id → JSON 对象 (id 如 "minecraft:story/mine_stone")
     */
    public static List<QuestNode> buildTree(Map<String, JsonObject> raw) {
        // 先解析所有节点 (含隐藏, 用于依赖; 展示时过滤无 display)
        Map<String, QuestNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : raw.entrySet()) {
            String id = e.getKey();
            JsonObject obj = e.getValue().getAsJsonObject();
            String parentId = obj.has("parent") ? obj.get("parent").getAsString() : null;

            String titleKey = null, descKey = null, iconId = null, frame = null, bg = null;
            if (obj.has("display") && obj.get("display").isJsonObject()) {
                JsonObject d = obj.getAsJsonObject("display");
                if (d.has("title") && d.get("title").isJsonObject() && d.getAsJsonObject("title").has("translate")) {
                    titleKey = d.getAsJsonObject("title").get("translate").getAsString();
                }
                if (d.has("description") && d.get("description").isJsonObject() && d.getAsJsonObject("description").has("translate")) {
                    descKey = d.getAsJsonObject("description").get("translate").getAsString();
                }
                if (d.has("icon") && d.get("icon").isJsonObject() && d.getAsJsonObject("icon").has("id")) {
                    iconId = d.getAsJsonObject("icon").get("id").getAsString();
                }
                if (d.has("frame")) frame = d.get("frame").getAsString();
                if (d.has("background")) bg = d.get("background").getAsString();
            }
            nodes.put(id, new QuestNode(id, parentId, titleKey, descKey, iconId, frame, bg));
        }
        // 挂父子
        List<QuestNode> roots = new ArrayList<>();
        for (QuestNode n : nodes.values()) {
            if (n.parentId() == null) {
                roots.add(n);
            } else {
                QuestNode parent = nodes.get(n.parentId());
                if (parent != null) parent.children().add(n);
                else roots.add(n); // 父缺失 (跨命名空间) 视为根
            }
        }
        // 只保留可展示节点 (有 display) — 隐藏成就过滤
        List<QuestNode> visibleRoots = new ArrayList<>();
        for (QuestNode r : roots) {
            QuestNode visible = filterHidden(r);
            if (visible != null) visibleRoots.add(visible);
        }
        return visibleRoots;
    }

    /** 递归过滤无 display 节点 (隐藏成就不展示; 但其可见子节点提升为根) */
    private static QuestNode filterHidden(QuestNode node) {
        // 收集可见子节点
        List<QuestNode> visChildren = new ArrayList<>();
        for (QuestNode c : node.children()) {
            QuestNode vc = filterHidden(c);
            if (vc != null) visChildren.add(vc);
        }
        if (!node.hasDisplay() && visChildren.isEmpty()) {
            return null; // 隐藏且无可见子 → 整枝删
        }
        // 隐藏但有可见子 → 子提升为根 (丢失一级深度)
        if (!node.hasDisplay()) {
            return visChildren.size() == 1 ? visChildren.get(0) : null; // 多可见子难提升, 留空
        }
        node.children().clear();
        node.children().addAll(visChildren);
        return node;
    }

    /** 调试: 打印树 (缩进) */
    public static void printTree(List<QuestNode> roots, StringBuilder sb) {
        for (QuestNode r : roots) printNode(r, 0, sb);
    }

    private static void printNode(QuestNode n, int depth, StringBuilder sb) {
        sb.append("  ".repeat(depth)).append(n.id()).append(" [").append(n.iconId()).append("]\n");
        for (QuestNode c : n.children()) printNode(c, depth + 1, sb);
    }
}

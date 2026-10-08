package com.github.xiaozhaoz1.littlemaidmoreaction.task.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务树节点 — 从原版成就 JSON 解析出的轻量数据模型 (M0)。
 *
 * <p>对齐原版 advancement JSON 结构 (client jar 内 data/minecraft/advancement/*.json):
 * <ul>
 *   <li>{@code id} — 完整 id (如 "minecraft:story/mine_stone")</li>
 *   <li>{@code parent} — 依赖父节点 id (null = 根/章节根)</li>
 *   <li>{@code title/description} — 显示文本 (translate key, 由调用方转 Component)</li>
 *   <li>{@code icon} — 物品 id (如 "minecraft:stone")</li>
 *   <li>{@code frame} — task/goal/challenge (原版 AdvancementType)</li>
 *   <li>{@code background} — 章节背景贴图 (根节点有, 子节点继承章节)</li>
 * </ul>
 *
 * <p>纯数据载体 — 布局坐标 {@link QuestTreeLayout} 算, 进度 {@code QuestProgressReader} 读,
 * 本类不依赖 MC client (只依赖 ResourceLocation 轻量 id)。
 */
public final class QuestNode {

    private final String id;
    private final String parentId;
    private final String titleKey;
    private final String descriptionKey;
    private final String iconId;
    private final String frame;       // task / goal / challenge
    private final String background;  // 章节背景贴图路径 (根节点)
    private final List<QuestNode> children = new ArrayList<>();

    public QuestNode(String id, String parentId, String titleKey, String descriptionKey,
                     String iconId, String frame, String background) {
        this.id = id;
        this.parentId = parentId;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.iconId = iconId;
        this.frame = frame;
        this.background = background;
    }

    public String id() { return id; }
    public String parentId() { return parentId; }
    public String titleKey() { return titleKey; }
    public String descriptionKey() { return descriptionKey; }
    public String iconId() { return iconId; }
    public String frame() { return frame; }
    public String background() { return background; }

    public List<QuestNode> children() { return children; }

    public boolean isRoot() { return parentId == null; }

    /** 是否已解析出显示信息 (有 icon 才算可展示; 纯隐藏/进度型无 display 的跳过) */
    public boolean hasDisplay() { return iconId != null && !iconId.isEmpty(); }

    @Override
    public String toString() {
        return "QuestNode{id='" + id + "', parent='" + parentId + "', icon='" + iconId + "'}";
    }
}

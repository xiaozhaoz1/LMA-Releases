package com.github.xiaozhaoz1.littlemaidmoreaction.task.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务树自动布局 — 经典递归树布局 (M0, 纯函数, 零 MC 依赖, 可 JVM 单测, 错题 #174)。
 *
 * <p>原版成就坐标在 {@code AdvancementPositioner} 运行时算 (JSON 无 x/y, 已实证);
 * FTB Quests 用手动坐标 (编辑器摆)。本实现采用经典递归树布局:
 * <ul>
 *   <li>同一深度 (层) y 对齐 — 每层固定行高, y = depth * ROW_H</li>
 *   <li>子树宽度 = 该子树所有叶子宽度和 (叶宽 = NODE_W + GAP_X)</li>
 *   <li>父节点水平居中于子节点 — 父 x = (最左子 x + 最右子 x) / 2</li>
 *   <li>叶子节点 x 依次排列 (累加)</li>
 * </ul>
 * 输出: {@code Map<id, (x,y)>} 像素坐标 (节点左上角)。调用方 (UI) 加 scrollX/Y 平移。
 *
 * <p>常量对齐原版: 节点 26x26 (原版 AdvancementWidget 尺寸), 行高 27 (原版 y*27)。
 */
public final class QuestTreeLayout {

    /** 节点宽 (像素) — 对齐原版 26 + 2 边距 */
    public static final int NODE_W = 28;
    /** 节点高 — 对齐原版 27 */
    public static final int NODE_H = 27;
    /** 兄弟节点水平间距 */
    public static final int GAP_X = 8;
    /** 层间距 (垂直) */
    public static final int GAP_Y = 16;

    /** 布局结果: 节点 id → (x, y) 左上角像素坐标 */
    public record Pos(int x, int y) {}

    private QuestTreeLayout() {}

    /**
     * 对任务树执行递归布局。
     *
     * @param roots 所有根节点 (章节根; 同一棵树多根按序排)
     * @return id → 坐标
     */
    public static Map<String, Pos> layout(java.util.List<QuestNode> roots) {
        Map<String, Pos> out = new HashMap<>();
        int cursorX = 0;
        for (QuestNode root : roots) {
            cursorX = layoutSubtree(root, 0, cursorX, out);
            cursorX += GAP_X; // 多根间留距
        }
        return out;
    }

    /**
     * 递归布局子树, 返回该子树占用的总宽度 (用于父居中与兄弟错位)。
     *
     * @param node   当前节点
     * @param depth  深度 (根=0; y = depth * (NODE_H + GAP_Y))
     * @param startX 本子树可用起始 x (叶子依次排)
     * @param out    结果累积
     * @return 子树占宽 (叶 = NODE_W, 非叶 = 子占宽和)
     */
    private static int layoutSubtree(QuestNode node, int depth, int startX, Map<String, Pos> out) {
        int y = depth * (NODE_H + GAP_Y);
        if (node.children().isEmpty()) {
            // 叶子: 占用一个节点位
            out.put(node.id(), new Pos(startX, y));
            return NODE_W;
        }
        // 非叶: 递归布局子节点, 子占宽累加
        int childStart = startX;
        for (QuestNode child : node.children()) {
            childStart += layoutSubtree(child, depth + 1, childStart, out);
            childStart += GAP_X;
        }
        int subtreeWidth = childStart - startX - GAP_X; // 去掉末尾间距
        // 父居中于子节点
        int firstChildX = out.get(node.children().get(0).id()).x();
        int lastChildX = out.get(node.children().get(node.children().size() - 1).id()).x();
        int parentX = (firstChildX + lastChildX) / 2;
        out.put(node.id(), new Pos(parentX, y));
        return Math.max(subtreeWidth, NODE_W);
    }
}

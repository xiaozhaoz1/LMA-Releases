package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务树 (v35.4: 简化版, 展示 enabled/showInBar + pipeline steps)。
 * v63: 新增被动任务分区 (showInBar=false)。
 */
public final class TaskTree {

    public record TaskNode(String taskType, String label, ItemStack icon,
                           List<TaskPipeline.TaskStep> steps,
                           boolean enabled, boolean visible, boolean passive) {}

    public static List<TaskNode> build() {
        List<TaskNode> nodes = new ArrayList<>();
        for (String taskType : TaskRegistry.taskTypes()) {
            TaskRegistry.TaskHandler handler = TaskRegistry.get(taskType);
            if (handler == null) continue;
            // v79.61x: 纯触发型被动无 pipeline — 类名兜底 taskType (用户裁定), steps 空
            TaskPipeline pipeline = handler.pipeline();
            // v79.62.2 显示名走翻译键 (task.littlemaidmoreaction.<type>, 如 dam_fill=填坝) — 找不到回退类名
            String label = pipeline == null ? taskType
                    : net.minecraft.network.chat.Component.translatable(
                            "task." + com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.MOD_ID + "." + taskType)
                        .getString();
            if ((label.startsWith("task.") || label.equals(taskType)) && pipeline != null) {
                label = pipeline.getClass().getSimpleName().replace("Pipeline", "");
            }
            List<TaskPipeline.TaskStep> steps = pipeline == null ? List.of() : pipeline.steps();
            nodes.add(new TaskNode(taskType, label,
                LmaTaskTypeRegistry.getIcon(taskType),
                steps,
                TaskToggle.isEnabled(taskType),
                TaskToggle.isVisible(taskType),
                !handler.showInBar()));
        }
        return nodes;
    }

    /** 仅被动任务 (showInBar=false) */
    public static List<TaskNode> buildPassive() {
        List<TaskNode> nodes = new ArrayList<>();
        for (var n : build()) {
            if (n.passive()) nodes.add(n);
        }
        return nodes;
    }

    /** 仅主动任务 (showInBar=true) */
    public static List<TaskNode> buildActive() {
        List<TaskNode> nodes = new ArrayList<>();
        for (var n : build()) {
            if (!n.passive()) nodes.add(n);
        }
        return nodes;
    }

    public static List<TaskGroup.GroupDef> buildGroups() { return TaskGroup.all(); }

    public static String buildText() {
        StringBuilder sb = new StringBuilder("§6═══ 任务 ═══\n");
        for (var n : build()) {
            sb.append(n.enabled() ? "§a✔" : "§c✖");
            sb.append(n.visible() ? " §f" : " §8");
            sb.append(n.taskType());
            if (!n.steps().isEmpty()) { sb.append(" §7"); n.steps().forEach(s -> sb.append(s.label()).append(" ")); }
            sb.append("\n");
        }
        sb.append("\n§6═══ 分组 ═══\n");
        for (var g : TaskGroup.all())
            sb.append("§f📁 ").append(g.label()).append(" §7→ ").append(String.join(", ", g.tasks())).append("\n");
        return sb.toString();
    }

    private TaskTree() {}
}

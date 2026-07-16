package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task;

import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter.LmaTaskTypeRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务树 (v35.4: 简化版, 展示 enabled/showInBar + pipeline steps)。
 */
public final class TaskTree {

    public record TaskNode(String taskType, String label, ItemStack icon,
                           List<TaskPipeline.TaskStep> steps,
                           boolean enabled, boolean visible) {}

    public static List<TaskNode> build() {
        List<TaskNode> nodes = new ArrayList<>();
        for (String taskType : TaskRegistry.taskTypes()) {
            TaskRegistry.TaskHandler handler = TaskRegistry.get(taskType);
            if (handler == null) continue;
            nodes.add(new TaskNode(taskType,
                handler.pipeline().getClass().getSimpleName().replace("Pipeline", ""),
                LmaTaskTypeRegistry.getIcon(taskType),
                handler.pipeline().steps(),
                TaskToggle.isEnabled(taskType),
                TaskToggle.isVisible(taskType)));
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

package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;

import java.util.*;

/**
 * 并行组分析器 — 将动作序列分析为可并行执行的组。
 *
 * <p>相邻无冲突动作 → 同一并行组。
 * 遇到 WAIT/REPEAT/CONDITIONAL/CANCEL_EVENT → 断开组。
 * 冲突动作（IAction.conflicts()） → 断开组，在新组中执行。
 */
public final class GroupBuilder {

    /**
     * 分析动作序列，返回并行组列表。
     *
     * @param steps 规则的动作序列
     * @return 并行组列表（按原始顺序）
     */
    public static List<ParallelGroup> build(List<ActionStep> steps) {
        List<ParallelGroup> groups = new ArrayList<>();
        List<ActionStep> current = new ArrayList<>();
        Set<String> currentIds = new LinkedHashSet<>();

        for (int i = 0; i < steps.size(); i++) {
            ActionStep step = steps.get(i);
            String typeId = step.typeId();

            // ★ auto_wait: play_anim/play_weapon_anim 自等待时单独成组
            if (("play_anim".equals(typeId) || "play_weapon_anim".equals(typeId))
                && "true".equals(step.params().getOrDefault("auto_wait", "false"))) {
                if (!current.isEmpty()) {
                    groups.add(ParallelGroup.actionGroup(current));
                    current = new ArrayList<>();
                    currentIds.clear();
                }
                current.add(step);
                currentIds.add(typeId);
                groups.add(ParallelGroup.actionGroup(current));
                current = new ArrayList<>();
                currentIds.clear();
                continue;
            }

            // 流程控制 — 断开并创建控制组
            if (isFlowControl(typeId)) {
                if (!current.isEmpty()) {
                    groups.add(ParallelGroup.actionGroup(current));
                    current = new ArrayList<>();
                    currentIds.clear();
                }
                groups.add(buildControlGroup(typeId, i, step));
                continue;
            }

            IAction action = ActionRegistry.get(typeId);
            if (action == null) continue;

            // 冲突检测 — 若与组内已有动作冲突则断开
            boolean conflicts = currentIds.stream()
                .anyMatch(id -> ActionRegistry.areConflicting(id, typeId)
                              || ActionRegistry.areConflicting(typeId, id));
            if (conflicts && !current.isEmpty()) {
                groups.add(ParallelGroup.actionGroup(current));
                current = new ArrayList<>();
                currentIds.clear();
            }

            current.add(step);
            currentIds.add(typeId);
        }

        // 最后一组
        if (!current.isEmpty()) {
            groups.add(ParallelGroup.actionGroup(current));
        }
        return groups;
    }

    private static boolean isFlowControl(String typeId) {
        return "wait".equals(typeId) || "wait_anim".equals(typeId) || "repeat".equals(typeId)
            || "cancel_event".equals(typeId) || "random".equals(typeId);
    }

    private static ParallelGroup buildControlGroup(String typeId, int idx, ActionStep step) {
        return switch (typeId) {
            case "wait", "wait_anim" -> ParallelGroup.waitGroup(idx);
            case "repeat" -> {
                int count = Integer.parseInt(
                    step.params().getOrDefault("count", "3"));
                yield ParallelGroup.repeatGroup(idx, count);
            }
            case "cancel_event" -> ParallelGroup.cancelGroup();
            case "random" -> {
                double chance = Double.parseDouble(
                    step.params().getOrDefault("chance", "0.5"));
                int skip = Integer.parseInt(
                    step.params().getOrDefault("skip", "1"));
                yield ParallelGroup.randomGroup(chance, skip);
            }
            default -> throw new IllegalStateException("Unknown flow control: " + typeId);
        };
    }

    private GroupBuilder() {}
}

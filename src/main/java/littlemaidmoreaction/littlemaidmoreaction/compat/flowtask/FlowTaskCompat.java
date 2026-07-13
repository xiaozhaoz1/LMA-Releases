package littlemaidmoreaction.littlemaidmoreaction.compat.flowtask;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.CompatScanner;

/**
 * v10 流程任务系统兼容模块 — 始终启用。
 *
 * <p>提供 has_flow_task/flow_task_state 条件 + set_flow_task 动作。
 */
public final class FlowTaskCompat {

    public static void init() {
        CompatScanner.scan(FlowTaskCompat.class,
            "littlemaidmoreaction/littlemaidmoreaction/compat/flowtask/impl/condition/",
            "littlemaidmoreaction/littlemaidmoreaction/compat/flowtask/impl/action/",
            null);

        LittleMaidMoreAction.LOGGER.info("[FlowTask] 流程任务模块已初始化");
    }

    private FlowTaskCompat() {}
}

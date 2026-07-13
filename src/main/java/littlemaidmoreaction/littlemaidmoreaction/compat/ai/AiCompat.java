package littlemaidmoreaction.littlemaidmoreaction.compat.ai;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;

/**
 * v10 AI 兼容门控 — 始终启用 (无模组依赖)。
 *
 * <p>为 AI_INTEGRATION_DESIGN.md 三系统提供包结构入口：
 * <ul>
 *   <li>AI 自主规则创建 — 7 个 ITool</li>
 *   <li>女仆合成台 — 5 条件 + 4 动作 + GUI</li>
 *   <li>流程任务系统 — TaskDataRegister + 状态机</li>
 * </ul>
 */
public final class AiCompat {

    public static void init() {
        LittleMaidMoreAction.LOGGER.info("[LMA/AI] AI integration ready (v10 stub)");
    }

    private AiCompat() {}
}

package littlemaidmoreaction.littlemaidmoreaction.compat.ai.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;

import java.util.List;

/**
 * LMA 状态摘要 — 自动注入每条对话 (v10)。
 *
 * <p>promptContext=true，LLM 始终知道：
 * - 活跃规则数量和 ID
 * - 禁用规则数量和 ID
 * - 当前流程任务状态
 *
 * <p>token 占用极小（≤2 行），但价值巨大：
 * LLM 能主动感知 LMA 状态，无需每次调用工具查询。
 * 当主人说"修改规则"时，LLM 已知道有哪些规则可用。
 */
public final class LmaStatusContext {

    public static final String CATEGORY = "lma_status";
    private static final String SUMMARY = "LMA rule engine status: active rules, flow tasks.";
    private static final String LINE_SEPARATOR = ", ";
    /** 自动注入中最多展示的活跃规则数（防 token 膨胀） */
    private static final int MAX_ACTIVE_SHOWN = 10;
    /** 自动注入中最多展示的禁用规则数 */
    private static final int MAX_DISABLED_SHOWN = 5;

    private LmaStatusContext() {}

    public static void registerAll(GameContextRegister register) {
        register.registerCategory(CATEGORY, SUMMARY, true);
        register.registerContext(CATEGORY, new LmaActiveRulesContext());
    }

    private static final class LmaActiveRulesContext extends AbstractMaidContext {
        private LmaActiveRulesContext() {
            super("lma_rules", "Active and disabled LMA rules");
        }

        @Override
        public String getValue(EntityMaid maid) {
            List<RuleDef> rules = RuleActionStorage.getRules();
            if (rules.isEmpty()) return "no LMA rules";

            List<RuleDef> active = rules.stream().filter(RuleDef::enabled).toList();
            List<RuleDef> disabled = rules.stream().filter(r -> !r.enabled()).toList();

            StringBuilder sb = new StringBuilder();

            // ★ v12.6: 强化身份消歧 + 任务关键词检测
            sb.append("IDENTITY: You ARE this maid entity. Player=owner. ");
            if (maid.hasCustomName()) sb.append("Your name is \"").append(maid.getCustomName().getString()).append("\". ");
            sb.append("CRITICAL: owner says \"I/me/my\" = owner NOT you. ");
            sb.append("\"you\"/\"maid\" = this maid (YOU). ");
            sb.append("target=self=YOU. target=owner=player. ");
            sb.append(LmaTaskTypeRegistry.buildTaskKeywordPrompt());
            sb.append(" | ");

            // 活跃规则 (上限保护)
            int totalActive = active.size();
            sb.append("LMA active rules (").append(totalActive).append("): ");
            if (active.isEmpty()) {
                sb.append("none");
            } else {
                var shown = active.subList(0, Math.min(totalActive, MAX_ACTIVE_SHOWN));
                sb.append(buildRuleSummary(shown));
                if (totalActive > MAX_ACTIVE_SHOWN) {
                    sb.append("...and ").append(totalActive - MAX_ACTIVE_SHOWN).append(" more");
                }
            }

            // 禁用规则 (上限保护)
            if (!disabled.isEmpty()) {
                int totalDisabled = disabled.size();
                sb.append(" | disabled (").append(totalDisabled).append("): ");
                var shown = disabled.subList(0, Math.min(totalDisabled, MAX_DISABLED_SHOWN));
                sb.append(buildRuleSummary(shown));
                if (totalDisabled > MAX_DISABLED_SHOWN) {
                    sb.append("...and ").append(totalDisabled - MAX_DISABLED_SHOWN).append(" more");
                }
            }

            // 流程任务
            var data = maid.getPersistentData();
            String task = data.getString("lma_flow_task");
            if (!task.isEmpty()) {
                String state = data.getString("lma_flow_state");
                int step = data.getInt("lma_flow_step");
                sb.append(" | Flow task: ").append(task)
                  .append(" (").append(state).append(", step ").append(step).append(")");
            }

            // ★ v12.5: 任务完成通知 — 读取后清除
            String completed = data.getString("lma_task_completed");
            if (!completed.isEmpty()) {
                sb.append(" | TASK COMPLETED: ").append(completed)
                  .append(". Tell the owner the task is done!");
                data.remove("lma_task_completed");
            }

            return sb.toString();
        }

        /** id=name 的紧凑摘要 */
        private static String buildRuleSummary(List<RuleDef> rules) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rules.size(); i++) {
                if (i > 0) sb.append(LINE_SEPARATOR);
                RuleDef r = rules.get(i);
                sb.append("#").append(r.id()).append(" ").append(r.name());
            }
            return sb.toString();
        }
    }
}

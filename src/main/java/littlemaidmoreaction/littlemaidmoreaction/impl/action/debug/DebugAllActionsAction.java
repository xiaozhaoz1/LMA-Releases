package littlemaidmoreaction.littlemaidmoreaction.impl.action.debug;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.*;

/**
 * DEBUG 动作 — 一次性执行所有已注册动作并输出日志。
 *
 * <p>对每个动作提供安全参数重写 (伤害→0, 目标→自身)，
 * 执行后输出 OK/FAIL/SKIPPED 日志。
 *
 * <h3>跳过列表</h3>
 * 以下高危动作直接跳过：
 * <ul>
 *   <li>{@code execute_command} — 可执行任意命令</li>
 *   <li>{@code explosion} — 可破坏地形</li>
 *   <li>{@code execution_kill} — 必定击杀</li>
 *   <li>{@code spawn_entity} — 可生成任意实体</li>
 *   <li>{@code summon_lightning} — 召唤雷电</li>
 *   <li>{@code debug_all_actions} — 避免自递归</li>
 * </ul>
 */
@RuleAction
public final class DebugAllActionsAction implements IAction {

    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.BoolParam("verbose", "详细模式", false)
    );

    /** 跳过的高危动作 */
    private static final Set<String> SKIP = Set.of(
        "execute_command", "explosion", "execution_kill",
        "spawn_entity", "summon_lightning", "debug_all_actions"
    );

    /** 安全参数 — 覆盖默认值以消除副作用 */
    private static final Map<String, String> SAFE = Map.of(
        "amount", "0.0",       // 零伤害
        "damage", "0.0",       // 零伤害
        "target", "self",      // 目标→自身
        "horizontal", "0.0",   // 零击退
        "vertical", "0.0",     // 零击飞
        "duration", "1",       // 最小持续时间
        "amplifier", "0"       // 零药水等级
    );

    @Override public String id() { return "debug_all_actions"; }
    @Override public String displayName() { return "DEBUG: 测试全部动作"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> rawParams) {
        boolean verbose = "true".equalsIgnoreCase(rawParams.get("verbose"));
        Collection<IAction> all = ActionRegistry.getAll();
        int ok = 0, fail = 0, skip = 0;

        for (IAction action : all) {
            if (SKIP.contains(action.id())) {
                if (verbose) {
                    LittleMaidMoreAction.LOGGER.debug("[LMA DEBUG] action={} SKIPPED", action.id());
                }
                skip++;
                continue;
            }

            try {
                // 构建安全参数: 默认值 + SAFE 覆盖
                Map<String, String> safeParams = buildSafeParams(action);
                action.execute(ctx, safeParams);
                if (verbose) {
                    LittleMaidMoreAction.LOGGER.debug("[LMA DEBUG] action={} OK", action.id());
                }
                ok++;
            } catch (Exception e) {
                LittleMaidMoreAction.LOGGER.warn("[LMA DEBUG] action={} FAIL: {}",
                    action.id(), e.toString());
                fail++;
            }
        }

        LittleMaidMoreAction.LOGGER.info("[LMA DEBUG] actions done: {} OK, {} FAIL, {} SKIP",
            ok, fail, skip);
    }

    private Map<String, String> buildSafeParams(IAction action) {
        Map<String, String> params = new LinkedHashMap<>();
        for (TypedParam<?> p : action.params()) {
            params.put(p.name(), String.valueOf(p.defaultValue()));
        }
        params.putAll(SAFE);  // 安全覆盖
        return params;
    }
}

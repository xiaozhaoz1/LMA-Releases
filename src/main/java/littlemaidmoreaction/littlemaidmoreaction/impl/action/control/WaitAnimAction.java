package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.MoreActionAPI;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.engine.TickScheduler;

import java.util.List;
import java.util.Map;

/**
 * 等待动画完成 — v7: 适配 INSTANT/FULL 双模式。
 *
 * <p><b>INSTANT 模式</b>：读取上一 {@code play_anim} 的动画时长
 * （{@link MoreActionAPI#getAnimationDuration(String)}，GeckoLibCache 精确时长）。
 *
 * <p><b>FULL 模式</b>：等待 START→CASTING→END 三阶段全部完成。
 * 总 tick = {@code lmma_dur_start + lmma_dur_casting + lmma_dur_end}
 * （从 PersistentData 读取用户配置的阶段时长，缺省每阶段 20 tick）。
 *
 * <p>动画名优先级（仅 INSTANT 模式）：
 * <ol>
 *   <li>参数 {@code anim_name}（显式指定）</li>
 *   <li>{@code ctx.getAttribute("last_anim")}（上一 {@code play_anim} 自动存储）</li>
 * </ol>
 *
 * <p>示例 JSON：
 * <pre>{@code
 * {"type": "play_anim", "params": {"mode": "INSTANT", "anim": "execution"}},
 * {"type": "wait_anim"}
 * }</pre>
 */
@RuleAction
public final class WaitAnimAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("anim_name", "动画名", "")
    );

    @Override public String id() { return "wait_anim"; }
    @Override public String displayName() { return "等待动画完成"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isAsync() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> rawParams) {
        var data = ctx.maid().getPersistentData();
        String mode = data.getString("lma_anim_mode");
        int ticks;

        if ("FULL".equals(mode)) {
            // FULL 模式: 只等 START+CASTING，END 阶段与后续动作并行
            int ds = getPositive(data.getInt("lma_dur_start"), 20);
            int dc = getPositive(data.getInt("lma_dur_casting"), 20);
            int de = getPositive(data.getInt("lma_dur_end"), 20);
            ticks = ds + dc; // END 不等待 — 伤害与收尾动画并行
            LittleMaidMoreAction.LOGGER.debug("[WaitAnim] FULL 等待 {}tick (START={}+CASTING={}) END={}(并行)",
                    ticks, ds, dc, de);
        } else {
            // INSTANT 模式 (及无模式时的兼容回退): 读取动画时长
            String animName = rawParams.getOrDefault("anim_name", "");
            if (animName.isEmpty()) {
                animName = ctx.getAttribute("last_anim", "");
            }
            ticks = MoreActionAPI.getAnimationDuration(animName);
            if (ticks <= 0) ticks = 40;
            LittleMaidMoreAction.LOGGER.debug("[WaitAnim] INSTANT 等待动画'{}'完成 ({} tick)", animName, ticks);
        }

        ctx.maid().getPersistentData().putInt(TickScheduler.WAIT_KEY, ticks);
    }

    /** 正数保护，非正则用默认值 */
    private static int getPositive(int v, int def) {
        return v > 0 ? v : def;
    }
}

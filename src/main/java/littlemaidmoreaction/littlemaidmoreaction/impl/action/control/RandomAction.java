package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 概率门 — 以指定概率决定是否跳过后续动作。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code chance} — 通过概率 0.0~1.0（默认 0.5）</li>
 *   <li>{@code skip} — 未通过时跳过后续动作组数（默认 1）</li>
 * </ul>
 *
 * <p>流程控制语义：作为 {@code isFlowControl} 动作被 GroupBuilder 识别，
 * Pipeline 生成 0~1 随机数，若 &gt; chance 则跳过后续 skip 个并行组。
 *
 * <p>示例 JSON — 闪避后 30% 概率播放嘲讽：
 * <pre>{@code
 * {"type": "play_anim", "params": {"anim_name": "animation.flash1"}},
 * {"type": "wait_anim"},
 * {"type": "random", "params": {"chance": "0.3", "skip": "2"}},
 * {"type": "play_anim", "params": {"anim_name": "animation.Mock1"}},
 * {"type": "wait_anim"}
 * }</pre>
 */
@RuleAction
public final class RandomAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.DoubleParam("chance", "概率", 0.5),
        new TypedParam.IntParam("skip", "跳过步数", 1)
    );
    @Override public String id() { return "random"; }
    @Override public String displayName() { return "概率分支"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> params) {}
}

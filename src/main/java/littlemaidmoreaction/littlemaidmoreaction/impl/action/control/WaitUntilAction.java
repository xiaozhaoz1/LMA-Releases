package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 等待条件满足 (v11 P1) — 挂起管道直到指定条件为 true。
 *
 * <p>写入 PersistentData key 供 TickScheduler 每 tick 评估。
 * 条件满足后移除 key，执行 resumeFrom 恢复管道。
 * 与 wait 动作不同: wait 是固定 tick 数，wait_until 是条件驱动。
 */
@RuleAction
public final class WaitUntilAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("condition_key", "条件key", "block_below"),
        new TypedParam.StringParam("condition_val", "期望值", "minecraft:stone"),
        new TypedParam.IntParam("timeout", "超时tick", 200),  // 最多等10秒
        new TypedParam.BoolParam("cancel_on_timeout", "超时取消", false)
    );

    @Override public String id() { return "wait_until"; }
    @Override public String displayName() { return "等待条件"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isAsync() { return true; }  // 挂起管道, 由 TickScheduler 恢复
    @Override public boolean isGameStateMutating() { return false; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return;

        String condKey = rawParams.getOrDefault("condition_key", "block_below");
        String condVal = rawParams.getOrDefault("condition_val", "minecraft:stone");
        int timeout = parseInt(rawParams.get("timeout"), 200);

        // 写入条件标记 — TickScheduler 检查此 key 即进入条件评估模式
        var data = maid.getPersistentData();
        data.putString("lma_wait_until_cond", condKey);
        data.putString("lma_wait_until_val", condVal);
        data.putInt("lma_wait_until_timeout", timeout);
        data.putLong("lma_wait_until_start", maid.level().getGameTime());
        // Pipeline 会将本 group 标记为 async，自动挂起并调用 TickScheduler.schedule()
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}

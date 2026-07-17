package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.envsense.EnvSenseScheduler;
import littlemaidmoreaction.littlemaidmoreaction.api.envsense.EnvSnapshot;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 环境感知命中条件 (v37) — O(1) 读 EnvSense 快照缓存，自身零扫描。
 *
 * <p>配合 lma_env_scan 事件使用：感知器每 200 tick 扫描一次，
 * 本条件只查最近一次快照中指定感知器是否命中。
 */
@RuleCondition
public final class EnvSensorCondition implements ICondition {

    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("sensor_id", "感知器ID", "")
    );

    @Override public String key() { return "env_sensor_hit"; }
    @Override public String displayName() { return "环境感知命中"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        String sensorId = rawParams.getOrDefault("sensor_id", "");
        if (sensorId.isEmpty()) return "false";
        EnvSnapshot snapshot = EnvSenseScheduler.getSnapshot(ctx.maid());
        return String.valueOf(snapshot != null && snapshot.hit(sensorId));
    }
}

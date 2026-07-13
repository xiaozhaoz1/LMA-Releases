package littlemaidmoreaction.littlemaidmoreaction.compat.ysm;

import littlemaidmoreaction.littlemaidmoreaction.core.model.MatchMode;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

import java.util.List;

/** YSM 兼容预设规则模板工厂。 */
public final class YsmPresets {

    public static List<RuleDef> createDefaults() {
        return List.of(
            RuleDef.full(200, "YSM-变身", "maid_interact",
                    1.0, 0, 50, MatchMode.ALL,
                    List.of(new ConditionDef("owner_holding_item", ":=:", "minecraft:nether_star")),
                    List.of(ActionStep.of("cancel_event"),
                            ActionStep.of("set_ysm_model", "mode", "ysm女仆模型")),
                    List.of("ysm")),
            RuleDef.full(201, "YSM-轮盘", "maid_interact",
                    1.0, 100, 50, MatchMode.ALL,
                    List.of(new ConditionDef("owner_holding_item", ":=:", "minecraft:feather")),
                    List.of(ActionStep.of("cancel_event"),
                            ActionStep.of("play_ysm_roulette", "anim_name", "default")),
                    List.of("ysm"))
        );
    }

    private YsmPresets() {}
}

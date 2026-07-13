package littlemaidmoreaction.littlemaidmoreaction.impl.altar;

import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.model.MatchMode;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

import java.util.List;

/**
 * 祭坛合成预设 (v11 — 从 compat 迁移到 impl)。
 * TLM 是 LMA 的必需依赖，非可选兼容模块。
 */
public final class AltarPresets {

    /** 预设-祭坛合成煤炭块 (ID=300): 6个煤炭 → 1个煤炭块。 */
    public static List<RuleDef> createDefaults() {
        return List.of(
            RuleDef.full(300,
                "祭坛-合成煤炭块",
                "maid_tick",
                1.0,
                1200,
                40,
                MatchMode.ALL,
                List.of(new ConditionDef("altar_nearby")),
                List.of(ActionStep.of("place_altar_item",
                    "item_id", "minecraft:coal",
                    "range", "10")),
                List.of("altar")
            )
        );
    }

    private AltarPresets() {}
}

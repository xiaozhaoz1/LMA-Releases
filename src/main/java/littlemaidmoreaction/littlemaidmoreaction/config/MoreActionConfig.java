package littlemaidmoreaction.littlemaidmoreaction.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组 Forge 配置文件。
 *
 * 战斗参数已迁移至规则引擎 JSON 预设（RuleActionStorage.createDefaultRules），
 * 此处仅保留规则引擎总开关和调试模式。
 */
public final class MoreActionConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CUSTOM_RULES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("custom_rules");
        CUSTOM_RULES_ENABLED = b
                .comment("规则引擎总开关。关闭后所有预设及自定义规则均不触发")
                .define("enabled", true);
        b.pop();

        b.push("debug");
        DEBUG_MODE = b
                .comment("调试模式：日志 + 聊天栏输出")
                .define("debug_mode", false);
        b.pop();

        SPEC = b.build();
    }
}

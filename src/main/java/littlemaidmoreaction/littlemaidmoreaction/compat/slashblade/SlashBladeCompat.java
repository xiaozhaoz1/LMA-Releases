package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.CompatScanner;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;
import net.minecraftforge.fml.ModList;

/**
 * 拔刀剑 (SlashBlade) 兼容门控。
 *
 * <h3>安全策略</h3>
 * <p>不直接引用 SlashBlade 类（防止 NoClassDefFoundError）。
 * 条件通过 {@code ForgeRegistries.ITEMS.getKey()} 检查 registry namespace。
 * 动作通过 TLM 的 {@code TaskAttack} + 物品 ID 匹配实现。</p>
 *
 * <h3>激活条件</h3>
 * <p>仅在 {@code slashblade} 模组加载时由 {@code CompatRegistry} 调用。</p>
 *
 * <h3>提供内容</h3>
 * <ul>
 *   <li><b>条件</b>: is_holding_katana (BOOL)</li>
 *   <li><b>预设</b>: 拔刀处决 (ID 200), 拔刀闪避 (ID 201)</li>
 * </ul>
 */
public final class SlashBladeCompat {
    private static final String MOD_ID = "slashblade";
    private static boolean INSTALLED = false;

    public static void init() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        INSTALLED = true;

        LittleMaidMoreAction.LOGGER.info("[SlashBlade] detected, scanning compat/slashblade/impl/...");

        CompatScanner.ScanResult r = CompatScanner.scan(SlashBladeCompat.class,
                "littlemaidmoreaction/littlemaidmoreaction/compat/slashblade/impl/condition/",
                "littlemaidmoreaction/littlemaidmoreaction/compat/slashblade/impl/action/",
                null);

        SlashbladeEventAdapter.register();

        LittleMaidMoreAction.LOGGER.info("[SlashBlade] init done: +{}c +{}a +events",
                r.conditions(), r.actions());
    }

    public static boolean isInstalled() { return INSTALLED; }

    private SlashBladeCompat() {}
}

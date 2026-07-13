package littlemaidmoreaction.littlemaidmoreaction.compat.tpm;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.CompatScanner;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;
import net.minecraftforge.fml.ModList;

/**
 * True Power of Maid (TPM) 兼容门控。
 *
 * <h3>前置</h3>
 * <ul><li>SlashBlade Resharped</li><li>mrqx SlashBlade Core</li><li>True POWER</li></ul>
 */
public final class TpmCompat {
    private static final String MOD_ID = "true_power_of_maid";
    private static boolean INSTALLED = false;

    public static void init() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        INSTALLED = true;

        LittleMaidMoreAction.LOGGER.info("[TPM] detected, scanning compat/tpm/impl/...");

        CompatScanner.ScanResult r = CompatScanner.scan(TpmCompat.class,
                "littlemaidmoreaction/littlemaidmoreaction/compat/tpm/impl/condition/",
                "littlemaidmoreaction/littlemaidmoreaction/compat/tpm/impl/action/",
                null);

        RuleActionStorage.ensureCompatDefaults(TpmPresets.createDefaults());

        LittleMaidMoreAction.LOGGER.info("[TPM] init done: +{}c +{}a +{} presets",
                r.conditions(), r.actions(), TpmPresets.createDefaults().size());
    }

    public static boolean isInstalled() { return INSTALLED; }
    private TpmCompat() {}
}

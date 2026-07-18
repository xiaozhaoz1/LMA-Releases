package littlemaidmoreaction.littlemaidmoreaction.compat.create;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.CompatScanner;
import net.minecraftforge.fml.ModList;

/**
 * Create 机械动力兼容门控。
 *
 * <p>提供女仆搬运任务 (木棍标记容器 + 两状态 TAKE↔DEPOSIT)，
 * 仅在 {@code create} 模组加载时激活。
 * 不拦截 Create 机械臂任何事件。</p>
 */
public final class CreateCompat {
    private static final String MOD_ID = "create";
    private static boolean INSTALLED = false;

    public static void init() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        INSTALLED = true;

        LittleMaidMoreAction.LOGGER.info("[CreateCompat] Create detected, scanning compat/create/impl/...");

        CompatScanner.ScanResult r = CompatScanner.scan(CreateCompat.class,
                "littlemaidmoreaction/littlemaidmoreaction/compat/create/impl/condition/",
                "littlemaidmoreaction/littlemaidmoreaction/compat/create/impl/action/",
                "littlemaidmoreaction/littlemaidmoreaction/compat/create/impl/event/",
                net.minecraftforge.common.MinecraftForge.EVENT_BUS::register);

        LittleMaidMoreAction.LOGGER.info("[CreateCompat] init done: +{}c +{}a +{} events",
                r.conditions(), r.actions(), r.events());
    }

    public static boolean isInstalled() { return INSTALLED; }

    private CreateCompat() {}
}

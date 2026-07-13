package littlemaidmoreaction.littlemaidmoreaction.compat.ysm;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.CompatScanner;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;

import net.minecraftforge.fml.ModList;

/**
 * YSM (Yes Steve Model) 兼容门控。
 *
 * <p>参考 TLM {@code compat/ysm/YsmCompat} 的版本检查 + INSTALLED 标志模式。
 * 由 {@code CompatRegistry.onEnqueue()} 在 {@code InterModEnqueueEvent}
 * 阶段调用，仅在 {@code yes_steve_model} 加载时执行。</p>
 *
 * <p>扫描 {@code compat/ysm/impl/} 下的条件/动作/事件类并自动注册。</p>
 */
public final class YsmCompat {
    private static final String MOD_ID = "yes_steve_model";

    /** 与 TLM 一致：YsmCompat.isInstalled() 供外部查询 */
    private static boolean INSTALLED = false;

    // ─── 入口 ──────────────────────────────────────────────────

    /**
     * 由 {@code CompatRegistry} 在 YSM 加载时调用。
     *
     * <ol>
     *   <li>版本检查 → 设置 {@code INSTALLED} 标志</li>
     *   <li>委托 {@link CompatScanner} 扫描条件/动作/事件</li>
     *   <li>注册 {@link YsmEvent} 到 Forge 事件总线</li>
     * </ol>
     */
    public static void init() {
        ModList.get().getModContainerById(MOD_ID).ifPresent(mod -> INSTALLED = true);
        if (!INSTALLED) return;

        LittleMaidMoreAction.LOGGER.info("[YsmCompat] YSM detected, scanning compat/ysm/impl/...");

        CompatScanner.ScanResult r = CompatScanner.scan(YsmCompat.class,
                "littlemaidmoreaction/littlemaidmoreaction/compat/ysm/impl/condition/",
                "littlemaidmoreaction/littlemaidmoreaction/compat/ysm/impl/action/",
                "littlemaidmoreaction/littlemaidmoreaction/compat/ysm/impl/event/");

        LittleMaidMoreAction.LOGGER.info("[YsmCompat] init done: +{} conditions +{} actions +{} events",
                r.conditions(), r.actions(), r.events());

        // 确保 YSM 专属预设规则文件存在
        RuleActionStorage.ensureCompatDefaults(YsmPresets.createDefaults());
    }

    public static boolean isInstalled() { return INSTALLED; }

    private YsmCompat() {}
}

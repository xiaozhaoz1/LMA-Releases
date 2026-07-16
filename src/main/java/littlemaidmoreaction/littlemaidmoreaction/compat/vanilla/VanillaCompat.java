package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.maideditor.BuiltinMaidEditorRegistration;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.CompatScanner;

/**
 * 原版功能兼容模块 — 注册 compat/vanilla/ 下的条件/动作类。
 *
 * <p>v12.3 的 5 个功能方块互动动作（crafting/furnace/brewing/jukebox/bell）
 * 位于 {@code compat/vanilla/interact/}，
 * 不在 {@code ForgeClassScanner} 的 {@code impl/} 扫描路径内，
 * 且 ClassGraph 回退仅在注册表为空时触发。
 * 此模块在 {@code CompatRegistry} 中无条件初始化，确保注册。</p>
 *
 * <p>与 {@code FlowTaskCompat} 模式一致 — 原生功能，无需门控。</p>
 */
public final class VanillaCompat {

    private VanillaCompat() {}

    public static void init() {
        BuiltinMaidEditorRegistration.init();
        CompatScanner.ScanResult r = CompatScanner.scan(VanillaCompat.class,
            "littlemaidmoreaction/littlemaidmoreaction/compat/vanilla/input/detect/",
            "littlemaidmoreaction/littlemaidmoreaction/compat/vanilla/interact/",
            null, null // no events
        );

        LittleMaidMoreAction.LOGGER.info(
            "[VanillaCompat] registered +{} conditions +{} actions",
            r.conditions(), r.actions());
    }
}

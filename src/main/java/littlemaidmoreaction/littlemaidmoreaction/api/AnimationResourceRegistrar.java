package littlemaidmoreaction.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 动画资源注册器 — 自定义动画扫描、TLM 注册。
 *
 * <p>从 MoreActionAPI 拆分 (v7)。
 * v7.1: 移除旧 AnimationState 注册（已由 MagicCasting Provider 替代）。</p>
 */
public final class AnimationResourceRegistrar {

    /** FORGE 总线：扫描 config/animations/ 目录，注册 .animation.json 到 TLM */
    public static void registerCustomAnimations(DefaultGeckoAnimationEvent event) {
        for (String file : littlemaidmoreaction.littlemaidmoreaction.storage.StartupLoader.getAnimationFiles()) {
            registerAnimation(event,
                    ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "animations/" + file));
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/Registrar] 注册 {} 个动画到 TLM",
                littlemaidmoreaction.littlemaidmoreaction.storage.StartupLoader.getAnimationFiles().size());
    }

    @OnlyIn(Dist.CLIENT)
    public static void scanCustomAnimations() {
        LittleMaidMoreAction.LOGGER.info("[LMA/Registrar] 动画就绪 ({} 文件)",
                littlemaidmoreaction.littlemaidmoreaction.storage.StartupLoader.getAnimationFiles().size());
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerAnimation(DefaultGeckoAnimationEvent event, ResourceLocation path) {
        event.addAnimation(DefaultGeckoAnimationEvent.AnimationType.ISS, path);
        if (MoreActionConfig.DEBUG_MODE.get()) {
            LittleMaidMoreAction.LOGGER.debug("[LMA/Registrar] 注册动画资源: {}", path);
        }
    }
}

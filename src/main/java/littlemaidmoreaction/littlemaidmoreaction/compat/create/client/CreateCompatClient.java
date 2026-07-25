package littlemaidmoreaction.littlemaidmoreaction.compat.create.client;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyScreen;
import littlemaidmoreaction.littlemaidmoreaction.init.LmaBlockEntityTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Create Compat 客户端注册 (v4.2 + v56).
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreateCompatClient {

    private CreateCompatClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                LmaBlockEntityTypes.MAID_POWER_BELT.get(),
                MaidPowerBeltRenderer::new);
    }

    /** v56: 便携装配 Screen 绑定 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("create")) {
            event.enqueueWork(() -> MenuScreens.register(
                LittleMaidMoreAction.MAID_ASSEMBLY_MENU.get(), MaidAssemblyScreen::new));
        }
    }
}

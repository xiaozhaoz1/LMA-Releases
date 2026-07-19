package littlemaidmoreaction.littlemaidmoreaction.compat.create.client;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.init.LmaBlockEntityTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Create Compat 客户端注册 (v4.2)。
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
}

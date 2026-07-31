package littlemaidmoreaction.littlemaidmoreaction.client;

import com.mojang.blaze3d.platform.InputConstants;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.network.InteractTriggerPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * block_interact 手动触发按键 — 默认数字键 0。
 *
 * <p>注册后自动出现在 MC「选项→控制」界面，支持重新绑定。
 * 按键时发送 {@link InteractTriggerPacket} 到服务端触发周围女仆交互。
 */
@OnlyIn(Dist.CLIENT)
public final class BlockInteractKeyMapping {

    public static final KeyMapping TRIGGER_KEY = new KeyMapping(
        "key.lma.block_interact_trigger",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_0,
        "key.categories.lma"
    );

    private BlockInteractKeyMapping() {}

    // ── MOD bus: 注册 KeyMapping ──

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = LittleMaidMoreAction.MOD_ID)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TRIGGER_KEY);
        }
    }

    // ── FORGE bus: 按键检测 ──

    @Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static final class ForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (!isInGame()) return;
            if (TRIGGER_KEY.consumeClick()) {
                InteractTriggerPacket.sendToServer();
            }
        }

        private static boolean isInGame() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getOverlay() != null) return false;
            if (mc.screen != null) return false;
            if (!mc.mouseHandler.isMouseGrabbed()) return false;
            return mc.isWindowActive();
        }
    }
}

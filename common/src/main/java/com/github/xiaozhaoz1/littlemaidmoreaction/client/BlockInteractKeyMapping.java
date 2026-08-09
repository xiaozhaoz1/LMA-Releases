package com.github.xiaozhaoz1.littlemaidmoreaction.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.InteractTriggerPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
//?} else {
import net.neoforged.api.distmarker.Dist;
//?}
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.OnlyIn;
//?}
//? if 1.20.1 {
import net.minecraftforge.client.event.InputEvent;
//?} else {
import net.neoforged.neoforge.client.event.InputEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//?} else {
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.client.settings.KeyConflictContext;
//?} else {
import net.neoforged.neoforge.client.settings.KeyConflictContext;
//?}
//? if 1.20.1 {
import net.minecraftforge.client.settings.KeyModifier;
//?} else {
import net.neoforged.neoforge.client.settings.KeyModifier;
//?}
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}
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

//? if 1.20.1 {
    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = LittleMaidMoreAction.MOD_ID)
//?} else {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = LittleMaidMoreAction.MOD_ID)
//?}
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TRIGGER_KEY);
        }
    }

    // ── FORGE bus: 按键检测 ──

//? if 1.20.1 {
    @Mod.EventBusSubscriber(value = Dist.CLIENT)
//?} else {
    @EventBusSubscriber(value = Dist.CLIENT)
//?}
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

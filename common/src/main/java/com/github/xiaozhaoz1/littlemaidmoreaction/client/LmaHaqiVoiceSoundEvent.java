package com.github.xiaozhaoz1.littlemaidmoreaction.client;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.mojang.blaze3d.audio.SoundBuffer;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

/**
 * 对主人哈气语音注入监听 (v79.20) — 仿 TLM {@code PlayMaidSoundEvent} 模式:
 * {@code PlaySoundSourceEvent} 时把真实 ogg 缓冲 attach 到已创建的播放通道。
 *
 * <p><b>仅 1.20 (forge) 需要</b>: TLM 1.20 只认自家 {@code MaidSoundInstance},
 * LMA 自挂监听注入; 1.21 (neoforge) 免 — TLM 1.21 监听 {@code instanceof ICustomSoundBuffer},
 * 本 mod 的 {@link PecoHaqiSoundPlayer.LmaHaqiPecoSoundInstance} implements 该接口即免费接入
 * (TLM PlayMaidSoundEvent 1.21 L16-20 实证)。
 */
@OnlyIn(Dist.CLIENT)
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmaHaqiVoiceSoundEvent {

    private LmaHaqiVoiceSoundEvent() {}

    @SubscribeEvent
    public static void onPlaySoundSource(PlaySoundSourceEvent event) {
        if (event.getSound() instanceof PecoHaqiSoundPlayer.LmaHaqiPecoSoundInstance instance) {
            SoundBuffer soundBuffer = instance.getSoundBuffer();
            if (soundBuffer != null) {
                event.getChannel().attachStaticBuffer(soundBuffer);
                event.getChannel().play();
            }
        }
    }
}
//?} else {
public final class LmaHaqiVoiceSoundEvent {

    private LmaHaqiVoiceSoundEvent() {}
}
//?}

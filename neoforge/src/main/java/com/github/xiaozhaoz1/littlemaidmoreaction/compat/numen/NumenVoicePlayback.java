package com.github.xiaozhaoz1.littlemaidmoreaction.compat.numen;

import com.github.tartaricacid.touhoulittlemaid.api.client.sound.ICustomSoundBuffer;
import com.github.tartaricacid.touhoulittlemaid.client.sound.CustomSoundLoader;
import com.github.tartaricacid.touhoulittlemaid.client.sound.data.SoundCache;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.LmaMaidVoicePayload;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 假人语音播放 (v75.3, 客户端) — 从 TLM 女仆语音包取音频, 在假人位置播放。
 *
 * <p>TLM 链路 (PlayMaidSoundPackage → MaidSoundInstance) 客户端只认 EntityMaid —
 * 假人直接 return。此处自建播放器 (完全仿 TLM MaidSoundInstance):
 * implements {@link ICustomSoundBuffer} (TLM mixin 拦截 SoundEngine 播放自定义 buffer),
 * 绑定假人客户端实体 (AbstractClientPlayer 镜像) 坐标跟随。
 */
public final class NumenVoicePlayback {

    private NumenVoicePlayback() {}

    public static void play(LmaMaidVoicePayload msg) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            // 1.21: Level.getEntities() protected — 假人是玩家, 用 players() 遍历
            AbstractClientPlayer companion = null;
            for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
                if (p.getUUID().equals(msg.companionUuid())) { companion = (AbstractClientPlayer) p; break; }
            }
            if (companion == null) return;
            SoundManager manager = mc.getSoundManager();
            manager.play(new CompanionVoiceInstance(companion, msg.soundPackId(), msg.soundEvent()));
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.warn("[NumenVoice] 播放失败: {}", ex.toString());
        }
    }

    /** 仿 TLM MaidSoundInstance — 绑定假人坐标跟随; getSoundBuffer 每次从语音包取 (TLM mixin 播放) */
    private static final class CompanionVoiceInstance extends AbstractTickableSoundInstance implements ICustomSoundBuffer {
        private final AbstractClientPlayer companion;
        private final String soundPackId;
        private final ResourceLocation soundEvent;

        CompanionVoiceInstance(AbstractClientPlayer companion, String soundPackId, ResourceLocation soundEvent) {
            super(SoundEvent.createVariableRangeEvent(soundEvent), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.companion = companion;
            this.soundPackId = soundPackId;
            this.soundEvent = soundEvent;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            Vec3 pos = companion.position();
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.looping = false;
        }

        @Override
        public boolean canPlaySound() {
            return !companion.isSilent();
        }

        @Override
        public void tick() {
            if (companion.isRemoved()) {
                this.stop();
            } else {
                Vec3 pos = companion.position();
                this.x = pos.x;
                this.y = pos.y;
                this.z = pos.z;
            }
        }

        @Nullable
        @Override
        public SoundBuffer getSoundBuffer() {
            SoundCache cache = CustomSoundLoader.getSoundCache(soundPackId);
            if (cache != null) {
                return cache.getBuffer(soundEvent);
            }
            return null;
        }
    }
}

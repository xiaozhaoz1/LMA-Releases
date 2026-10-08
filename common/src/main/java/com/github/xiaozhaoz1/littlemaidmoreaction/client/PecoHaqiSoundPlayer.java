package com.github.xiaozhaoz1.littlemaidmoreaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
//? if !1.20.1 {
import com.github.tartaricacid.touhoulittlemaid.api.client.sound.ICustomSoundBuffer;
//?}
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

import javax.annotation.Nullable;

/**
 * 对主人哈气语音播放器 (v79.20, 客户端) — 仿 TLM {@code MaidSoundInstance} 全文模式
 * (坐标跟随女仆 + canPlaySound 静默判定 + tick 移除即停), 缓冲来自
 * {@link PecoHaqiSubsetLoader} (peco 包 11 文件子集随机)。
 *
 * <p>注入方式双平台差异:
 * <ul>
 *   <li>1.21 (neoforge): {@code implements ICustomSoundBuffer} — TLM 的 PlayMaidSoundEvent
 *       监听 {@code instanceof ICustomSoundBuffer} 自动注入 (免自挂监听)</li>
 *   <li>1.20 (forge): TLM 只认自家 MaidSoundInstance — LMA 自挂监听
 *       {@link LmaHaqiVoiceSoundEvent} 注入</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class PecoHaqiSoundPlayer {

    private PecoHaqiSoundPlayer() {}

    /** 播放一次对主人哈气语音 (peco 子集随机) */
    public static void play(EntityMaid maid, float volume) {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        manager.play(new LmaHaqiPecoSoundInstance(maid, volume));
    }

    /**
     * 自定义声音实例 — 占位事件用 TLM maid.mode.idle (已注册且有定义,
     * 真实缓冲由 getSoundBuffer 注入覆盖; 不注册的 RL SoundEngine 会拒播)。
     * 音量走父类 {@code AbstractSoundInstance.volume} 字段 (clamp 0-2 对齐配置)。
     */
//? if 1.20.1 {
    public static final class LmaHaqiPecoSoundInstance extends AbstractTickableSoundInstance {
//?} else {
    public static final class LmaHaqiPecoSoundInstance extends AbstractTickableSoundInstance implements ICustomSoundBuffer {
//?}
        private final EntityMaid maid;

        public LmaHaqiPecoSoundInstance(EntityMaid maid, float volume) {
            super(InitSounds.MAID_IDLE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.maid = maid;
            this.x = maid.getX();
            this.y = maid.getY();
            this.z = maid.getZ();
            this.volume = Math.max(0.0F, Math.min(volume, 2.0F));
        }

        @Override
        public boolean canPlaySound() {
            return !this.maid.isSilent();
        }

        @Override
        public void tick() {
            if (this.maid.isRemoved()) {
                this.stop();
            } else {
                this.x = this.maid.getX();
                this.y = this.maid.getY();
                this.z = this.maid.getZ();
            }
        }

        public EntityMaid getMaid() {
            return maid;
        }

        @Nullable
//? if !1.20.1 {
        @Override
//?}
        public SoundBuffer getSoundBuffer() {
            return PecoHaqiSubsetLoader.randomBuffer();
        }
    }
}

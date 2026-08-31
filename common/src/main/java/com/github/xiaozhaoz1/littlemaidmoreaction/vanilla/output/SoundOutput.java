package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 声音输出原语 (v79.6x 新建 — B5 收编 HaqiService.resolveSound/CraftService/BellRingPipeline 内联播放)。
 * 防御点: resolve 未注册事件 → null (调用方 null 守卫不播); 播放参数透传不吞。
 */
public final class SoundOutput {

    private SoundOutput() {}

    /** 实体发声 (entity.playSound 转发) */
    public static void playEntity(Entity entity, SoundEvent event, float volume, float pitch) {
        entity.playSound(event, volume, pitch);
    }

    /** 位置发声 (world.playSound(null, pos, ...) 转发 — 原 CraftService/BellRingPipeline 内联) */
    public static void playAt(Level level, BlockPos pos, SoundEvent event, SoundSource source,
                              float volume, float pitch) {
        level.playSound(null, pos, event, source, volume, pitch);
    }

    /** 双平台注册表查 SoundEvent (1.20.1 ForgeRegistries.SOUND_EVENTS / 1.21.1 BuiltInRegistries.SOUND_EVENT) */
    @Nullable
    public static SoundEvent resolve(String modId, String name) {
        ResourceLocation rl;
 //? if 1.20.1 {
        rl = new ResourceLocation(modId, name);
 //?} else {
        rl = ResourceLocation.fromNamespaceAndPath(modId, name);
 //?}
 //? if 1.20.1 {
        return net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(rl);
 //?} else {
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(rl);
 //?}
    }

    /** mod 声音按名播放 (解析失败静默跳过 — 原 HaqiService.playSound 语义) */
    public static void playModSound(Entity entity, String modId, String name, float volume) {
        SoundEvent event = resolve(modId, name);
        if (event != null) playEntity(entity, event, volume, 1.0F);
    }
}

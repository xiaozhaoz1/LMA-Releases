package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
//? if 1.20.1 {
import net.minecraftforge.registries.DeferredRegister;
//?} else {
import net.neoforged.neoforge.registries.DeferredRegister;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

/**
 * LMA 内置音效注册 — 对标 TLM InitSounds (v10)。
 *
 * <p>注册 JAR 内置音效: man/manbaout/whatcanisay (v10) + 哈气 10 个 (v79.9: ha_1..5/laowu_1..5)。
 * 用户自定义音效请使用资源包。</p>
 *
 * <p>⚠ 命名禁 "maid*" 前缀 — TLM EntityMaid.playSound 对 maid 前缀走音效包路线 (TLM 源码实证)。</p>
 */
public final class LmaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
//? if 1.20.1 {
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LittleMaidMoreAction.MOD_ID);
//?} else {
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, LittleMaidMoreAction.MOD_ID);
//?}

    private static final float RANGE = 16.0F;

    /** 注册便捷 — 双平台 ResourceLocation */
    private static void reg(String name) {
//? if 1.20.1 {
        SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(
                new ResourceLocation(LittleMaidMoreAction.MOD_ID, name), RANGE));
//?} else {
        SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, name), RANGE));
//?}
    }

    static {
        reg("man");
        reg("manbaout");
        reg("whatcanisay");
        // v79.9: 哈气音频 (ha_1..5 哈气音 / laowu_1..5 老五音 — D:\claudecode\sounds 导入)
        reg("ha_1");
        reg("ha_2");
        reg("ha_3");
        reg("ha_4");
        reg("ha_5");
        reg("laowu_1");
        reg("laowu_2");
        reg("laowu_3");
        reg("laowu_4");
        reg("laowu_5");
    }

    private LmaSounds() {}
}

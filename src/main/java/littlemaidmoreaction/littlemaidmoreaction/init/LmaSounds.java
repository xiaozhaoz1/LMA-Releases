package littlemaidmoreaction.littlemaidmoreaction.init;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * LMA 内置音效注册 — 对标 TLM InitSounds (v10)。
 *
 * <p>注册 JAR 内置 3 个音效 (man/manbaout/whatcanisay)。
 * 用户自定义音效请使用资源包。</p>
 */
public final class LmaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LittleMaidMoreAction.MOD_ID);

    static {
        SOUNDS.register("man",
            () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(LittleMaidMoreAction.MOD_ID, "man"), 16.0F));
        SOUNDS.register("manbaout",
            () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(LittleMaidMoreAction.MOD_ID, "manbaout"), 16.0F));
        SOUNDS.register("whatcanisay",
            () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(LittleMaidMoreAction.MOD_ID, "whatcanisay"), 16.0F));
    }

    private LmaSounds() {}
}

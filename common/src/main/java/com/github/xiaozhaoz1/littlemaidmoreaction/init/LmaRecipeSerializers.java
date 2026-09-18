package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
//?} else {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
//?}

/**
 * LMA 配方序列化器注册点 (v79.62.3) — 自定义防御塔合成 (保留手办模型 NBT).
 *
 * <p>挂载链路: {@code init.LmaRegistrar.registerRecipeSerializers(modBus)} → forge
 * {@code LittleMaidMoreAction} / neoforge {@code LmaNeoForgeEntry} 构造器。
 */
public final class LmaRecipeSerializers {

    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> SERIALIZERS =
//? if 1.20.1 {
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, LittleMaidMoreAction.MOD_ID);
//?} else {
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, LittleMaidMoreAction.MOD_ID);
//?}

    /** v79.62.3: 防御塔手办合成 (1 手办 → 1 防御塔, 继承 EntityInfo NBT) */
    //? if 1.20.1 {
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<?>> GARAGE_KIT_DEFENSE =
            SERIALIZERS.register("garage_kit_defense_recipe",
                    () -> com.github.xiaozhaoz1.littlemaidmoreaction.defense.DefenseGarageKitRecipeSerializer.INSTANCE);
    //?} else {
    public static final Supplier<net.minecraft.world.item.crafting.RecipeSerializer<?>> GARAGE_KIT_DEFENSE =
            SERIALIZERS.register("garage_kit_defense_recipe",
                    () -> com.github.xiaozhaoz1.littlemaidmoreaction.defense.DefenseGarageKitRecipeSerializer.INSTANCE);
    //?}

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    private LmaRecipeSerializers() {}
}

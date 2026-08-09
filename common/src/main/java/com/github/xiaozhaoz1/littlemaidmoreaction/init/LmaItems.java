package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.item.Item;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.IEventBus;
//?} else {
import net.neoforged.bus.api.IEventBus;
//?}
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
//? if 1.20.1 {
import net.minecraftforge.registries.RegistryObject;
//?} else {
import java.util.function.Supplier;
//?}

/**
 * LMA 物品注册点 (v79.22 就绪) — 后续物品在此注册。
 *
 * <p>用法 (以饰品为例, 双平台条件化):
 * <pre>{@code
 * //? if 1.20.1 {
 * public static final RegistryObject<Item> MY_ITEM = ITEMS.register("my_item", Item::new);
 * //?} else {
 * public static final Supplier<Item> MY_ITEM = ITEMS.register("my_item", Item::new);
 * //?}
 * }</pre>
 *
 * <p>挂载链路: {@code init.LmaRegistrar.registerItems(modBus)} → forge
 * {@code LittleMaidMoreAction} / neoforge {@code LmaNeoForgeEntry} 构造器。
 */
public final class LmaItems {

    public static final DeferredRegister<Item> ITEMS =
//? if 1.20.1 {
            DeferredRegister.create(ForgeRegistries.ITEMS, LittleMaidMoreAction.MOD_ID);
//?} else {
            DeferredRegister.create(BuiltInRegistries.ITEM, LittleMaidMoreAction.MOD_ID);
//?}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private LmaItems() {}
}

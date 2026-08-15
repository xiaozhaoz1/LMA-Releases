package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.item.Item;
//? if 1.20.1 {
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
//?} else {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
//?}

/**
 * 酒狐奶物品注册 (v79.6x) — 两个可饮奶桶物品。
 * 挂载: {@code LittleMaidMoreAction} / {@code LmaNeoForgeEntry} 构造器调用 {@link #register}。
 */
public final class KitsuneMilkItems {

    public static final DeferredRegister<Item> ITEMS =
//? if 1.20.1 {
            DeferredRegister.create(ForgeRegistries.ITEMS, LittleMaidMoreAction.MOD_ID);
//?} else {
            DeferredRegister.create(BuiltInRegistries.ITEM, LittleMaidMoreAction.MOD_ID);
//?}

//? if 1.20.1 {
    public static final RegistryObject<Item> TAMED_MILK_BUCKET =
            ITEMS.register("tamed_milk_bucket", TamedMilkBucketItem::new);
    public static final RegistryObject<Item> WILD_DOGMILK =
            ITEMS.register("wild_dogmilk", WildMilkItem::new);
//?} else {
    public static final Supplier<Item> TAMED_MILK_BUCKET =
            ITEMS.register("tamed_milk_bucket", TamedMilkBucketItem::new);
    public static final Supplier<Item> WILD_DOGMILK =
            ITEMS.register("wild_dogmilk", WildMilkItem::new);
//?}

//? if 1.20.1 {
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
//?} else {
    public static void register(net.neoforged.bus.api.IEventBus bus) {
//?}
        ITEMS.register(bus);
    }

    private KitsuneMilkItems() {}
}

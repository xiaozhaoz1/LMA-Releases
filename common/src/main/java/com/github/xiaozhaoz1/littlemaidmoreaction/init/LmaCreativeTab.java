package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.core.registries.Registries;
//?} else {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}

/**
 * LMA 创造栏标签页 (2026-08-16 用户裁定 — 「创造栏没 LMA 栏」: LMA 物品从未注册过
 * CreativeModeTab, 图鉴书/酒狐奶桶创造栏搜不到; 本类注册 littlemaidmoreaction 标签页,
 * 挂载 LittleMaidMoreAction / LmaNeoForgeEntry 构造器 (与物品 DeferredRegister 同 bus 时序)。
 */
public final class LmaCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
//? if 1.20.1 {
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LittleMaidMoreAction.MOD_ID);
//?} else {
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, LittleMaidMoreAction.MOD_ID);
//?}

    private LmaCreativeTab() {}

//? if 1.20.1 {
    public static final RegistryObject<CreativeModeTab> LMA_TAB = TABS.register("littlemaidmoreaction",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(LmaItems.MAID_CODEX.get()))
                    .title(Component.translatable("itemGroup.littlemaidmoreaction"))
                    .displayItems((params, out) -> {
                        out.accept(new ItemStack(LmaItems.MAID_CODEX.get()));
                        out.accept(new ItemStack(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkItems.TAMED_MILK_BUCKET.get()));
                        out.accept(new ItemStack(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkItems.WILD_DOGMILK.get()));
                    })
                    .build());
//?} else {
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LMA_TAB = TABS.register("littlemaidmoreaction",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(LmaItems.MAID_CODEX.get()))
                    .title(Component.translatable("itemGroup.littlemaidmoreaction"))
                    .displayItems((params, out) -> {
                        out.accept(new ItemStack(LmaItems.MAID_CODEX.get()));
                        out.accept(new ItemStack(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkItems.TAMED_MILK_BUCKET.get()));
                        out.accept(new ItemStack(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkItems.WILD_DOGMILK.get()));
                    })
                    .build());
//?}

    //? if 1.20.1 {
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        TABS.register(bus);
    }
    //?} else {
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        TABS.register(bus);
    }
    //?}
}

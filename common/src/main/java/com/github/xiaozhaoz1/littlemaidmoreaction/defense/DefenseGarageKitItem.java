package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityItemStackGarageKitRenderer;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlocks;
import com.google.common.base.Suppliers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
//?}

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 防御塔物品 (v79.62.3) — BlockItem, 继承原版手办 NBT 语义 (EntityInfo 键, 拆放回写)。
 * 物品栏/手持渲染复用 TLM {@link TileEntityItemStackGarageKitRenderer} (手办模型 — 双平台签名一致,
 * javap 实证 1.20.1/1.21.1 均 (BlockEntityRenderDispatcher, EntityModelSet))。
 */
public class DefenseGarageKitItem extends BlockItem {

    public DefenseGarageKitItem() {
        super(LmaBlocks.GARAGE_KIT_DEFENSE.get(), new Item.Properties().stacksTo(1));
    }

    /** 默认手办实体 id (对齐 TLM DEFAULT_ENTITY_ID — javap 1.21.1 实证) */
    private static final String DEFAULT_ENTITY_ID = "touhou_little_maid:maid";
    /** 默认模型 id (对齐 TLM DEFAULT_MODEL_ID — hakurei_reimu) */
    private static final String DEFAULT_MODEL_ID = "touhou_little_maid:hakurei_reimu";
    /** 手办 NBT 键 (TLM ItemGarageKit.ENTITY_INFO 同源) */
    private static final String ENTITY_INFO = "EntityInfo";

    /** 创造栏用: 带默认手办数据的物品 (对齐 TLM BlockGarageKit.fillItemCategory —
     * 结构必须 {EntityInfo: {id, model_id, IsYsmModel}} — readEntityInfo/setData 读的就是 EntityInfo 子键,
     * 直接放平级会读不到 → 渲染器只画底座没女仆) */
    public static ItemStack creativeDefault() {
        ItemStack stack = new ItemStack(com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems.GARAGE_KIT_DEFENSE.get());
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.putString("id", DEFAULT_ENTITY_ID);
        data.putString(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.MODEL_ID_TAG, DEFAULT_MODEL_ID);
        data.putBoolean(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.IS_YSM_MODEL_TAG, false);
        net.minecraft.nbt.CompoundTag info = new net.minecraft.nbt.CompoundTag();
        info.put(ENTITY_INFO, data);
//? if 1.20.1 {
        stack.getOrCreateTag().put(ENTITY_INFO, data);
//?} else {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(info));
//?}
        return stack;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private static final Supplier<TileEntityItemStackGarageKitRenderer> MEMOIZE = Suppliers.memoize(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                return new TileEntityItemStackGarageKitRenderer(
                        minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
            });

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return MEMOIZE.get();
            }
        });
    }
}

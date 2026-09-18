package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
//? if 1.20.1 {
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.core.RegistryAccess;
//?} else {
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.CraftingInput;
//?}

/**
 * v79.62.3 防御塔手办合成 — 继承输入手办的模型 NBT (TLM 手办有几十个模型变体, 纯 JSON 配方
 * ShapelessRecipe.assemble = result.copy() 丢 NBT → 玩家拿某模型手办合成得到默认模型).
 *
 * <p>1 手办 (任意模型) → 1 防御塔手办 (同模型). assemble 把输入 EntityInfo NBT 复制到输出.
 * <p>双平台签名差异: 1.20.1 matches/assemble 用 {@link CraftingContainer} + {@link RegistryAccess},
 * 1.21.1 用 {@link CraftingInput} + {@link HolderLookup.Provider} (stonecutter 分支).
 * 序列化器: {@link DefenseGarageKitRecipeSerializer}.
 */
public class DefenseGarageKitRecipe extends CustomRecipe {

    /** 输入物品 id (TLM garage_kit 方块物品) */
    public static final String INPUT_ITEM = "touhou_little_maid:garage_kit";
    /** 输出物品 id (LMA 防御塔) */
    public static final String OUTPUT_ITEM = "littlemaidmoreaction:garage_kit_defense";
    /** 手办 NBT 键 (TLM EntityInfo — 含 model_id/id/IsYsmModel) */
    private static final String ENTITY_INFO = "EntityInfo";

//? if 1.20.1 {
    public DefenseGarageKitRecipe(net.minecraft.resources.ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return singleGarageKit(container.getContainerSize(), i -> container.getItem(i));
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        return assembleFrom(container.getContainerSize(), i -> container.getItem(i));
    }

    @Override
    public net.minecraft.world.item.ItemStack getResultItem(RegistryAccess registries) {
        return resultItem();
    }
//?} else {
    public DefenseGarageKitRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return singleGarageKit(input.size(), input::getItem);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return assembleFrom(input.size(), input::getItem);
    }

    @Override
    public net.minecraft.world.item.ItemStack getResultItem(HolderLookup.Provider registries) {
        return resultItem();
    }
//?}

    /** 合成格是否恰 1 个 garage_kit (任何 NBT/模型变体) */
    private static boolean singleGarageKit(int size, java.util.function.IntFunction<ItemStack> get) {
        int count = 0;
        ItemStack one = ItemStack.EMPTY;
        for (int i = 0; i < size; i++) {
            ItemStack s = get.apply(i);
            if (s.isEmpty()) continue;
            count++;
            one = s;
        }
        return count == 1 && isGarageKit(one);
    }

    /** 是否 TLM 手办物品 (registry id 匹配) */
    private static boolean isGarageKit(ItemStack s) {
        if (s.isEmpty()) return false;
//? if 1.20.1 {
        net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
        return key != null && key.toString().equals(INPUT_ITEM);
//?} else {
        net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
        return key != null && key.toString().equals(INPUT_ITEM);
//?}
    }

    /** 合成: 输出防御塔 + 复制输入手办 NBT (EntityInfo) */
    private static ItemStack assembleFrom(int size, java.util.function.IntFunction<ItemStack> get) {
        ItemStack source = ItemStack.EMPTY;
        for (int i = 0; i < size; i++) {
            ItemStack s = get.apply(i);
            if (!s.isEmpty() && isGarageKit(s)) { source = s; break; }
        }
        if (source.isEmpty()) return ItemStack.EMPTY;
        return buildOutput(source);
    }

    /** 公共: 输出防御塔 + 复制输入手办 NBT (EntityInfo/MAID_INFO → CUSTOM_DATA.EntityInfo) — gametest 直接验证 */
    public static ItemStack buildOutput(ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = resultItem();
//? if 1.20.1 {
        // 1.20.1: NBT 存物品 getOrCreateTag().EntityInfo
        if (source.hasTag() && source.getTag().contains(ENTITY_INFO)) {
            out.getOrCreateTag().put(ENTITY_INFO, source.getTag().getCompound(ENTITY_INFO).copy());
        }
//?} else {
        // 1.21.1: TLM 手办数据存 MAID_INFO 组件 (CustomData, 直接 {id, model_id, IsYsmModel})
        // → 复制到输出 CUSTOM_DATA.EntityInfo (与 DefenseGarageKitBlock.readEntityInfo 一致)
        var maidInfo = source.get(com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent.MAID_INFO);
        if (maidInfo != null) {
            net.minecraft.nbt.CompoundTag inner = maidInfo.copyTag();
            net.minecraft.nbt.CompoundTag info = new net.minecraft.nbt.CompoundTag();
            info.put(ENTITY_INFO, inner);
            out.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(info));
        }
//?}
        return out;
    }

    /** 输出物品 (无 NBT 默认形态 — JEI 显示 + 合成基础) */
    private static ItemStack resultItem() {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(OUTPUT_ITEM)));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DefenseGarageKitRecipeSerializer.INSTANCE;
    }
}

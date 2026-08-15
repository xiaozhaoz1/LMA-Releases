package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

 //? if !1.20.1 {
import net.minecraft.world.item.crafting.RecipeHolder;
 //?}

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.SlotLayout;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.ItemSpawner;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaInputRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
//?}
 //? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
 //?} else {
import net.minecraft.core.registries.BuiltInRegistries;
 //?}

import java.util.List;
import java.util.Map;

/**
 * 熔炉业务服务 (v79.61x 架构审计 A 抽取) — 配方扫描/原料解析/生效名单。
 *
 * <p>原 FurnacePipeline 内联: validate 与 resolveSmeltIngredient 两处重复配方循环 +
 * 双平台条件化混在管线 (五层尺越界)。逐行搬入本类, 行为零变化 (失败文案逐字保留)。
 * v79.59 #191: 名单匹配走 ItemFilters.isAllowed(Item) — Item.toString() 双平台语义不一致。
 */
public final class FurnaceService {

    private FurnaceService() {}

    /** 校验可烧炼 — null = 通过; 非 null = 失败文案 (与原 validate 文案逐字一致) */
    public static String validateSmelt(ServerLevel level, EntityMaid maid, String target) {
        Map<Item, Integer> allItems = VanillaInputRegistry.readAllItems(maid);
        var lists = effectiveLists(maid);
        List<String> black = lists.get(0);
        List<String> white = lists.get(1);

        // 空 target — 检查是否有任何可烧炼材料
        if (target.isEmpty()) {
 //? if 1.20.1 {
            for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
 //?} else {
            for (RecipeHolder<SmeltingRecipe> recipeHolder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
                SmeltingRecipe recipe = recipeHolder.value();
 //?}
                for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                    if (!ItemFilters.isAllowed(ing.getItem(), black, white)) continue;
                    if (allItems.getOrDefault(ing.getItem(), 0) > 0) return null;
                }
            }
            // 燃料判定 — AbstractFurnaceBlockEntity.isFuel (原木/煤等)。
            // 原实现误查「可烧炼产物」(铁锭是铁矿配方产物) → 有铁锭就能开炉、开了就失败循环 (审计 H2)。
            for (Item item : allItems.keySet()) {
                if (!ItemFilters.isAllowed(item, black, white)) continue;
                if (AbstractFurnaceBlockEntity.isFuel(new ItemStack(item))) return null;
            }
            return "无可烧炼材料";
        }

 //? if 1.20.1 {
        Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(target));
 //?} else {
        Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(target));
 //?}
        if (targetItem == null) return "无效的目标物品: " + target;
        if (!ItemFilters.isAllowed(targetItem, black, white)) return "目标物品在黑/白名单之外: " + target;

 //? if 1.20.1 {
        for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
 //?} else {
        for (RecipeHolder<SmeltingRecipe> recipeHolder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            SmeltingRecipe recipe = recipeHolder.value();
 //?}
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.is(targetItem)) continue;
            for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                if (!ItemFilters.isAllowed(ing.getItem(), black, white)) continue;
                if (allItems.getOrDefault(ing.getItem(), 0) > 0) return null;
            }
        }
        if (allItems.getOrDefault(targetItem, 0) > 0) return null;
        return "no smeltable material for " + target;
    }

    /** 取可烧炼原料注册名 — 无 → "" (executeOne 用; 空 target 立即失败 — 原语义) */
    public static String resolveSmeltIngredient(ServerLevel level, EntityMaid maid) {
        String target = TaskMetaData.getTarget(maid);
        if (target.isEmpty()) return "";
 //? if 1.20.1 {
        Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(target));
 //?} else {
        Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(target));
 //?}
        if (targetItem == null) return "";
        var lists = effectiveLists(maid);
        List<String> black = lists.get(0);
        List<String> white = lists.get(1);
        if (!ItemFilters.isAllowed(targetItem, black, white)) return "";
        Map<Item, Integer> allItems = VanillaInputRegistry.readAllItems(maid);
 //? if 1.20.1 {
        for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
 //?} else {
        for (RecipeHolder<SmeltingRecipe> recipeHolder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            SmeltingRecipe recipe = recipeHolder.value();
 //?}
            if (!recipe.getResultItem(level.registryAccess()).is(targetItem)) continue;
            for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                if (!ItemFilters.isAllowed(ing.getItem(), black, white)) continue;
                if (allItems.getOrDefault(ing.getItem(), 0) > 0)
 //? if 1.20.1 {
                    return ForgeRegistries.ITEMS.getKey(ing.getItem()).toString();
 //?} else {
                    return BuiltInRegistries.ITEM.getKey(ing.getItem()).toString();
 //?}
            }
        }
        if (allItems.getOrDefault(targetItem, 0) > 0) return target;
        return "";
    }

    /** 生效黑+白名单 pair [0]=black [1]=white — per-maid 覆盖全局 (v79.61x 收敛 effectiveBlack/effectiveWhite) */
    private static List<List<String>> effectiveLists(EntityMaid maid) {
        return ItemFilters.effectivePair(TaskConfigs.get(maid, "furnace"),
                ActiveTaskConfig.FURNACE_BLACKLIST.get(), ActiveTaskConfig.FURNACE_WHITELIST.get());
    }

    // ── 单拍业务动作 (v79.61x 定级修正: 原 FurnaceOutput 多步业务动作, 非 io 原语 —
    //    无跨 tick 状态 + 有领域语义 → service 单拍; 搬入本类, 行为零变化) ──

    /** 收一炉产物 — 读产物槽 → 清槽 → 掉落拾取 (多步业务动作) */
    public static boolean collectResult(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                         SlotLayout slots) {
        int outSlot = slots.slot("output").orElse(2);
        ItemStack result = furnace.getItem(outSlot);
        if (result.isEmpty()) return false;
        ItemStack copy = result.copy();
        furnace.setItem(outSlot, ItemStack.EMPTY);
        furnace.setChanged();
        ItemSpawner.spawnForPickup(maid, copy);
        return true;
    }

    /** 加一次料 — 注册表解析 → 找背包 → 抽取 → 入炉 (多步业务动作) */
    public static boolean addInput(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                    String inputItemId, SlotLayout slots) {
        int inSlot = slots.slot("input").orElse(0);
        ItemStack input = furnace.getItem(inSlot);
        if (!input.isEmpty()) return false;
        if (inputItemId.isEmpty()) return false;
//? if 1.20.1 {
        var ti = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
//?} else {
        var ti = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(inputItemId));
//?}
        if (ti == null) return false;
        IItemHandler inv = maid.getAvailableInv(true);
        int s = ItemsUtil.findStackSlot(inv, stack -> stack.is(ti));
        if (s < 0) return false;
        int toTake = Math.min(8, inv.getStackInSlot(s).getCount());
        furnace.setItem(inSlot, inv.extractItem(s, toTake, false).copy());
        furnace.setChanged();
        return true;
    }

    /** 加一次燃料 — 燃料判定 → 找背包 → 抽取 → 入炉 (多步业务动作) */
    public static boolean addFuel(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                   String inputItemId, SlotLayout slots) {
        int fuelSlot = slots.slot("fuel").orElse(1);
        ItemStack fuel = furnace.getItem(fuelSlot);
        if (!fuel.isEmpty()) return false;
        IItemHandler inv = maid.getAvailableInv(true);
//? if 1.20.1 {
        var ti = inputItemId.isEmpty() ? null : ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
//?} else {
        var ti = inputItemId.isEmpty() ? null : BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(inputItemId));
//?}
        int s = ItemsUtil.findStackSlot(inv, stack ->
            AbstractFurnaceBlockEntity.isFuel(stack) && (ti == null || !stack.is(ti)));
        if (s < 0) return false;
        int toTake = Math.min(64, inv.getStackInSlot(s).getCount());
        furnace.setItem(fuelSlot, inv.extractItem(s, toTake, false).copy());
        furnace.setChanged();
        return true;
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
//? if 1.20.1 {
import net.minecraftforge.items.ItemStackHandler;
//?} else {
import net.neoforged.neoforge.items.ItemStackHandler;
//?}
//? if 1.20.1 {
import net.minecraftforge.items.wrapper.RecipeWrapper;
//?} else {
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
//?}

import javax.annotation.Nullable;
import java.util.*;

public final class MaidAssemblyService {

    public static final int SEARCH_RADIUS = 3;
    public static final int BASE_DURATION = 100;

    private MaidAssemblyService() {}

    // ═══ MachineKind ═══

    public enum MachineKind {
        PRESS, SAW, DEPLOYER, MILLSTONE;

        @Nullable
        public static MachineKind fromStack(ItemStack stack) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null || !"create".equals(id.getNamespace())) return null;
            return switch (id.getPath()) {
                case "mechanical_press" -> PRESS;
                case "mechanical_saw" -> SAW;
                case "deployer" -> DEPLOYER;
                case "millstone" -> MILLSTONE;
                default -> null;
            };
        }

        public boolean needsExtraMaterial() { return this == DEPLOYER; }
    }

    // ═══ Recipe Finding ═══

    @Nullable
    public static Recipe<?> findSimpleRecipe(Level level, MachineKind kind, ItemStack input) {
        if (input.isEmpty()) return null;
        return switch (kind) {
            case PRESS -> findPressing(level, input);
            case SAW -> findCutting(level, input);
            case MILLSTONE -> findMilling(level, input);
            default -> null;
        };
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    @Nullable
    public static DeployerApplicationRecipe findDeployingRecipe(Level level, ItemStack input, ItemStack held) {
        if (input.isEmpty() || held.isEmpty()) return null;
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, input.copy());
        inv.setStackInSlot(1, held.copy());
//? if 1.20.1 {
        RecipeWrapper wrapper = new RecipeWrapper(inv);
        // 1. SequencedAssembly 子配方
        var result = SequencedAssemblyRecipe.getRecipe(level, wrapper,
            AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class,
            r -> r.matches(wrapper, level));
        // 2. 常规部署配方回退 — 仅非装配中间产物
        if (result.isEmpty() && !isAssemblyIntermediate(input))
            result = (Optional) AllRecipeTypes.DEPLOYING.find(wrapper, level);
        return (DeployerApplicationRecipe) result.orElse(null);
//?} else {
        // 1.21 RecipeWrapper = neoforge items.wrapper; getRecipe 返回 RecipeHolder
        net.neoforged.neoforge.items.wrapper.RecipeWrapper wrapper =
                new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inv);
        var result = SequencedAssemblyRecipe.getRecipe(level, wrapper,
            AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class,
            h -> h.value().matches(wrapper, level));
        if (result.isEmpty() && !isAssemblyIntermediate(input))
            result = AllRecipeTypes.DEPLOYING.find(wrapper, level);
        return result.map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
//?}
    }

    @Nullable
    public static ItemStack findDeployerHeldItem(Level level, ItemStack input, List<ItemStack> available) {
        for (ItemStack c : available)
            if (!c.isEmpty() && findDeployingRecipe(level, input, c) != null) return c.copy();
        return null;
    }

    // ═══ Recipe Execution ═══

    public static List<ItemStack> processSimple(Level level, Recipe<?> recipe, ItemStack input) {
        return RecipeApplier.applyRecipeOn(level, input.copy(), recipe, true);
    }

    public static List<ItemStack> processDeploying(Level level, DeployerApplicationRecipe recipe,
                                                    ItemStack input, ItemStack held) {
        List<ItemStack> out = RecipeApplier.applyRecipeOn(level, input.copy(), recipe, true);
        if (!recipe.shouldKeepHeldItem()) {
            if (held.isDamageableItem()) {
                held.setDamageValue(held.getDamageValue() + 1);
                if (held.getDamageValue() >= held.getMaxDamage()) held.shrink(1);
            } else held.shrink(1);
        }
        return out;
    }

    public static boolean isAssemblyIntermediate(ItemStack out) {
        return hasSequencedTag(out);
    }

    /** 序列装配标记判断双平台 (1.21 无 hasTag/getTag — DataComponents.CUSTOM_DATA) */
    private static boolean hasSequencedTag(ItemStack stack) {
//? if 1.20.1 {
        return stack.hasTag() && stack.getTag().contains("SequencedAssembly");
//?} else {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("SequencedAssembly");
//?}
    }

    /** 物品相同判断双平台 */
    // ═══ Sound ═══

    public static void playMachineSound(Level level, Vec3i pos, MachineKind kind) {
        if (level.isClientSide) return;
        float r = level.random.nextFloat();
        switch (kind) {
            case PRESS -> AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, pos, 0.5f, 0.75f + r * 0.25f);
            case SAW -> AllSoundEvents.SAW_ACTIVATE_WOOD.playOnServer(level, pos, 0.75f, 0.85f + 0.15f * r);
            case DEPLOYER -> AllSoundEvents.CRAFTER_CLICK.playOnServer(level, pos, 0.75f, 0.85f + 0.15f * r);
            case MILLSTONE -> AllSoundEvents.CRUSHING_1.playOnServer(level, pos, 0.5f, 0.9f + 0.2f * r);
        }
    }

    // ═══ Duration ═══

    public static int getDuration(MachineKind kind, EntityMaid maid) {
        int base = switch (kind) { case DEPLOYER -> 120; default -> BASE_DURATION; };
        // 好感度效率乘区 (原 switch 分档 → base / speed 倍率)
        return (int) (base / com.github.xiaozhaoz1.littlemaidmoreaction.task.service.MaidFavorability.workSpeedMultiplier(maid));
    }

    // ═══ Nearby search (委托 NearbyContainerService) ═══

    public static List<ItemStack> collectAvailableItems(EntityMaid maid) {
        return collectAvailableItems(maid, false);
    }

    /** 收集可用物品: 背包 + 附近容器 + 隙间(可选). */
    public static List<ItemStack> collectAvailableItems(EntityMaid maid, boolean includeWireless) {
        List<ItemStack> all = new ArrayList<>();
        var bp = maid.getAvailableBackpackInv();
        for (int s = 0; s < bp.getSlots(); s++) { ItemStack st = bp.getStackInSlot(s); if (!st.isEmpty()) all.add(st.copy()); }
        if (maid.level() != null) all.addAll(
            com.github.xiaozhaoz1.littlemaidmoreaction.task.service.NearbyContainerService.scanItems(maid.level(), maid.blockPosition(), SEARCH_RADIUS));
        if (includeWireless) {
            var w = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.container.WirelessChestSpace.getWirelessHandler(maid);
            if (w != null) for (int s = 0; s < w.getSlots(); s++) {
                ItemStack st = w.getStackInSlot(s); if (!st.isEmpty()) all.add(st.copy());
            }
        }
        return all;
    }

    public static ItemStack extractNearbyItem(Level level, BlockPos pos, ItemStack target) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.service.NearbyContainerService.extractItem(
            level, pos, SEARCH_RADIUS, st -> ItemStackHelper.isSameItem(st, target), java.util.Set.of());
    }

    // ═══ Machine block item ═══

    public static ItemStack getMachineBlockStack(MachineKind kind) {
        if (kind == null) return ItemStack.EMPTY;
        String path = switch (kind) {
            case PRESS -> "mechanical_press";
            case SAW -> "mechanical_saw";
            case DEPLOYER -> "deployer";
            case MILLSTONE -> "millstone";
        };
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
        return new ItemStack(item);
    }

    // ═══ Private ═══

    @SuppressWarnings({"unchecked","rawtypes"})
    @Nullable private static PressingRecipe findPressing(Level l, ItemStack in) {
        if (isAssemblyIntermediate(in))
            return (PressingRecipe)(Object) SequencedAssemblyRecipe.getRecipe(l, in,
                AllRecipeTypes.PRESSING.getType(), PressingRecipe.class).orElse(null);
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, in.copy());
        return (PressingRecipe)(Object) AllRecipeTypes.PRESSING.find(new RecipeWrapper(inv), l).orElse(null);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Nullable private static CuttingRecipe findCutting(Level l, ItemStack in) {
        if (isAssemblyIntermediate(in))
            return (CuttingRecipe)(Object) SequencedAssemblyRecipe.getRecipe(l, in,
                AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class).orElse(null);
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, in.copy());
        return (CuttingRecipe)(Object) AllRecipeTypes.CUTTING.find(new RecipeWrapper(inv), l).orElse(null);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Nullable private static MillingRecipe findMilling(Level l, ItemStack in) {
        if (isAssemblyIntermediate(in))
            return (MillingRecipe)(Object) SequencedAssemblyRecipe.getRecipe(l, in,
                AllRecipeTypes.MILLING.getType(), MillingRecipe.class).orElse(null);
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, in.copy());
        return (MillingRecipe)(Object) AllRecipeTypes.MILLING.find(new RecipeWrapper(inv), l).orElse(null);
    }
}

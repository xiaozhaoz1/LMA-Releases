package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.SmithingPipeline;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

/**
 * v79.62.1 锻造容器 — 类似原版锻造台: base(装备) + addition(材料) + result + 女仆背包 + 玩家背包.
 *
 * <p><b>无模板升级</b>: 遍历 RecipeManager 全部 smithing transform 配方, 跳过模板需求,
 * 仅匹配 base+addition (覆盖所有 mod 锻造模板配方 + 原版下界合金升级).
 *
 * <p><b>合成流程 (用户裁定)</b>: 玩家从女仆背包拖装备/材料进 base/addition 槽 →
 * 结果槽实时显示 → 玩家点结果槽取出 = 合成完成 (消耗 base+addition, 产物给玩家).
 * 女仆必须在锻造台 4 格内才能合成.
 */
public class MaidSmithingMenu extends AbstractContainerMenu {

    public static final int SLOT_SIZE = 18;
    public static final int BASE_SLOT = 0;
    public static final int ADDITION_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    /** 锻造槽数量 */
    public static final int SMITH_SLOTS = 3;
    /** 女仆背包槽起始 */
    public static final int MAID_INV_START = SMITH_SLOTS;
    /** 玩家背包槽起始 (女仆背包 36 槽) */
    public static final int PLAYER_INV_START = MAID_INV_START + 36;
    /** 总槽数 */
    public static final int TOTAL_SLOTS = PLAYER_INV_START + 36;

    /** 锻造台工作距离 (sqr) */
    private static final double WORK_DIST_SQR = 16.0;

    private final Container smithSlots = new SimpleContainer(3);
    private final EntityMaid maid;

    public MaidSmithingMenu(int id, Inventory playerInv, EntityMaid maid) {
        super(LmaMenus.SMITHING_MENU, id);
        this.maid = maid;
        addSlots(playerInv);
    }

    /** 客户端打开 (网络包 maidId 反查) */
    public MaidSmithingMenu(int id, Inventory playerInv, FriendlyByteBuf data) {
        super(LmaMenus.SMITHING_MENU, id);
        EntityMaid m = playerInv.player.level().getEntity(data.readInt()) instanceof EntityMaid e ? e : null;
        this.maid = m;
        addSlots(playerInv);
    }

    private void addSlots(Inventory playerInv) {
        // 锻造槽: base / addition / result (无模板 — 跳过模板槽)
        addSlot(new SmithSlot(smithSlots, BASE_SLOT, 26, 48));
        addSlot(new SmithSlot(smithSlots, ADDITION_SLOT, 44, 48));
        addSlot(new ResultSlot(smithSlots, RESULT_SLOT, 98, 48));
        // 女仆背包 (36) — 只读源 (可拖入锻造槽, 不可被玩家拿走)
        if (maid != null) {
            var inv = maid.getAvailableInv(true);
            int n = Math.min(inv.getSlots(), 36);
            for (int i = 0; i < n; i++) {
//? if 1.20.1 {
                addSlot(new net.minecraftforge.items.SlotItemHandler(inv, i, 8 + (i % 9) * SLOT_SIZE, 124 + (i / 9) * SLOT_SIZE) {
                    @Override public boolean mayPickup(Player p) { return false; }
                });
//?} else {
                addSlot(new net.neoforged.neoforge.items.SlotItemHandler(inv, i, 8 + (i % 9) * SLOT_SIZE, 124 + (i / 9) * SLOT_SIZE) {
                    @Override public boolean mayPickup(Player p) { return false; }
                });
//?}
            }
        }
        // 玩家背包 (36) — 原版锻造台下方
        int py = 190;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * SLOT_SIZE, py + row * SLOT_SIZE));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 8 + col * SLOT_SIZE, py + 58));
    }

    // ── 合成逻辑 ──

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        refreshResult();
    }

    /** 无模板刷新结果槽 — 遍历 transform 配方, base+addition 匹配 */
    public void refreshResult() {
        if (maid == null || !(maid.level() instanceof ServerLevel sl)) return;
        ItemStack base = smithSlots.getItem(BASE_SLOT);
        ItemStack addition = smithSlots.getItem(ADDITION_SLOT);
        if (base.isEmpty() || addition.isEmpty()) {
            smithSlots.setItem(RESULT_SLOT, ItemStack.EMPTY);
            return;
        }
//? if 1.20.1 {
        for (SmithingRecipe recipe : sl.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
//?} else {
        for (var holder : sl.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            SmithingRecipe recipe = holder.value();
//?}
            if (!(recipe instanceof SmithingTransformRecipe)) continue;
            if (!recipe.isBaseIngredient(base)) continue;
            if (!recipe.isAdditionIngredient(addition)) continue;
            smithSlots.setItem(RESULT_SLOT, assemble(sl, recipe, base, addition));
            return;
        }
        smithSlots.setItem(RESULT_SLOT, ItemStack.EMPTY);
    }

    /** 无模板应用配方 — 双平台 assemble 差异 */
    private static ItemStack assemble(ServerLevel level, SmithingRecipe recipe, ItemStack base, ItemStack addition) {
//? if 1.20.1 {
        net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(3);
        container.setItem(1, base);
        container.setItem(2, addition);
        return recipe.assemble(container, level.registryAccess());
//?} else {
        return recipe.assemble(
                new net.minecraft.world.item.crafting.SmithingRecipeInput(ItemStack.EMPTY, base, addition),
                level.registryAccess());
//?}
    }

    /** 女仆是否在锻造台旁 (合成前置校验) */
    private boolean maidAtTable() {
        if (maid == null || !(maid.level() instanceof ServerLevel sl)) return false;
        return SmithingPipeline.findSmithingTable(sl, maid) != null;
    }

    // ── 容器契约 ──

    @Override
    public boolean stillValid(Player player) {
        if (maid == null) return false;
        if (!maid.isAlive()) return false;
        return player.distanceToSqr(maid) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 屏蔽 shift 快速移动 (锻造需手动拖入槽位)
        return ItemStack.EMPTY;
    }

    public EntityMaid getMaid() { return maid; }
    public Container getSmithSlots() { return smithSlots; }

    // ── 槽位实现 ──

    /** 普通锻造输入槽 (base/addition) */
    private static class SmithSlot extends Slot {
        SmithSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
    }

    /**
     * 结果槽 — 不可放入; 玩家点击取出 = 合成完成:
     * 校验女仆在锻造台旁 + 消耗 base+addition + 产物给玩家.
     */
    private class ResultSlot extends Slot {
        ResultSlot(Container c, int i, int x, int y) { super(c, i, x, y); }

        @Override public boolean mayPlace(ItemStack s) { return false; }

        @Override public boolean mayPickup(Player player) {
            if (!maidAtTable()) return false;               // 女仆必须在锻造台旁
            return !smithSlots.getItem(RESULT_SLOT).isEmpty();
        }

        @Override public void onTake(Player player, ItemStack stack) {
            // 消耗 base + addition (槽内缩 1)
            ItemStack base = smithSlots.getItem(BASE_SLOT);
            ItemStack addition = smithSlots.getItem(ADDITION_SLOT);
            if (!base.isEmpty()) base.shrink(1);
            if (!addition.isEmpty()) addition.shrink(1);
            smithSlots.setItem(RESULT_SLOT, ItemStack.EMPTY);
            // 产物给玩家 (背包/手)
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            super.onTake(player, stack);
        }
    }
}

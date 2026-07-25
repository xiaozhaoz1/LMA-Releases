package littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 便携装配 Menu — 原版176px宽。锁定通过clickMenuButton发包+DataSlot同步状态。
 *
 * <pre>
 *   [0][1][2][3][4][5][6][7]  — 机器方块 y=20
 *    🧱 中              📦📦  — 标签 y=44
 *   [🧱][中]          [📦][📦] — 材料+中间+最终×2 y=54
 *   [🔒]              [✓]   — 按钮 y=78
 * </pre>
 */
public class MaidAssemblyMenu extends AbstractContainerMenu {

    public static final int SLOT_SIZE = 18;
    public static final int MACHINE_X = 8, MACHINE_Y = 20;
    public static final int LABEL_Y = 44;
    public static final int BOTTOM_Y = 54;
    public static final int PANEL_W = 176, PANEL_TOP_H = 100;

    public static final int MACHINE_SLOTS = 8;
    public static final int MATERIAL_SLOT = 8;
    public static final int INTERMEDIATE_SLOT = 9;
    public static final int OUTPUT1_SLOT = 10;
    public static final int OUTPUT2_SLOT = 11;
    public static final int TOTAL_SLOTS = 12;

    public static final int PLAYER_Y = 113;
    public static final int LOCK_BUTTON_ID = 100;

    private final MaidAssemblyInventory inv;
    private final EntityMaid maid;
    private boolean matLockedSynced;

    public MaidAssemblyMenu(int id, Inventory playerInv, EntityMaid maid) {
        super(LittleMaidMoreAction.MAID_ASSEMBLY_MENU.get(), id);
        this.maid = maid;
        this.inv = MaidAssemblyInventory.of(maid); // 服务端共享单例
        addDataAndSlots(playerInv);
    }

    public MaidAssemblyMenu(int id, Inventory playerInv, FriendlyByteBuf data) {
        super(LittleMaidMoreAction.MAID_ASSEMBLY_MENU.get(), id);
        this.maid = MaidAssemblyNetwork.getMaidFromMenu(playerInv, data);
        this.inv = MaidAssemblyInventory.client(this.maid); // 客户端独立实例
        addDataAndSlots(playerInv);
    }

    private void addDataAndSlots(Inventory playerInv) {
        // DataSlot: 材料锁状态同步到客户端 (server→client)
        addDataSlot(new DataSlot() {
            @Override public int get() { return inv.isMaterialLocked() ? 1 : 0; }
            @Override public void set(int v) { matLockedSynced = v != 0; }
        });

        // 机器方块 0-7
        for (int i = 0; i < MACHINE_SLOTS; i++)
            addSlot(new MachineSlot(inv, i, MACHINE_X + i * SLOT_SIZE, MACHINE_Y));
        // 材料 + 中间
        addSlot(new SlotItemHandler(inv, MATERIAL_SLOT, MACHINE_X, BOTTOM_Y));
        addSlot(new SlotItemHandler(inv, INTERMEDIATE_SLOT, MACHINE_X + SLOT_SIZE, BOTTOM_Y));
        // 输出×2
        addSlot(new SlotItemHandler(inv, OUTPUT1_SLOT, MACHINE_X + 6 * SLOT_SIZE, BOTTOM_Y));
        addSlot(new SlotItemHandler(inv, OUTPUT2_SLOT, MACHINE_X + 7 * SLOT_SIZE, BOTTOM_Y));
        // 玩家
        int px = 8;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, px + col * SLOT_SIZE, PLAYER_Y + row * SLOT_SIZE));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, px + col * SLOT_SIZE, PLAYER_Y + 58));
    }

    public MaidAssemblyInventory getInv() { return inv; }
    public EntityMaid getMaid() { return maid; }
    /** 客户端锁状态（由DataSlot同步） */
    public boolean isMatLocked() { return inv.isMaterialLocked() || matLockedSynced; }

    @Override
    public boolean stillValid(Player player) {
        return maid != null && !maid.isRemoved()
            && player.distanceToSqr(maid.getX() + 0.5, maid.getY() + 0.5, maid.getZ() + 0.5) <= 64;
    }

    @Override
    public void removed(Player player) {
        LittleMaidMoreAction.LOGGER.info("[AssemblyInv] Menu.removed items[0]={}",
            inv.getStackInSlot(0).isEmpty() ? "-" : inv.getStackInSlot(0).getDisplayName().getString());
        super.removed(player);
        inv.saveToNBT();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == LOCK_BUTTON_ID) {
            inv.handleMaterialLock(getCarried());
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        ItemStack moving = slot.getItem();

        if (index < TOTAL_SLOTS) {
            if (!moveItemStackTo(moving, TOTAL_SLOTS, slots.size(), true))
                return ItemStack.EMPTY;
        } else {
            MaidAssemblyService.MachineKind kind = MaidAssemblyService.MachineKind.fromStack(moving);
            if (kind != null) {
                if (!moveItemStackTo(moving, 0, MACHINE_SLOTS, false))
                    return ItemStack.EMPTY;
            } else if (!moveItemStackTo(moving, MATERIAL_SLOT, MATERIAL_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (moving.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    private static class MachineSlot extends SlotItemHandler {
        MachineSlot(IItemHandler h, int i, int x, int y) { super(h, i, x, y); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public int getMaxStackSize(ItemStack s) { return 1; }
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 防御塔 GUI 容器 (v79.62.3) — 9 格弹药槽 + 玩家背包 + 控制行 (模式/半径/伤害)。
 *
 * <p>控制同步 (MaidAssemblyScreen 同款): 值经 {@link DataSlot} 服务端广播到客户端;
 * 模式/半径/伤害按钮走 vanilla {@link #clickMenuButton} (客户端
 * {@code handleInventoryButtonClick} 发 ServerboundContainerButtonClickPacket), 无自定义网络包。
 *
 * <p>布局 (与 DefenseTowerScreen 的 drawSlotBg 坐标一一对应):
 * 弹药 3×3 = x 62/80/98, y 20/38/56; 玩家背包 py=156 (行 156/174/192, 快捷 210);
 * 控制行在 y 78~136 (不压物品栏)。
 *
 * <p>stillValid: 塔 BE 存活 + 玩家 8 格内 + 玩家是塔主人 (裁定 ⑨, 非主人拒开)。
 */
public class DefenseTowerMenu extends AbstractContainerMenu {

    public static final int SLOT_SIZE = 18;
    /** 武器槽 (1 格: 弓/御币) */
    public static final int WEAPON_SLOTS = 1;
    /** 弹药槽 9 格 (3×3) */
    public static final int AMMO_SLOTS = 9;
    /** 玩家背包槽起始 */
    public static final int PLAYER_INV_START = WEAPON_SLOTS + AMMO_SLOTS;
    /** 总槽数 (1 + 9 + 27 + 9) */
    public static final int TOTAL_SLOTS = PLAYER_INV_START + 36;

    /** 主面板框高 (与屏 PANEL_H 一致 — 必须相同! 否则物品栏槽位/背景错位) */
    public static final int PANEL_H = 140;
    /** 物品栏框槽位起点 y = 主面板 + 间距(4) + 框内标签下槽位起点(9) — 与屏 invTop+9 对齐 */
    public static final int PLAYER_INV_Y = PANEL_H + 4 + 9;

    /** clickMenuButton 按钮 id */
    public static final int BTN_RADIUS_UP = 0;
    public static final int BTN_RADIUS_DOWN = 1;

    private final DefenseGarageKitBlockEntity tower;

    /** 服务端广播到客户端的半径 (standalone DataSlot; 攻击方式由武器自动判定, 无需 modeSlot) */
    private final DataSlot radiusSlot = DataSlot.standalone();

    public DefenseTowerMenu(int id, Inventory playerInv, DefenseGarageKitBlockEntity tower) {
        super(LmaMenus.DEFENSE_TOWER_MENU.get(), id);
        this.tower = tower;
        radiusSlot.set(tower.getRadius());
        addDataSlot(radiusSlot);
        addSlots(playerInv);
    }

    /** 客户端打开 (buf: BlockPos → 反查 BE; DataSlot 由服务端广播覆盖) */
    public DefenseTowerMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        super(LmaMenus.DEFENSE_TOWER_MENU.get(), id);
        net.minecraft.core.BlockPos pos = buf.readBlockPos();
        var be = playerInv.player.level().getBlockEntity(pos);
        this.tower = be instanceof DefenseGarageKitBlockEntity t ? t : null;
        // 初始值: 客户端 BE 若已同步则用 (首帧无闪烁), 否则 0 等广播
        radiusSlot.set(tower != null ? tower.getRadius() : 16);
        addDataSlot(radiusSlot);
        addSlots(playerInv);
    }

    private void addSlots(Inventory playerInv) {
        // 塔容器 = 武器槽(1) + 弹药槽(9)
        net.minecraft.world.SimpleContainer inv = tower != null ? tower.getAmmo() : new net.minecraft.world.SimpleContainer(WEAPON_SLOTS + AMMO_SLOTS);
        // 武器槽 1 格 (x62, y34 — 弓/御币)
        addSlot(new Slot(inv, 0, 62, 34));
        // 弹药槽 9 格 3×3 (x62/80/98, y52/70/88) — 与屏 drawSlotBg 对齐
        for (int i = 0; i < AMMO_SLOTS; i++) {
            addSlot(new Slot(inv, WEAPON_SLOTS + i, 62 + (i % 3) * SLOT_SIZE, 52 + (i / 3) * SLOT_SIZE));
        }
        // 玩家背包 27 + 9 (下移 — 控制行在中间)
        int py = PLAYER_INV_Y;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * SLOT_SIZE, py + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * SLOT_SIZE, py + 58));
        }
    }

    // ── 控制 (vanilla clickMenuButton — 无自定义网络包) ──

    /** 客户端按钮 → handleInventoryButtonClick(containerId, id) → 服务端此方法 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (tower == null) return false;
        switch (id) {
            case BTN_RADIUS_UP -> tower.setRadius(tower.getRadius() + 4);
            case BTN_RADIUS_DOWN -> tower.setRadius(tower.getRadius() - 4);
            default -> { return false; }
        }
        // 同步 DataSlot (broadcastChanges 下 tick 广播给客户端)
        radiusSlot.set(tower.getRadius());
        return true;
    }

    // ── 客户端读取 (DataSlot 广播值) ──

    public int getRadius() { return radiusSlot.get(); }

    @Override
    public boolean stillValid(Player player) {
        if (tower == null || tower.isRemoved()) return false;
        if (tower.getOwnerUuid() != null && !tower.getOwnerUuid().equals(player.getUUID())) return false;
        return player.distanceToSqr(tower.getBlockPos().getX() + 0.5,
                tower.getBlockPos().getY() + 0.5,
                tower.getBlockPos().getZ() + 0.5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < AMMO_SLOTS) {
                // 弹药槽 → 玩家背包
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, TOTAL_SLOTS, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, AMMO_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    public DefenseGarageKitBlockEntity getTower() { return tower; }
}

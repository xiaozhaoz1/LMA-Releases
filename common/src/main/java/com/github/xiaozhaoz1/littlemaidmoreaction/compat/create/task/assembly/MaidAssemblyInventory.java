package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.items.ItemStackHandler;
//?} else {
import net.neoforged.neoforge.items.ItemStackHandler;
//?}

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 便携装配库存 — 仿 Biotech SpiderAssemblyTable: 每 Maid 单一实例, 通过 {@link #of} 获取.
 *
 * <p>Pipeline 和 Menu 共享同一实例 → Pipeline修改实时可见, Menu关闭时保存.
 * <p>客户端 Menu 使用单独实例 (不共享), 通过容器数据包同步.
 */
public final class MaidAssemblyInventory extends ItemStackHandler {

    private static final Map<UUID, MaidAssemblyInventory> CACHE = new ConcurrentHashMap<>();

    public static final int MACHINE_SLOTS = 8;
    public static final int MATERIAL_SLOT = 8;
    public static final int INTERMEDIATE_SLOT = 9;
    public static final int OUTPUT1_SLOT = 10;
    public static final int OUTPUT2_SLOT = 11;
    public static final int TOTAL_SLOTS = 12;

    private static final String NBT_KEY = TaskKeys.ASSEMBLY_INV, INV_KEY = "Inventory",
        LOCKS_KEY = "Locks", BLOCKED_KEY = "Blocked", MAT_LOCK_KEY = "MatLock";

    private final EntityMaid maid;
    private final boolean serverSide;
    private final ItemStack[] itemLocks = new ItemStack[MACHINE_SLOTS];
    private final boolean[] slotBlocked = new boolean[MACHINE_SLOTS];
    private ItemStack materialLock = ItemStack.EMPTY;

    /** 获取/创建此 Maid 的唯一 Inventory 实例 (服务端共享) */
    public static MaidAssemblyInventory of(EntityMaid maid) {
        var existing = CACHE.get(maid.getUUID());
        if (existing != null && existing.maid == maid) return existing;
        var inv = new MaidAssemblyInventory(maid, true);
        CACHE.put(maid.getUUID(), inv);
        return inv;
    }

    /** 服务端用: 共享实例, serverSide=true */
    private MaidAssemblyInventory(EntityMaid maid, boolean serverSide) {
        super(TOTAL_SLOTS);
        this.maid = maid;
        this.serverSide = serverSide;
        for (int i = 0; i < MACHINE_SLOTS; i++) itemLocks[i] = ItemStack.EMPTY;
        loadFromNBT();
        LittleMaidMoreAction.LOGGER.info("[AssemblyInv] created serverSide={} maid={}", serverSide, maid.getStringUUID());
    }

    /** 客户端用: 独立实例, 不同步 NBT */
    public static MaidAssemblyInventory client(EntityMaid maid) {
        return new MaidAssemblyInventory(maid, false);
    }

    @Override public int getSlotLimit(int slot) { return slot < MACHINE_SLOTS ? 1 : 64; }

    @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot < MACHINE_SLOTS) return MaidAssemblyService.MachineKind.fromStack(stack) != null;
        return true;
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        super.setStackInSlot(slot, stack);
        if (serverSide) saveToNBT();
    }

    // ── Material slot lock ──

    public boolean isMaterialLocked() { return !materialLock.isEmpty(); }
    public ItemStack getMaterialLock() { return materialLock.copy(); }

    public void handleMaterialLock(ItemStack carried) {
        if (!carried.isEmpty()) {
            materialLock = carried.copyWithCount(1);
        } else {
            if (isMaterialLocked()) materialLock = ItemStack.EMPTY;
            else { ItemStack mat = getStackInSlot(MATERIAL_SLOT); if (!mat.isEmpty()) materialLock = mat.copyWithCount(1); }
        }
        LittleMaidMoreAction.LOGGER.info("[AssemblyInv] matLock set={}", !materialLock.isEmpty());
        if (serverSide) saveToNBT();
    }

    public boolean autoRefillMaterial() {
        if (!isMaterialLocked()) return true;
        ItemStack cur = getStackInSlot(MATERIAL_SLOT);
        if (!cur.isEmpty()) return true;
        var bp = maid.getAvailableBackpackInv();
        for (int s = 0; s < bp.getSlots(); s++) {
            ItemStack st = bp.getStackInSlot(s);
            if (!st.isEmpty() && ItemStackHelper.isSameItem(st, materialLock)) {
                setStackInSlot(MATERIAL_SLOT, bp.extractItem(s, 64, false));
                return true;
            }
        }
        if (maid.level() != null) {
            ItemStack extracted = MaidAssemblyService.extractNearbyItem(maid.level(), maid.blockPosition(), materialLock);
            if (!extracted.isEmpty()) { setStackInSlot(MATERIAL_SLOT, extracted); return true; }
        }
        return false;
    }

    // ── NBT persistence ──

    private void loadFromNBT() {
        setSize(TOTAL_SLOTS); // 先分配数组, deserializeNBT 再填充
        CompoundTag root = maid.getPersistentData().getCompound(NBT_KEY);
        if (root.contains(INV_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag invTag = root.getCompound(INV_KEY);
//? if 1.20.1 {
            deserializeNBT(invTag);
//?} else {
            deserializeNBT(maid.level().registryAccess(), invTag);
//?}
            LittleMaidMoreAction.LOGGER.info("[AssemblyInv] loadFromNBT items={}", invTag.getList("Items", Tag.TAG_COMPOUND).size());
        }
        ListTag lt = root.getList(LOCKS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < MACHINE_SLOTS; i++)
            itemLocks[i] = i < lt.size() ? parseItem(maid.level().registryAccess(), lt.getCompound(i)) : ItemStack.EMPTY;
        byte[] bl = root.getByteArray(BLOCKED_KEY);
        for (int i = 0; i < MACHINE_SLOTS; i++) slotBlocked[i] = i < bl.length && bl[i] != 0;
        if (root.contains(MAT_LOCK_KEY, Tag.TAG_COMPOUND))
            materialLock = parseItem(maid.level().registryAccess(), root.getCompound(MAT_LOCK_KEY));
    }

    public void saveToNBT() {
        if (!serverSide) return;
        CompoundTag root = maid.getPersistentData().getCompound(NBT_KEY);
//? if 1.20.1 {
        root.put(INV_KEY, serializeNBT());
//?} else {
        root.put(INV_KEY, serializeNBT(maid.level().registryAccess()));
//?}
        ListTag lt = new ListTag(); for (ItemStack l : itemLocks) lt.add(saveItem(l, maid.level().registryAccess()));
        root.put(LOCKS_KEY, lt);
        byte[] bl = new byte[MACHINE_SLOTS]; for (int i = 0; i < MACHINE_SLOTS; i++) bl[i] = slotBlocked[i] ? (byte)1 : 0;
        root.putByteArray(BLOCKED_KEY, bl);
        if (isMaterialLocked()) root.put(MAT_LOCK_KEY, saveItem(materialLock, maid.level().registryAccess()));
        else root.remove(MAT_LOCK_KEY);
        maid.getPersistentData().put(NBT_KEY, root);
        LittleMaidMoreAction.LOGGER.info("[AssemblyInv] saved slots: 0={} 8={} 10={}",
            getStackInSlot(0).isEmpty() ? "-" : getStackInSlot(0).getDisplayName().getString(),
            getStackInSlot(8).isEmpty() ? "-" : getStackInSlot(8).getDisplayName().getString(),
            getStackInSlot(10).isEmpty() ? "-" : getStackInSlot(10).getDisplayName().getString());
    }

    // ── Convenience ──

    public int filledMachineSlots() { int c = 0; for (int i = 0; i < MACHINE_SLOTS; i++) if (!getStackInSlot(i).isEmpty()) c++; return c; }
    public boolean hasAnyMachine() { return filledMachineSlots() > 0; }
    public MaidAssemblyService.MachineKind getMachineKind(int slot) { return MaidAssemblyService.MachineKind.fromStack(getStackInSlot(slot)); }

    public ItemStack tryInsertOutput(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        for (int slot : new int[]{OUTPUT1_SLOT, OUTPUT2_SLOT}) {
            ItemStack ex = getStackInSlot(slot);
            if (ex.isEmpty()) { setStackInSlot(slot, stack.copy()); return ItemStack.EMPTY; }
            if (ItemStackHelper.isSameItem(ex, stack) && ex.getCount() + stack.getCount() <= ex.getMaxStackSize()) {
                ex.grow(stack.getCount()); setStackInSlot(slot, ex); return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    // ── v75.1 双平台工具 (1.21: isSameItemSameTags→isSameItemSameComponents, ItemStack.of 需 Provider) ──

    private static net.minecraft.nbt.Tag saveItem(ItemStack stack, net.minecraft.core.HolderLookup.Provider provider) {
        if (stack.isEmpty()) return new CompoundTag();   // v75.2: 1.21 ItemStack.save 禁编码空物品 (崩溃实证: Cannot encode empty ItemStack)
//? if 1.20.1 {
        return stack.save(new CompoundTag());
//?} else {
        return stack.save(provider, new CompoundTag());
//?}
    }

    private static ItemStack parseItem(net.minecraft.core.HolderLookup.Provider provider, CompoundTag tag) {
//? if 1.20.1 {
        return ItemStack.of(tag);
//?} else {
        return ItemStack.parseOptional(provider, tag);
//?}
    }
}

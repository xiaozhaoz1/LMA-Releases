package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * LMA 任务配置容器基类 — 持有配置 NBT 快照 + 共享容器契约。
 *
 * <p>服务端: 子类构造时可调用 {@link #loadConfig(CompoundTag)} 初始化。
 * <p>客户端: 响应包到达时调用 {@link #updateConfig(CompoundTag)} 更新。
 *
 * <p>容器契约 (所有任务配置屏一致):
 * <ul>
 *   <li>stillValid — 女仆存活且属于玩家才可操作 (TLM 基类 only 检查存活)</li>
 *   <li>quickMoveStack — 屏蔽 shift 快捷移动 (配置屏无槽位移动需求)</li>
 * </ul>
 */
public abstract class LmaTaskConfigContainer extends TaskConfigContainer {

    protected CompoundTag config = new CompoundTag();

    public LmaTaskConfigContainer(MenuType<?> type, int containerId, Inventory playerInv, int maidId) {
        super(type, containerId, playerInv, maidId);
    }

    @Override
    public boolean stillValid(Player player) {
        return getMaid() != null && getMaid().isAlive() && getMaid().isOwnedBy(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    protected void loadConfig(CompoundTag cfg) {
        this.config = cfg;
    }

    public void updateConfig(CompoundTag cfg) {
        this.config = cfg;
    }


    public CompoundTag getConfig() {
        return config;
    }
}

package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v67.3: 配方链合成配置容器 — 当前产物 (TASK_TARGET) + 产物上限 (pipelineConfig max_products)。
 */
public class CraftChainConfigMenu extends LmaTaskConfigContainer {

    public CraftChainConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LittleMaidMoreAction.CRAFT_CHAIN_CONFIG_MENU.get(), containerId, playerInv, maidId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Parchment/Mojang 映射兼容 — 显式覆写确保方法可见 */
    @Override
    public boolean stillValid(Player player) {
        return getMaid() != null && getMaid().isAlive() && getMaid().isOwnedBy(player);
    }
}

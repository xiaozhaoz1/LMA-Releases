package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v67.3: 通用黑白名单配置容器 — 一个 MenuType 服务 furnace/jukebox/arm_transfer。
 *
 * <p>屏幕 ({@link ItemListConfigScreen}) 通过 getTaskType() 区分任务,
 * 配置存各任务 pipelineConfig (lma_cfg_&lt;taskType&gt;) 的 blacklist/whitelist 键。
 */
public class ItemListConfigMenu extends LmaTaskConfigContainer {

    public ItemListConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LittleMaidMoreAction.ITEM_LIST_CONFIG_MENU.get(), containerId, playerInv, maidId);
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

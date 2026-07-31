package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * BlockInteract 配置容器 — 对齐 TLM AttackTaskConfigContainer 模式。
 *
 * <p>仅提供容器类型注册 + quickMoveStack 空实现。
 * 屏幕通过 {@link BlockInteractConfigScreen} 直接读写 maid pipelineConfig。
 */
public class BlockInteractConfigMenu extends LmaTaskConfigContainer {

    public BlockInteractConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LittleMaidMoreAction.BLOCK_INTERACT_CONFIG_MENU.get(), containerId, playerInv, maidId);
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

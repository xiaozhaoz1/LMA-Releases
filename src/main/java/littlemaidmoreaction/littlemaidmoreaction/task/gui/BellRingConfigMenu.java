package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v67.13: 敲钟单女仆配置容器 — 对齐 BlockInteractConfigMenu 模式。
 *
 * <p>间隔存 pipelineConfig "ring_interval", 空则用全局 BELL_RING_INTERVAL。
 * 屏幕经 {@link BellRingConfigScreen} 直接读写 maid pipelineConfig。
 */
public class BellRingConfigMenu extends LmaTaskConfigContainer {

    public BellRingConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LittleMaidMoreAction.BELL_RING_CONFIG_MENU.get(), containerId, playerInv, maidId);
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

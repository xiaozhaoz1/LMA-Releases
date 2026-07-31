package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * LMA 任务配置容器基类 — 持有配置 NBT 快照。
 *
 * <p>服务端: 子类构造时可调用 {@link #loadConfig(CompoundTag)} 初始化。
 * <p>客户端: 响应包到达时调用 {@link #updateConfig(CompoundTag)} 更新。
 */
public abstract class LmaTaskConfigContainer extends TaskConfigContainer {

    protected CompoundTag config = new CompoundTag();

    public LmaTaskConfigContainer(MenuType<?> type, int containerId, Inventory playerInv, int maidId) {
        super(type, containerId, playerInv, maidId);
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

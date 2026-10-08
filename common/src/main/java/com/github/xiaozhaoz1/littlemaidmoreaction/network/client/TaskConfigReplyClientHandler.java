package com.github.xiaozhaoz1.littlemaidmoreaction.network.client;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.LmaTaskConfigContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

/**
 * 任务配置响应包 — 客户端落地处理 (2026-09-21 由 {@code ReplyTaskConfigPacket} 外移)。
 *
 * <p>客户端当前打开的容器菜单若是 {@link LmaTaskConfigContainer} 且 maidId 匹配 ⇒
 * 调 {@code updateConfig(cfg)} 刷新配置屏; 未开屏 / 不是本菜单 / maidId 不匹配 / 无玩家
 * ⇒ 静默跳过 (与迁移前逐字一致)。
 *
 * <p>注: 保留 maidId 与 taskType 的判据只取 maidId (与迁移前一致 — taskType 仅随包携带, 未参与判定)。
 */
@OnlyIn(Dist.CLIENT)
public final class TaskConfigReplyClientHandler {

    private TaskConfigReplyClientHandler() {}

    /** 客户端: 把服务端回包配置写进当前打开的配置菜单 */
    public static void apply(int maidId, CompoundTag config) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.containerMenu instanceof LmaTaskConfigContainer menu
            && menu.getMaid() != null
            && menu.getMaid().getId() == maidId) {
            menu.updateConfig(config);
        }
    }
}

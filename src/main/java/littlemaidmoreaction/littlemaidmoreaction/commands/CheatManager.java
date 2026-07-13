package littlemaidmoreaction.littlemaidmoreaction.commands;

import net.minecraft.server.level.ServerPlayer;

/**
 * v10 权限管理器 — 使用 Minecraft 原生权限阶梯。
 *
 * <h3>权限等级</h3>
 * <ul>
 *   <li>0 — 普通玩家：只能查看规则</li>
 *   <li>2 — OP (默认)：可编辑自己的规则</li>
 *   <li>3 — 高级 OP：可编辑所有规则</li>
 *   <li>4 — 服主/单机作弊：可删除预设</li>
 * </ul>
 *
 * <p>单机：世界开启"允许作弊"时自动获得权限等级 4。
 * 服务器：通过 /op 命令授予。</p>
 */
public final class CheatManager {

    /** 是否可以编辑自己的规则（需要 OP 等级 2+） */
    public static boolean canEditRules(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /** 是否可以编辑所有规则（需要 OP 等级 3+） */
    public static boolean canEditAllRules(ServerPlayer player) {
        return player.hasPermissions(3);
    }

    /** 是否可以删除预设规则（需要 OP 等级 4+） */
    public static boolean canDeletePresets(ServerPlayer player) {
        return player.hasPermissions(4);
    }

    private CheatManager() {}
}

package com.github.xiaozhaoz1.littlemaidmoreaction.compat.numen;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * YSM 命令通道 (v77.8) — 假人 (player) 侧 YSM IO 原语, 镜像 YsmOutput IO 面
 * (maid 侧走 TLM 内建集成, 见 vanilla/output/ysm/YsmOutput)。
 *
 * <p>混淆版 YSM (yes_steve_model, 全混淆无 API) 无 OpenYSM 能力 — 唯一通道 =
 * YSM 官方命令 (命令 = 稳定契约)。目标与参数一律
 * {@link StringArgumentType#escapeIfRequired(String)} — 白名单外字符 (中文/斜杠等)
 * 自动加引号, ASCII 名裸拼; 玩家名路径 (parseNameOrUUID playerName,
 * includesEntities=false) 兼容 EntityArgument.players()。
 *
 * <p>命令面源码实证 (openysm-1.21.1, 与混淆版同代码系): /ysm model set
 * &lt;targets&gt; &lt;model_id&gt; &lt;texture_id&gt; [ignore_auth] / /ysm model disable
 * &lt;targets&gt; &lt;bool&gt; / /ysm anim play &lt;targets&gt; &lt;animation&gt;
 * ("stop" 停止)。全部 requires 权限 2 — console source stack (权限 4) 足够。
 *
 * <p>已知边界 (全实测): ① 目标不能用 UUID — parseNameOrUUID UUID 分支设
 * includesEntities=true → EntityArgument.players() 解析期拒 "只有玩家会受此命令的影响";
 * ② auth 列表拒绝 (need_auth) 不抛异常静默 — 模型在服务端 auth 列表且假人未认证时设置
 * 失败, 需日志/模型检查判断; ③ roamingVars 无服务端命令 (客户端 molang 表达式), 不镜像。
 */
public final class YsmCommandChannel {

    private YsmCommandChannel() {}

    /** 设 YSM 模型 — /ysm model set <玩家> <模型> <贴图> (空贴图 → "-" = 模型默认贴图) */
    public static void setModel(ServerLevel level, ServerPlayer player, String modelId, String texture) {
        String tex = (texture == null || texture.isEmpty()) ? "-" : texture;
        execute(level, "model set " + esc(player) + " " + esc(modelId) + " " + esc(tex));
    }

    /** 禁用/恢复 YSM 模型 — /ysm model disable <玩家> <bool> (true=禁用, 恢复默认模型) */
    public static void disableModel(ServerLevel level, ServerPlayer player, boolean disable) {
        execute(level, "model disable " + esc(player) + " " + disable);
    }

    /** 播放 YSM 动画 — /ysm anim play <玩家> <动画>; null/空 = "stop" 停止 */
    public static void playAnimation(ServerLevel level, ServerPlayer player, String animation) {
        String anim = (animation == null || animation.isEmpty()) ? "stop" : animation;
        execute(level, "anim play " + esc(player) + " " + esc(anim));
    }

    /** 玩家名目标 — escapeIfRequired (中文等自动引号) */
    private static String esc(ServerPlayer player) {
        return StringArgumentType.escapeIfRequired(player.getScoreboardName());
    }

    /** 字符串参数 — escapeIfRequired (模型 id 含 '/' 必引号, 实测) */
    private static String esc(String s) {
        return StringArgumentType.escapeIfRequired(s);
    }

    private static void execute(ServerLevel level, String subCommand) {
        try {
            String cmd = "ysm " + subCommand;
            level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withPermission(4), cmd);
            LittleMaidMoreAction.LOGGER.info("[YsmCommand] cmd={}", cmd);
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.warn("[YsmCommand] YSM 命令异常: {}", ex.toString());
        }
    }
}

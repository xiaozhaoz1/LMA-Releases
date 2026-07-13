package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import java.util.List;
import java.util.Map;

/** 发送消息 — 支持 chat/action_bar/status(BossBar) 三种类型，变量替换 {maid}/{target}。 */
@RuleAction
public final class SendMessageAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("message","消息内容",""),
        new TypedParam.SelectParam("type","消息类型","chat",List.of("chat","action_bar","status"))
    );
    @Override public String id() { return "send_message"; }
    @Override public String displayName() { return "发送消息"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        String msg = raw.getOrDefault("message",""); if (msg.isEmpty()) return;
        msg = msg.replace("{maid}",ctx.maid().getName().getString()).replace("{target}",ctx.target()!=null?ctx.target().getName().getString():"");
        ServerPlayer owner = (ServerPlayer) ctx.maid().level().getPlayerByUUID(ctx.maid().getOwnerUUID());
        if (owner == null) return;
        String type = raw.getOrDefault("type","chat");
        Component c = Component.literal(msg);
        switch (type) {
            case "action_bar" -> owner.displayClientMessage(c,true);
            case "status" -> {
                var boss = new ServerBossEvent(c, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
                boss.setVisible(true);
                owner.connection.send(ClientboundBossEventPacket.createAddPacket(boss));
                ctx.maid().level().getServer().tell(new net.minecraft.server.TickTask(60,()->owner.connection.send(ClientboundBossEventPacket.createRemovePacket(boss.getId()))));
            }
            default -> owner.sendSystemMessage(c);
        }
    }
}

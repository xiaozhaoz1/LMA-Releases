package littlemaidmoreaction.littlemaidmoreaction.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.debug.RuleTracer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * v10 /LMA 命令 — 规则编辑器入口 + 调试追踪。
 *
 * <p>用法：
 * <ul>
 *   <li>{@code /LMA rule}   — 打开规则编辑器 (需要 hasPermission(2))</li>
 *   <li>{@code /LMA trace on|off|live|list|clear} — 规则追踪 (需要 hasPermission(2))</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class LmaCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("LMA")
            .requires(s -> s.hasPermission(2))
            .then(Commands.literal("rule")
                .executes(LmaCommand::openRuleEditor))
            .then(Commands.literal("trace")
                .then(Commands.literal("on").executes(LmaCommand::enableTrace))
                .then(Commands.literal("off").executes(LmaCommand::disableTrace))
                .then(Commands.literal("live").executes(LmaCommand::liveTrace))
                .then(Commands.literal("list").executes(LmaCommand::listTrace))
                .then(Commands.literal("clear").executes(LmaCommand::clearTrace)))
        );
    }

    // ── rule ──

    private static int openRuleEditor(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal("§a[LMA] 规则编辑器请通过 Forge 模组列表 → LittleMaidMoreAction 打开"),
            false);
        return 1;
    }

    // ── trace ──

    private static int enableTrace(CommandContext<CommandSourceStack> ctx) {
        RuleTracer.setEnabled(true);
        ctx.getSource().sendSuccess(() ->
            Component.literal("§a[LMA] 规则追踪已启用 §7(/LMA trace live 开启实时消息)"), false);
        return 1;
    }

    private static int disableTrace(CommandContext<CommandSourceStack> ctx) {
        RuleTracer.setEnabled(false);
        RuleTracer.setLiveMessages(false);
        ctx.getSource().sendSuccess(() ->
            Component.literal("§e[LMA] 规则追踪已禁用"), false);
        return 1;
    }

    private static int liveTrace(CommandContext<CommandSourceStack> ctx) {
        RuleTracer.setEnabled(true);
        RuleTracer.setLiveMessages(true);
        ctx.getSource().sendSuccess(() ->
            Component.literal("§a[LMA] 实时消息已开启 §7(规则触发时将向主人玩家输出条件/动作详情)"), false);
        return 1;
    }

    private static int listTrace(CommandContext<CommandSourceStack> ctx) {
        List<RuleTracer.TraceRecord> history = RuleTracer.history();
        if (history.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("§7[LMA] 无追踪记录 §8(先 /LMA trace on)"), false);
            return 0;
        }
        int count = Math.min(history.size(), 10);
        ctx.getSource().sendSuccess(() ->
            Component.literal("§6═══ LMA 规则追踪 §7(最近" + count + "条) ═══"), false);
        for (int i = 0; i < count; i++) {
            RuleTracer.TraceRecord r = history.get(i);
            String status = r.matched ? "§a✓" : "§c✗";
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s §f#%d §7'%s' §8事件=%s §7耗时=%dms §8条件=%d 动作=%d",
                status, r.ruleId, r.ruleName, r.eventId, r.durationMs,
                r.conditions.size(), r.actions.size())), false);
            // 条件详情
            if (!r.conditions.isEmpty()) {
                for (RuleTracer.ConditionResult c : r.conditions) {
                    String cs = c.passed() ? "§a✓" : "§c✗";
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                        "    %s §8%s §7%s §f%s §8→ %s",
                        cs, c.key(), c.operator() != null ? c.operator() : "bool",
                        c.expected(), c.actual())), false);
                }
            }
            // 动作详情
            if (!r.actions.isEmpty()) {
                for (RuleTracer.ActionResult a : r.actions) {
                    String as = a.success() ? "§aOK" : "§cFAIL";
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                        "    §7[%d] §f%s §8→ %s",
                        a.stepIndex() + 1, a.actionId(), as)), false);
                }
            }
        }
        return count;
    }

    private static int clearTrace(CommandContext<CommandSourceStack> ctx) {
        RuleTracer.clear();
        ctx.getSource().sendSuccess(() ->
            Component.literal("§a[LMA] 追踪记录已清空"), false);
        return 1;
    }

    private LmaCommand() {}
}

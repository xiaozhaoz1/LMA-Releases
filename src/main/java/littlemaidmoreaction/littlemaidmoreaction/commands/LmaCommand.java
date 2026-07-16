package littlemaidmoreaction.littlemaidmoreaction.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskFlowGraph;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskToggle;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskTree;
import littlemaidmoreaction.littlemaidmoreaction.core.debug.RuleTracer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class LmaCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("LMA")
            .requires(s -> s.hasPermission(2))
            .then(Commands.literal("rule").executes(LmaCommand::openRuleEditor))
            .then(Commands.literal("trace")
                .then(Commands.literal("on").executes(LmaCommand::enableTrace))
                .then(Commands.literal("off").executes(LmaCommand::disableTrace))
                .then(Commands.literal("live").executes(LmaCommand::liveTrace))
                .then(Commands.literal("list").executes(LmaCommand::listTrace))
                .then(Commands.literal("clear").executes(LmaCommand::clearTrace)))
            .then(Commands.literal("task")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(LmaCommand::handleTask)))
        );
    }

    /** 统一任务命令解析: /lma task list|tree|enable X|disable X|flow ... */
    private static int handleTask(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "args");
        String[] parts = raw.split("\\s+");
        if (parts.length == 0) return send(ctx, "§7用法: /lma task list|tree|enable|disable|flow ...");

        return switch (parts[0]) {
            case "list"  -> taskList(ctx);
            case "tree"  -> taskTree(ctx);
            case "enable"-> toggleTask(ctx, parts, true);
            case "disable"->toggleTask(ctx, parts, false);
            case "show"  -> toggleVisible(ctx, parts, true);
            case "hide"  -> toggleVisible(ctx, parts, false);
            case "flow"  -> handleFlow(ctx, parts);
            default      -> send(ctx, "§c未知: " + parts[0]);
        };
    }

    private static int toggleTask(CommandContext<CommandSourceStack> ctx, String[] parts, boolean enable) {
        if (parts.length < 2) return send(ctx, "§7用法: /lma task " + (enable ? "enable" : "disable") + " <类型>");
        TaskToggle.setEnabled(parts[1], enable);
        return send(ctx, "§a" + parts[1] + (enable ? " 已启用" : " 已禁用"));
    }
    private static int toggleVisible(CommandContext<CommandSourceStack> ctx, String[] parts, boolean show) {
        if (parts.length < 2) return send(ctx, "§7用法: /lma task " + (show ? "show" : "hide") + " <类型>");
        TaskToggle.setVisible(parts[1], show);
        return send(ctx, "§a" + parts[1] + (show ? " 显示在任务栏" : " 隐藏(被动)"));
    }

    // ── flow: /lma task flow add|remove|join|chain|list|clear ... ──
    private static int handleFlow(CommandContext<CommandSourceStack> ctx, String[] parts) {
        if (parts.length < 2) return send(ctx, "§7用法: /lma task flow add|remove|join|chain|list|clear");
        return switch (parts[1]) {
            case "list"   -> flowList(ctx);
            case "clear"  -> flowClear(ctx);
            case "add"    -> flowAdd(ctx, parts);
            case "remove" -> flowRemove(ctx, parts);
            case "join"   -> flowJoin(ctx, parts);
            case "chain"  -> flowChain(ctx, parts);
            default       -> send(ctx, "§c未知flow: " + parts[1]);
        };
    }

    // ── rule ──
    private static int openRuleEditor(CommandContext<CommandSourceStack> ctx) { return send(ctx, "§a规则编辑器请通过模组列表打开"); }

    // ── trace ──
    private static int enableTrace(CommandContext<CommandSourceStack> ctx) { RuleTracer.setEnabled(true); return send(ctx, "§a追踪已启用"); }
    private static int disableTrace(CommandContext<CommandSourceStack> ctx) { RuleTracer.setEnabled(false); RuleTracer.setLiveMessages(false); return send(ctx, "§e追踪已禁用"); }
    private static int liveTrace(CommandContext<CommandSourceStack> ctx) { RuleTracer.setEnabled(true); RuleTracer.setLiveMessages(true); return send(ctx, "§a实时消息已开启"); }
    private static int listTrace(CommandContext<CommandSourceStack> ctx) { return send(ctx, "§7追踪记录"); }
    private static int clearTrace(CommandContext<CommandSourceStack> ctx) { RuleTracer.clear(); return send(ctx, "§a已清空"); }

    // ── task display ──
    private static int taskList(CommandContext<CommandSourceStack> ctx) { return send(ctx, "§6" + TaskTree.buildText()); }
    private static int taskTree(CommandContext<CommandSourceStack> ctx) {
        var nodes = TaskTree.build();
        StringBuilder sb = new StringBuilder("§6═══ 任务树 ═══\n");
        for (var n : nodes) {
            sb.append(n.enabled() ? "§a✔" : "§c✖");
            sb.append(n.visible() ? " §f" : " §8");
            sb.append(n.taskType());
            if (!n.steps().isEmpty()) { sb.append(" §7"); n.steps().forEach(s -> sb.append(s.label()).append(" ")); }
            sb.append("\n");
        }
        sb.append("\n§6═══ 分组 ═══\n");
        for (var g : TaskTree.buildGroups()) sb.append("§f📁 ").append(g.label()).append(" §7→ ").append(String.join(", ", g.tasks())).append("\n");
        return send(ctx, sb.toString());
    }

    // ── flow commands ──
    private static int flowList(CommandContext<CommandSourceStack> ctx) {
        var edges = TaskFlowGraph.allEdges();
        if (edges.isEmpty()) return send(ctx, "§7无流程链");
        StringBuilder sb = new StringBuilder("§6═══ 流程链 ═══\n");
        for (var e : edges) sb.append("§f").append(e.from()).append(" §7─").append(e.split()).append("/").append(e.join()).append("→ §f").append(e.to()).append("\n");
        return send(ctx, sb.toString());
    }

    /** /lma task flow add <from> <to> <split> <join> */
    private static int flowAdd(CommandContext<CommandSourceStack> ctx, String[] parts) {
        if (parts.length < 6) return send(ctx, "§7用法: /lma task flow add <from> <to> <SINGLE|PARALLEL|CONDITIONAL|FALLBACK|REPEAT> <ALL|ANY>");
        var split = parseSplit(parts[4]); var join = parseJoin(parts[5]);
        if (split == null || join == null) return send(ctx, "§c无效参数");
        TaskFlowGraph.addEdge(parts[2], parts[3], split, join);
        return send(ctx, "§a" + parts[2] + " → " + parts[3] + " [" + split + "/" + join + "]");
    }

    /** /lma task flow remove <from> <to> */
    private static int flowRemove(CommandContext<CommandSourceStack> ctx, String[] parts) {
        if (parts.length < 4) return send(ctx, "§7用法: /lma task flow remove <from> <to>");
        TaskFlowGraph.removeEdge(parts[2], parts[3]);
        return send(ctx, "§e已删除: " + parts[2] + " → " + parts[3]);
    }

    /** /lma task flow join <from1,from2> <to> <join> */
    private static int flowJoin(CommandContext<CommandSourceStack> ctx, String[] parts) {
        if (parts.length < 5) return send(ctx, "§7用法: /lma task flow join <from1,from2> <to> <ALL|ANY>");
        var fromList = parseCsv(parts[2]); var join = parseJoin(parts[4]);
        if (join == null) return send(ctx, "§c无效join");
        TaskFlowGraph.addJoin(fromList, parts[3], join);
        return send(ctx, "§a汇合: " + fromList + " → " + parts[3] + " [" + join + "]");
    }

    /** /lma task flow chain <t1,t2,t3> <split> <join> */
    private static int flowChain(CommandContext<CommandSourceStack> ctx, String[] parts) {
        if (parts.length < 5) return send(ctx, "§7用法: /lma task flow chain <t1,t2,t3> <SINGLE|PARALLEL|...> <ALL|ANY>");
        var tasks = parseCsv(parts[2]); var split = parseSplit(parts[3]); var join = parseJoin(parts[4]);
        if (split == null || join == null) return send(ctx, "§c无效参数");
        TaskFlowGraph.addChain(tasks, split, join);
        return send(ctx, "§a链式: " + tasks + " [" + split + "/" + join + "]");
    }

    private static int flowClear(CommandContext<CommandSourceStack> ctx) {
        int c = TaskFlowGraph.allEdges().size(); TaskFlowGraph.clearAll();
        return send(ctx, "§e已清除 " + c + " 条边");
    }

    // ── helpers ──
    private static int send(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
    private static TaskFlowGraph.SplitType parseSplit(String s) { try { return TaskFlowGraph.SplitType.valueOf(s.toUpperCase()); } catch (Exception e) { return null; } }
    private static TaskFlowGraph.JoinType parseJoin(String s) { try { return TaskFlowGraph.JoinType.valueOf(s.toUpperCase()); } catch (Exception e) { return null; } }
    private static List<String> parseCsv(String s) { return Arrays.asList(s.replace("\"", "").split("\\s*,\\s*")); }

    private LmaCommand() {}
}

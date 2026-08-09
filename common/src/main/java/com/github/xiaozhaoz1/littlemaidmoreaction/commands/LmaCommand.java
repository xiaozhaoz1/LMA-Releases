package com.github.xiaozhaoz1.littlemaidmoreaction.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.TaskTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
//? if 1.20.1 {
import net.minecraftforge.event.RegisterCommandsEvent;
//?} else {
import net.neoforged.neoforge.event.RegisterCommandsEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}


//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class LmaCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("LMA")
            .requires(s -> s.hasPermission(2))
            .then(Commands.literal("task")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(LmaCommand::handleTask)))
        );
    }

    /** 统一任务命令解析: /lma task list|tree|enable X|disable X|reload */
    private static int handleTask(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "args");
        String[] parts = raw.split("\\s+");
        if (parts.length == 0) return send(ctx, "§7用法: /lma task list|tree|enable|disable|show|hide|reload");

        return switch (parts[0]) {
            case "list"  -> taskList(ctx);
            case "tree"  -> taskTree(ctx);
            case "enable"-> toggleTask(ctx, parts, true);
            case "disable"->toggleTask(ctx, parts, false);
            case "show"  -> toggleVisible(ctx, parts, true);
            case "hide"  -> toggleVisible(ctx, parts, false);
            case "debug"  -> taskDebug(ctx, parts);   // v76 Phase 5: 任务运行时快照
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

    // ── task display ──

    /** v76 Phase 5: /lma task debug [uuid] — 任务运行时快照 (状态/游标/最后转换/最后错误) */
    private static int taskDebug(CommandContext<CommandSourceStack> ctx, String[] parts) {
        java.util.UUID filter = null;
        if (parts.length >= 2) {
            try {
                filter = java.util.UUID.fromString(parts[1]);
            } catch (IllegalArgumentException e) {
                return send(ctx, "§7用法: /lma task debug [uuid]");
            }
        }
        var server = ctx.getSource().getServer();
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (var level : server.getAllLevels()) {
            for (var e : level.getEntities().getAll()) {
                if (!(e instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) continue;
                if (filter != null && !maid.getUUID().equals(filter)) continue;
                count++;
                String task = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid);
                if (task == null || task.isEmpty()) {
                    sb.append("§8").append(maid.getName().getString()).append(": §7空闲\n");
                    continue;
                }
                var handler = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get(task);
                if (handler == null) {
                    sb.append("§8").append(maid.getName().getString()).append(": §7任务 ").append(task).append(" 未注册\n");
                    continue;
                }
                sb.append("§8").append(maid.getName().getString()).append("\n");
                // v79.6: FSM 调试分支随 FsmPipeline 删除 — 统一代码管线提示
                sb.append("§7代码管线 ").append(task).append(" (无调试快照)\n");
            }
        }
        if (count == 0) return send(ctx, "§7无匹配女仆");
        return send(ctx, sb.toString());
    }

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

    // ── helpers ──
    private static int send(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private LmaCommand() {}
}

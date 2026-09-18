package com.github.xiaozhaoz1.littlemaidmoreaction.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.TaskTree;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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

import java.util.List;
import java.util.stream.Collectors;

/**
 * v79.62.5 重写为正式 Brigadier 命令树 — 子命令逐级 literal + 参数 suggests 补全
 * (原 greedyString 手拆无 Tab 补全 — 用户实测不会用)。
 *
 * <pre>/lma
 *  ├─ task list | tree | debug [uuid]
 *  ├─ task enable|disable|show|hide &lt;type&gt;   (Tab 补全任务类型)
 *  ├─ festival &lt;name&gt;                         (Tab 补全节日 id/名)
 *  └─ structure fire &lt;kind&gt; | state | reset   (fire Tab 补全 discover/refresh/enter/leave)
 * </pre>
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class LmaCommand {

    // ── 补全数据源 ──

    /** 任务类型建议 (Tab: /lma task enable &lt;type&gt;) — 注册表全量 */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TASK = (ctx, b) ->
            SharedSuggestionProvider.suggest(TaskRegistry.taskTypes().stream().sorted().collect(Collectors.toList()), b);

    /** 节日建议 (Tab: /lma festival &lt;name&gt;) — 节日表 id + 名称 */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_FESTIVAL = (ctx, b) -> {
        // ★ v79.64 (用户实测: "怎么还有中文" ✗): 原实现把 **id + 中文名** 都列进 Tab 建议 ✗ —
        //   而参数类型是 word() (只认 ASCII ✗) ⇒ 用户 Tab 选到中文 ⇒ 必被拒 ✓
        //   ⇒ 现**只给 id** ✓ (handleFestival 里 FestivalTable.byName 两者都认 ✓ 但用法统一走 id ✓)
        List<String> names = com.github.xiaozhaoz1.littlemaidmoreaction.storage.FestivalTable.all().stream()
                .map(f -> f.id())
                .collect(Collectors.toList());
        return SharedSuggestionProvider.suggest(names, b);
    };

    /** 结构信号建议 (Tab: /lma structure fire &lt;kind&gt;) */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_STRUCT_KIND = (ctx, b) ->
            SharedSuggestionProvider.suggest(List.of("discover", "refresh", "enter", "leave"), b);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("lma")
            .requires(s -> s.hasPermission(2))
            // ── task ──
            .then(Commands.literal("task")
                .then(Commands.literal("list").executes(LmaCommand::handleTaskList))
                .then(Commands.literal("tree").executes(LmaCommand::handleTaskTree))
                .then(Commands.literal("debug")
                    .executes(LmaCommand::handleTaskDebug)
                    .then(Commands.argument("uuid", StringArgumentType.word())
                        .executes(LmaCommand::handleTaskDebug)))
                .then(Commands.literal("enable")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(SUGGEST_TASK)
                        .executes(ctx -> toggleTask(ctx, true))))
                .then(Commands.literal("disable")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(SUGGEST_TASK)
                        .executes(ctx -> toggleTask(ctx, false))))
                .then(Commands.literal("show")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(SUGGEST_TASK)
                        .executes(ctx -> toggleVisible(ctx, true))))
                .then(Commands.literal("hide")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(SUGGEST_TASK)
                        .executes(ctx -> toggleVisible(ctx, false)))))
            // ── festival ──
            .then(Commands.literal("festival")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(SUGGEST_FESTIVAL)
                    .executes(LmaCommand::handleFestival)))
            // ── structure ──
            .then(Commands.literal("structure")
                .then(Commands.literal("fire")
                    .then(Commands.argument("kind", StringArgumentType.word())
                        .suggests(SUGGEST_STRUCT_KIND)
                        .executes(LmaCommand::handleStructureFire)))
                .then(Commands.literal("state").executes(LmaCommand::handleStructureState))
                .then(Commands.literal("reset").executes(LmaCommand::handleStructureReset)))
        );
    }

    // ── task handlers ──

    /** /lma task list — 任务列表 */
    private static int handleTaskList(CommandContext<CommandSourceStack> ctx) {
        return send(ctx, TaskTree.buildText());
    }

    /** /lma task tree — 任务树带步骤/分组 */
    private static int handleTaskTree(CommandContext<CommandSourceStack> ctx) {
        var nodes = TaskTree.build();
        StringBuilder sb = new StringBuilder("§6═══ 任务树 ═══\n");
        for (var n : nodes) {
            sb.append(n.enabled() ? "§a✔" : "§c✖");
            sb.append(n.visible() ? " §f" : " §8");
            sb.append(n.taskType());
            if (!n.steps().isEmpty()) {
                sb.append(" §7");
                n.steps().forEach(s -> sb.append(s.label()).append(" "));
            }
            sb.append("\n");
        }
        sb.append("\n§6═══ 分组 ═══\n");
        for (var g : TaskTree.buildGroups()) {
            sb.append("§f📁 ").append(g.label()).append(" §7→ ").append(String.join(", ", g.tasks())).append("\n");
        }
        return send(ctx, sb.toString());
    }

    /** /lma task debug [uuid] — 任务运行时快照 */
    private static int handleTaskDebug(CommandContext<CommandSourceStack> ctx) {
        String uuidStr = null;
        try {
            uuidStr = StringArgumentType.getString(ctx, "uuid");
        } catch (IllegalArgumentException e) {
            // 无 uuid 参数 — 全部女仆
        }
        java.util.UUID filter = null;
        if (uuidStr != null) {
            try {
                filter = java.util.UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                return send(ctx, "§c非法 uuid: " + uuidStr + " (§7用法: /lma task debug [uuid])");
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
                var handler = TaskRegistry.get(task);
                if (handler == null) {
                    sb.append("§8").append(maid.getName().getString()).append(": §7任务 ").append(task).append(" 未注册\n");
                    continue;
                }
                sb.append("§8").append(maid.getName().getString()).append("\n");
                sb.append("§7代码管线 ").append(task).append(" (无调试快照)\n");
            }
        }
        if (count == 0) return send(ctx, "§7无匹配女仆");
        return send(ctx, sb.toString());
    }

    /** /lma task enable|disable &lt;type&gt; */
    private static int toggleTask(CommandContext<CommandSourceStack> ctx, boolean enable) {
        String type = StringArgumentType.getString(ctx, "type");
        if (TaskRegistry.get(type) == null) return send(ctx, "§c未知任务类型: " + type + " (Tab 补全列表)");
        TaskToggle.setEnabled(type, enable);
        return send(ctx, "§a" + type + (enable ? " 已启用" : " 已禁用"));
    }

    /** /lma task show|hide &lt;type&gt; */
    private static int toggleVisible(CommandContext<CommandSourceStack> ctx, boolean show) {
        String type = StringArgumentType.getString(ctx, "type");
        if (TaskRegistry.get(type) == null) return send(ctx, "§c未知任务类型: " + type + " (Tab 补全列表)");
        TaskToggle.setVisible(type, show);
        return send(ctx, "§a" + type + (show ? " 显示在任务栏" : " 隐藏(被动)"));
    }

    // ── festival ──

    /** /lma festival &lt;name&gt; — 触发节日信号 (调试节日礼物/气泡) */
    private static int handleFestival(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return send(ctx, "§c该指令需玩家执行");
        }
        String name = StringArgumentType.getString(ctx, "name");
        var f = com.github.xiaozhaoz1.littlemaidmoreaction.storage.FestivalTable.byName(name);
        if (f == null) return send(ctx, "§c未知节日: " + name + " (Tab 补全; 空表 = 节日数据未加载)");
        var level = (net.minecraft.server.level.ServerLevel) sp.level();
        int triggered = 0;
        for (var e : level.getEntities().getAll()) {
            if (!(e instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) continue;
            if (maid.getOwner() != sp) continue;
            com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.FestivalPassiveTask
                    .debugTrigger(level, maid, f);
            triggered++;
        }
        if (triggered == 0) return send(ctx, "§7附近没有你的女仆 (需先驯服女仆且在同维度)");
        return send(ctx, "§a已触发: " + f.name() + " → " + triggered + " 只女仆");
    }

    // ── structure ──

    /** /lma structure fire &lt;kind&gt; — 立即发射结构信号 (discover/refresh/enter/leave) */
    private static int handleStructureFire(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return send(ctx, "§c该指令需玩家执行");
        }
        String kind = StringArgumentType.getString(ctx, "kind");
        String result = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.StructureSense.debugFire(
                (net.minecraft.server.level.ServerLevel) sp.level(), sp, kind);
        return send(ctx, "§6[结构调试] §f" + result);
    }

    /** /lma structure state — 结构状态机与文案缓存快照 */
    private static int handleStructureState(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return send(ctx, "§c该指令需玩家执行");
        }
        return send(ctx, "§6[结构状态] §f" + com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.StructureSense.debugState(sp.getUUID()));
    }

    /** /lma structure reset — 清空结构缓存 (重测首次发现) */
    private static int handleStructureReset(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return send(ctx, "§c该指令需玩家执行");
        }
        return send(ctx, "§6[结构重置] §f" + com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.StructureSense.debugReset(sp.getUUID()));
    }

    // ── helpers ──
    private static int send(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private LmaCommand() {}
}

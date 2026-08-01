package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.network.ConfigSyncPacket;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import littlemaidmoreaction.littlemaidmoreaction.config.ActiveTaskConfig;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.config.PassiveTaskConfig;

/**
 * v67.2: Cloth Config 设置屏 — 模组全部配置项 + 任务自定义入口。
 *
 * <p>入口: {@link LMAConfigScreen}「详细设置」按钮。
 * 结构:
 * <ul>
 *   <li>全局分类: 规则引擎 / 调试 / 连锁采集 / 环境感知 / <b>右键交互</b> (木棍/距离直列)</li>
 *   <li><b>任务自定义</b> 分类: 每个任务一个 {@link ButtonEntry} → {@link TaskSettingsScreen} 子屏</li>
 * </ul>
 *
 * <p>API 模式对齐 TLM {@code compat/cloth/MenuIntegration}: ConfigBuilder + entryBuilder,
 * 每项 setDefaultValue + setTooltip + setSaveConsumer, 保存回调统一 {@link MoreActionConfig#saveAll()} (v67.6)。
 */
public final class ClothSettingsScreen {

    private ClothSettingsScreen() {}

    /** 构建设置屏 — 返回 cloth 生成的 Screen, 由调用方 setScreen */
    public static Screen create(Screen parent) {
        ConfigBuilder root = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("LittleMaidMoreAction 设置"));
        ConfigEntryBuilder eb = root.entryBuilder();

        // ── 规则引擎 ──
        ConfigCategory rules = root.getOrCreateCategory(Component.literal("规则引擎"));
        rules.addEntry(eb.startBooleanToggle(Component.literal("规则引擎总开关"),
                        MoreActionConfig.CUSTOM_RULES_ENABLED.get())
                .setDefaultValue(MoreActionConfig.CUSTOM_RULES_ENABLED.getDefault())
                .setTooltip(Component.literal("关闭后所有预设及自定义规则均不触发"))
                .setSaveConsumer(MoreActionConfig.CUSTOM_RULES_ENABLED::set).build());

        // ── 调试 ──
        ConfigCategory debug = root.getOrCreateCategory(Component.literal("调试"));
        debug.addEntry(eb.startBooleanToggle(Component.literal("调试模式"),
                        MoreActionConfig.DEBUG_MODE.get())
                .setDefaultValue(MoreActionConfig.DEBUG_MODE.getDefault())
                .setTooltip(Component.literal("调试模式：日志 + 聊天栏输出"))
                .setSaveConsumer(MoreActionConfig.DEBUG_MODE::set).build());

        // ── 连锁采集 ──
        ConfigCategory chain = root.getOrCreateCategory(Component.literal("连锁采集"));
        chain.addEntry(eb.startIntField(Component.literal("单次最大方块数"),
                        ActiveTaskConfig.CHAIN_MAX_BLOCKS.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_MAX_BLOCKS.getDefault())
                .setMin(1).setMax(1024)
                .setTooltip(Component.literal("连锁采集(砍树/挖矿)单次最大方块数"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_MAX_BLOCKS::set).build());
        chain.addEntry(eb.startBooleanToggle(Component.literal("砍树校验天然树"),
                        ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.getDefault())
                .setTooltip(Component.literal("防止女仆拆玩家木建筑"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK::set).build());
        chain.addEntry(eb.startIntField(Component.literal("扫描间隔 (tick)"),
                        ActiveTaskConfig.CHAIN_SCAN_INTERVAL.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_SCAN_INTERVAL.getDefault())
                .setMin(20).setMax(1200)
                .setTooltip(Component.literal("无目标扫描频率, 60 = 3 秒"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_SCAN_INTERVAL::set).build());
        chain.addEntry(eb.startIntField(Component.literal("最大采集距离 (格)"),
                        ActiveTaskConfig.CHAIN_MAX_DISTANCE.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_MAX_DISTANCE.getDefault())
                .setMin(4).setMax(128)
                .setTooltip(Component.literal("连锁 BFS 搜索与整脉破坏距离上限"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_MAX_DISTANCE::set).build());

        // ── 环境感知 ──
        ConfigCategory env = root.getOrCreateCategory(Component.literal("环境感知"));
        env.addEntry(eb.startBooleanToggle(Component.literal("环境感知总开关"),
                        PassiveTaskConfig.ENVSENSE_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.ENVSENSE_ENABLED.getDefault())
                .setTooltip(Component.literal("false=女仆不接收任何环境信号 (v63)"))
                .setSaveConsumer(PassiveTaskConfig.ENVSENSE_ENABLED::set).build());
        env.addEntry(eb.startIntField(Component.literal("扫描间隔 (tick)"),
                        PassiveTaskConfig.ENV_SCAN_INTERVAL.get())
                .setDefaultValue(PassiveTaskConfig.ENV_SCAN_INTERVAL.getDefault())
                .setMin(20).setMax(1200)
                .setTooltip(Component.literal("环境感知扫描间隔，默认 200 = 10秒"))
                .setSaveConsumer(PassiveTaskConfig.ENV_SCAN_INTERVAL::set).build());
        env.addEntry(eb.startIntField(Component.literal("默认扫描半径"),
                        PassiveTaskConfig.ENV_DEFAULT_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_DEFAULT_RADIUS.getDefault())
                .setMin(4).setMax(64)
                .setTooltip(Component.literal("无工作范围时的默认扫描半径"))
                .setSaveConsumer(PassiveTaskConfig.ENV_DEFAULT_RADIUS::set).build());
        env.addEntry(eb.startIntField(Component.literal("每感知器命中上限"),
                        PassiveTaskConfig.ENV_MAX_HITS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_MAX_HITS.getDefault())
                .setMin(1).setMax(256)
                .setTooltip(Component.literal("每感知器命中结果上限"))
                .setSaveConsumer(PassiveTaskConfig.ENV_MAX_HITS::set).build());
        env.addEntry(eb.startDoubleField(Component.literal("太冷阈值"),
                        PassiveTaskConfig.ENV_COLD_THRESHOLD.get())
                .setDefaultValue(PassiveTaskConfig.ENV_COLD_THRESHOLD.getDefault())
                .setMin(-1.0).setMax(2.0)
                .setTooltip(Component.literal("低于此值触发 env_too_cold (TLM COLD 档默认 0.15)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_COLD_THRESHOLD::set).build());
        env.addEntry(eb.startDoubleField(Component.literal("太热阈值"),
                        PassiveTaskConfig.ENV_HOT_THRESHOLD.get())
                .setDefaultValue(PassiveTaskConfig.ENV_HOT_THRESHOLD.getDefault())
                .setMin(0.0).setMax(2.0)
                .setTooltip(Component.literal("高于此值触发 env_too_hot (TLM 判热默认 1.0)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_HOT_THRESHOLD::set).build());
        env.addEntry(eb.startIntField(Component.literal("玩家门控半径"),
                        PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS.getDefault())
                .setMin(0).setMax(256)
                .setTooltip(Component.literal("仅此范围内女仆参与环境感知, 0=不门控"))
                .setSaveConsumer(PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS::set).build());
        env.addEntry(eb.startIntField(Component.literal("黑暗判定亮度阈值"),
                        PassiveTaskConfig.ENV_DARKNESS_THRESHOLD.get())
                .setDefaultValue(PassiveTaskConfig.ENV_DARKNESS_THRESHOLD.getDefault())
                .setMin(0).setMax(15)
                .setTooltip(Component.literal("低于此值触发 env_darkness (怪物生成亮度默认 7)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_DARKNESS_THRESHOLD::set).build());
        env.addEntry(eb.startBooleanToggle(Component.literal("结构探测总开关"),
                        PassiveTaskConfig.ENV_STRUCTURE_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_ENABLED.getDefault())
                .setTooltip(Component.literal("村庄/矿井/前哨站探测 (findNearestMapStructure 较慢)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_ENABLED::set).build());
        env.addEntry(eb.startIntField(Component.literal("结构探测间隔 (tick)"),
                        PassiveTaskConfig.ENV_STRUCTURE_INTERVAL.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_INTERVAL.getDefault())
                .setMin(1200).setMax(168000)
                .setTooltip(Component.literal("默认 24000 = 1 MC 天"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_INTERVAL::set).build());
        env.addEntry(eb.startIntField(Component.literal("结构探测半径 (区块)"),
                        PassiveTaskConfig.ENV_STRUCTURE_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_RADIUS.getDefault())
                .setMin(1).setMax(32)
                .setTooltip(Component.literal("越大越慢"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_RADIUS::set).build());

        // ── 右键交互 (v67.2 全局直列) ──
        ConfigCategory bi = root.getOrCreateCategory(Component.literal("右键交互"));
        bi.addEntry(eb.startTextField(Component.literal("标记物品 (右键方块)"),
                        ActiveTaskConfig.BI_MARK_ITEM.get())
                .setDefaultValue(ActiveTaskConfig.BI_MARK_ITEM.getDefault())
                .setTooltip(Component.literal("右键方块标记目标用的物品 (物品id, 如 minecraft:stick)"))
                .setSaveConsumer(ActiveTaskConfig.BI_MARK_ITEM::set).build());
        bi.addEntry(eb.startTextField(Component.literal("绑定物品 (右键女仆)"),
                        ActiveTaskConfig.BI_BIND_ITEM.get())
                .setDefaultValue(ActiveTaskConfig.BI_BIND_ITEM.getDefault())
                .setTooltip(Component.literal("右键女仆绑定任务用的物品 (物品id)"))
                .setSaveConsumer(ActiveTaskConfig.BI_BIND_ITEM::set).build());
        bi.addEntry(eb.startDoubleField(Component.literal("交互距离 (格)"),
                        ActiveTaskConfig.BI_INTERACT_DISTANCE.get())
                .setDefaultValue(ActiveTaskConfig.BI_INTERACT_DISTANCE.getDefault())
                .setMin(1.0).setMax(16.0)
                .setTooltip(Component.literal("女仆右键交互的距离上限"))
                .setSaveConsumer(ActiveTaskConfig.BI_INTERACT_DISTANCE::set).build());
        bi.addEntry(eb.startDoubleField(Component.literal("按键触发范围 (格)"),
                        ActiveTaskConfig.BI_TRIGGER_RANGE.get())
                .setDefaultValue(ActiveTaskConfig.BI_TRIGGER_RANGE.getDefault())
                .setMin(5.0).setMax(64.0)
                .setTooltip(Component.literal("按键触发时扫描玩家周围女仆的范围"))
                .setSaveConsumer(ActiveTaskConfig.BI_TRIGGER_RANGE::set).build());
        bi.addEntry(eb.startIntField(Component.literal("定时器默认间隔 (tick)"),
                        ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get())
                .setDefaultValue(ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.getDefault())
                .setMin(20).setMax(12000)
                .setTooltip(Component.literal("定时器默认间隔, 200 = 10秒"))
                .setSaveConsumer(ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL::set).build());

        // ── 任务自定义 (v67.2: 每任务一个按钮 → TaskSettingsScreen 子屏) ──
        ConfigCategory tasks = root.getOrCreateCategory(Component.literal("任务自定义"));
        var taskTypes = new ArrayList<>(TaskRegistry.taskTypes());
        taskTypes.sort(Comparator.naturalOrder());
        for (String taskType : taskTypes) {
            tasks.addEntry(new ButtonEntry(TaskSettingsScreen.title(taskType),
                    Component.literal("自定义"),
                    () -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.setScreen(TaskSettingsScreen.create(mc.screen, taskType));
                    }));
        }

        root.setSavingRunnable(() -> {
            MoreActionConfig.saveAll();
            if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) {
                ConfigSyncPacket.send();
            }
        });
        return root.build();
    }
}

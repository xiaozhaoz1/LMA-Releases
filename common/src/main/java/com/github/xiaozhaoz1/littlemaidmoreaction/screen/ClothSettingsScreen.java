package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.ConfigSyncPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;

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
        // v79.26.6: 挖矿兜底行为参数 (原行内魔法数/类内常量 → 配置)
        chain.addEntry(eb.startIntField(Component.literal("垂直挖穿深度 (格)"),
                        ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.getDefault())
                .setMin(1).setMax(8)
                .setTooltip(Component.literal("目标矿在脚下≤此深度时向下挖穿, 头顶≤同深度时向上挖穿矿正下方整列 (默认 6)"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH::set).build());
        // v79.26.8e: 垫柱触发高度/面前挖穿距离 GUI 删除 — 垫柱链/面前挖穿退役
        // (用户裁定 "不用垫方块了, 只要挖上下能挖到的就行了"), 桥/阶梯固定逻辑无配置
        chain.addEntry(eb.startIntField(Component.literal("导航看门狗超时 (tick)"),
                        ActiveTaskConfig.CHAIN_NAV_TIMEOUT.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_NAV_TIMEOUT.getDefault())
                .setMin(40).setMax(2400)
                .setTooltip(Component.literal("寻路超时未达目标则跳过重试, 240=12秒 (默认 240)"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_NAV_TIMEOUT::set).build());
        chain.addEntry(eb.startBooleanToggle(Component.literal("卡方块自救"),
                        ActiveTaskConfig.CHAIN_SELF_RESCUE.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_SELF_RESCUE.getDefault())
                .setTooltip(Component.literal("女仆被埋/卡住时自动瞬破窒息方块脱困 (v79.26.8d 参考 maid_useful_task)"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_SELF_RESCUE::set).build());
        // v79.26.7: 跳过集有效期 GUI 删除 — 分档死值 (TLM 60t / 激进 1s, 用户裁定)

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

        // ── v79.9: 哈气 ──
        ConfigCategory haqi = root.getOrCreateCategory(Component.literal("哈气"));
        haqi.addEntry(eb.startBooleanToggle(Component.literal("哈气任务总开关"),
                        PassiveTaskConfig.HAQI_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_ENABLED.getDefault())
                .setTooltip(Component.literal("默认关闭; 开启后女仆靠近其他女仆时概率触发"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_ENABLED::set).build());
        haqi.addEntry(eb.startDoubleField(Component.literal("触发概率"),
                        PassiveTaskConfig.HAQI_CHANCE.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_CHANCE.getDefault())
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.literal("2 格内有其他女仆时的触发概率, 默认 0.1 = 10%"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_CHANCE::set).build());
        haqi.addEntry(eb.startIntField(Component.literal("基础看着时长 (tick)"),
                        PassiveTaskConfig.HAQI_DURATION_TICKS.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_DURATION_TICKS.getDefault())
                .setMin(20).setMax(1200)
                .setTooltip(Component.literal("60 = 3 秒; 总看着时长 = 基础 + 音频实际时长"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_DURATION_TICKS::set).build());
        haqi.addEntry(eb.startDoubleField(Component.literal("音频音量"),
                        PassiveTaskConfig.HAQI_VOLUME.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_VOLUME.getDefault())
                .setMin(0.0).setMax(2.0)
                .setTooltip(Component.literal("哈气音频播放音量, 默认 1.0"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_VOLUME::set).build());
        haqi.addEntry(eb.startDoubleField(Component.literal("挥击概率"),
                        PassiveTaskConfig.HAQI_HIT_CHANCE.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_HIT_CHANCE.getDefault())
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.literal("LOOK 期间概率挥击目标一下, 默认 0.3 = 30%"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_CHANCE::set).build());
        haqi.addEntry(eb.startDoubleField(Component.literal("挥击伤害"),
                        PassiveTaskConfig.HAQI_HIT_DAMAGE.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_HIT_DAMAGE.getDefault())
                .setMin(0.0).setMax(100.0)
                .setTooltip(Component.literal("挥击伤害, 默认 1.0 = 一点血"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_DAMAGE::set).build());
        // ── v79.20: 哈气对主人变体 (独立二级开关 + 独立配置) ──
        haqi.addEntry(eb.startBooleanToggle(Component.literal("哈气对主人开关"),
                        PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.getDefault())
                .setTooltip(Component.literal("默认关闭; 需哈气总开关开启; 只控制对主人哈气 (旁边无女仆且主人在 2 格内)"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_ENABLED_TO_OWNER::set).build());
        haqi.addEntry(eb.startDoubleField(Component.literal("对主人触发概率"),
                        PassiveTaskConfig.HAQI_CHANCE_TO_OWNER.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_CHANCE_TO_OWNER.getDefault())
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.literal("旁边无女仆时对 2 格内主人的触发概率, 默认 0.1 = 10%"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_CHANCE_TO_OWNER::set).build());
        haqi.addEntry(eb.startIntField(Component.literal("对主人看着时长 (tick)"),
                        PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER.getDefault())
                .setMin(20).setMax(1200)
                .setTooltip(Component.literal("60 = 3 秒; 客户端语音文件实际时长服务端不可知, 总时长 = 此值"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER::set).build());
        haqi.addEntry(eb.startDoubleField(Component.literal("对主人音频音量"),
                        PassiveTaskConfig.HAQI_VOLUME_TO_OWNER.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_VOLUME_TO_OWNER.getDefault())
                .setMin(0.0).setMax(2.0)
                .setTooltip(Component.literal("littlemaid_peco 声音包 idle 子集随机播放音量, 默认 1.0"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_VOLUME_TO_OWNER::set).build());
        haqi.addEntry(eb.startDoubleField(Component.literal("对主人挥击概率"),
                        PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER.getDefault())
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.literal("LOOK 期间概率拍主人一下, 默认 0.3 = 30%"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER::set).build());
        haqi.addEntry(eb.startDoubleField(Component.literal("对主人挥击伤害"),
                        PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER.get())
                .setDefaultValue(PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER.getDefault())
                .setMin(0.0).setMax(100.0)
                .setTooltip(Component.literal("挥击伤害, 默认 1.0 = 一点血; 主人不反击"))
                .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER::set).build());

        // ── v79.26.8e: 寻路设置退役 (用户裁定 "寻路全用TLM... 那个寻路全局设置就没用了, 子任务的寻路设置也没用了")
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

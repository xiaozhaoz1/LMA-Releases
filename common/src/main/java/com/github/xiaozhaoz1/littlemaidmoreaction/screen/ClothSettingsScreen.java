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
import com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig;

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
                .setTooltip(Component.literal("无目标扫描频率, 20 = 1 秒"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_SCAN_INTERVAL::set).build());
        chain.addEntry(eb.startIntField(Component.literal("最大采集距离 (格)"),
                        ActiveTaskConfig.CHAIN_MAX_DISTANCE.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_MAX_DISTANCE.getDefault())
                .setMin(4).setMax(128)
                .setTooltip(Component.literal("连锁 BFS 搜索与整脉破坏距离上限"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_MAX_DISTANCE::set).build());
        // 挖矿兜底行为参数 (原行内魔法数/类内常量 → 配置)
        chain.addEntry(eb.startIntField(Component.literal("垂直挖穿深度 (格)"),
                        ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.getDefault())
                .setMin(1).setMax(8)
                .setTooltip(Component.literal("头顶≤此深度的裸露矿 TLM 不可达时向上挖穿矿正下方整列 (默认 6)"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH::set).build());
        // 垫柱触发高度/面前挖穿距离 GUI 删除 — 垫柱链/面前挖穿退役
        // (用户裁定 "不用垫方块了, 只要挖上下能挖到的就行了"), 桥/阶梯固定逻辑无配置
        chain.addEntry(eb.startIntField(Component.literal("导航看门狗超时 (tick)"),
                        ActiveTaskConfig.CHAIN_NAV_TIMEOUT.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_NAV_TIMEOUT.getDefault())
                .setMin(40).setMax(2400)
                .setTooltip(Component.literal("寻路超时未达目标则跳过重试, 240=12秒 (默认 240)"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_NAV_TIMEOUT::set).build());
        // 跳过集有效期 GUI 删除 — 分档死值 (TLM 60t / 激进 1s, 用户裁定)

        // ── 环境感知 ──
        ConfigCategory env = root.getOrCreateCategory(Component.literal("环境感知"));
        env.addEntry(eb.startBooleanToggle(Component.literal("环境感知总开关"),
                        PassiveTaskConfig.ENVSENSE_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.ENVSENSE_ENABLED.getDefault())
                .setTooltip(Component.literal("false=女仆不接收任何环境信号 (v63)"))
                .setSaveConsumer(PassiveTaskConfig.ENVSENSE_ENABLED::set).build());
        env.addEntry(eb.startBooleanToggle(Component.literal("自救"),
                        PassiveTaskConfig.SELF_RESCUE_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.SELF_RESCUE_ENABLED.getDefault())
                .setTooltip(Component.literal("女仆掉血时触发自救被动任务, 被埋/卡住时自动瞬破窒息方块脱困"))
                .setSaveConsumer(PassiveTaskConfig.SELF_RESCUE_ENABLED::set).build());
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
                .setTooltip(Component.literal("默认 1200 = 1 分钟"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_INTERVAL::set).build());
        env.addEntry(eb.startIntField(Component.literal("结构探测半径 (区块)"),
                        PassiveTaskConfig.ENV_STRUCTURE_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_RADIUS.getDefault())
                .setMin(1).setMax(32)
                .setTooltip(Component.literal("越大越慢"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_RADIUS::set).build());
        env.addEntry(eb.startIntField(Component.literal("结构信号半径 (格)"),
                        PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS.getDefault())
                .setMin(1).setMax(64)
                .setTooltip(Component.literal("玩家附近此范围内的主人女仆才接收结构信号 (v79.60 per-player)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS::set).build());
        env.addEntry(eb.startStrList(Component.literal("结构信号白名单"),
                        new ArrayList<>(PassiveTaskConfig.ENV_STRUCTURE_WHITELIST.get()))
                .setDefaultValue(new ArrayList<>(PassiveTaskConfig.ENV_STRUCTURE_WHITELIST.getDefault()))
                .setTooltip(Component.literal("只发名单内结构的信号 (registry id, 支持 minecraft:village_* 通配, 空=全部)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_WHITELIST::set).build());
        env.addEntry(eb.startBooleanToggle(Component.literal("只发最近结构信号"),
                        PassiveTaskConfig.ENV_STRUCTURE_NEAREST_ONLY.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_NEAREST_ONLY.getDefault())
                .setTooltip(Component.literal("开=白名单结果内只取距离最近 1 个结构发信号"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_NEAREST_ONLY::set).build());
        env.addEntry(eb.startBooleanToggle(Component.literal("随机选择女仆接收信号"),
                        PassiveTaskConfig.ENV_STRUCTURE_RANDOM_MAID.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_RANDOM_MAID.getDefault())
                .setTooltip(Component.literal("开=玩家附近主人女仆中随机选 1 个, 关=选最近"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_RANDOM_MAID::set).build());
        env.addEntry(eb.startIntField(Component.literal("进入结构判定距离(格)"),
                        PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.getDefault())
                .setMin(1).setMax(100)
                .setTooltip(Component.literal("主人距结构中心≤此值=在结构内(enter信号,不气泡)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST::set).build());
        env.addEntry(eb.startIntField(Component.literal("离开结构判定距离(格)"),
                        PassiveTaskConfig.ENV_STRUCTURE_LEAVE_DIST.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_LEAVE_DIST.getDefault())
                .setMin(2).setMax(256)
                .setTooltip(Component.literal("主人距结构中心>此值=离开(leave信号,不气泡)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_LEAVE_DIST::set).build());
        env.addEntry(eb.startIntField(Component.literal("结构提醒重发间隔(tick)"),
                        PassiveTaskConfig.ENV_STRUCTURE_REFRESH_TICKS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_REFRESH_TICKS.getDefault())
                .setMin(1200).setMax(168000)
                .setTooltip(Component.literal("主人在结构外每此间隔重发方向气泡(默认2400=2分钟)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_REFRESH_TICKS::set).build());
        env.addEntry(eb.startIntField(Component.literal("结构提醒次数上限"),
                        PassiveTaskConfig.ENV_STRUCTURE_REFRESH_MAX.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_REFRESH_MAX.getDefault())
                .setMin(1).setMax(10)
                .setTooltip(Component.literal("每次进入结构外围的提醒总数(含首次发现)"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_REFRESH_MAX::set).build());

        // ── 右键交互 (全局直列) ──
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

        // ── 哈气 ──
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
        // ── 哈气对主人变体 (独立二级开关 + 独立配置) ──
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

        // ── 寻路设置退役 (用户裁定 "寻路全用TLM... 那个寻路全局设置就没用了, 子任务的寻路设置也没用了")
        // ── 任务自定义 (每任务一个按钮 → TaskSettingsScreen 子屏) ──
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

        // ── 女仆好感度双乘区 ──
        ConfigCategory fav = root.getOrCreateCategory(Component.literal("女仆好感度乘区"));
        fav.addEntry(eb.startBooleanToggle(Component.literal("总开关"),
                        ActiveTaskConfig.MAID_FAVORABILITY_ENABLED.get())
                .setDefaultValue(true)
                .setTooltip(Component.literal("效率 (等级越高工作越快) + 消耗 (等级越高消耗越低)"))
                .setSaveConsumer(ActiveTaskConfig.MAID_FAVORABILITY_ENABLED::set).build());
        fav.addEntry(eb.startBooleanToggle(Component.literal("自动修复"),
                        ActiveTaskConfig.REPAIR_AUTO_ENABLED.get())
                .setDefaultValue(true)
                .setTooltip(Component.literal("女仆随时间用经验慢慢修装备 (1 点/约 5 秒, 消耗 = 4 XP × 好感度消耗乘区)"))
                .setSaveConsumer(ActiveTaskConfig.REPAIR_AUTO_ENABLED::set).build());
        fav.addEntry(eb.startDoubleField(Component.literal("效率 Lv1 倍率"),
                        ActiveTaskConfig.FAVOR_SPEED_L1.get())
                .setDefaultValue(1.1).setMin(1.0).setMax(5.0)
                .setTooltip(Component.literal("工作间隔 = 基准 / 倍率"))
                .setSaveConsumer(ActiveTaskConfig.FAVOR_SPEED_L1::set).build());
        fav.addEntry(eb.startDoubleField(Component.literal("效率 Lv2 倍率"),
                        ActiveTaskConfig.FAVOR_SPEED_L2.get())
                .setDefaultValue(1.25).setMin(1.0).setMax(5.0)
                .setSaveConsumer(ActiveTaskConfig.FAVOR_SPEED_L2::set).build());
        fav.addEntry(eb.startDoubleField(Component.literal("效率 Lv3 倍率"),
                        ActiveTaskConfig.FAVOR_SPEED_L3.get())
                .setDefaultValue(1.5).setMin(1.0).setMax(5.0)
                .setSaveConsumer(ActiveTaskConfig.FAVOR_SPEED_L3::set).build());
        fav.addEntry(eb.startDoubleField(Component.literal("消耗 Lv1 倍率"),
                        ActiveTaskConfig.FAVOR_COST_L1.get())
                .setDefaultValue(0.9).setMin(0.1).setMax(1.0)
                .setTooltip(Component.literal("消耗 = 基准 × 倍率 (越低越省)"))
                .setSaveConsumer(ActiveTaskConfig.FAVOR_COST_L1::set).build());
        fav.addEntry(eb.startDoubleField(Component.literal("消耗 Lv2 倍率"),
                        ActiveTaskConfig.FAVOR_COST_L2.get())
                .setDefaultValue(0.75).setMin(0.1).setMax(1.0)
                .setSaveConsumer(ActiveTaskConfig.FAVOR_COST_L2::set).build());
        fav.addEntry(eb.startDoubleField(Component.literal("消耗 Lv3 倍率"),
                        ActiveTaskConfig.FAVOR_COST_L3.get())
                .setDefaultValue(0.5).setMin(0.1).setMax(1.0)
                .setSaveConsumer(ActiveTaskConfig.FAVOR_COST_L3::set).build());

        // ── 挤奶 (v79.6x) ──
        ConfigCategory kitsune = root.getOrCreateCategory(Component.literal("挤奶"));
        kitsune.addEntry(eb.startBooleanToggle(Component.literal("挤奶主开关"),
                        WildKitsuneMilkConfig.TOGGLE_ENABLED.get())
                .setDefaultValue(WildKitsuneMilkConfig.TOGGLE_ENABLED.getDefault())
                .setTooltip(Component.literal("空桶右键女仆挤奶 (已驯服自己的 / 未驯服)"))
                .setSaveConsumer(WildKitsuneMilkConfig.TOGGLE_ENABLED::set).build());
        kitsune.addEntry(eb.startBooleanToggle(Component.literal("野生奶副开关"),
                        WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA.get())
                .setDefaultValue(WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA.getDefault())
                .setTooltip(Component.literal("开=未驯服产野生酒狐奶, 关=未驯服也产酒狐奶桶"))
                .setSaveConsumer(WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA::set).build());
        kitsune.addEntry(eb.startIntField(Component.literal("奶桶抗性时长 (tick)"),
                        WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.get())
                .setDefaultValue(WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.getDefault())
                .setMin(20).setMax(12000)
                .setSaveConsumer(WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS::set).build());
        kitsune.addEntry(eb.startIntField(Component.literal("奶桶恢复时长 (tick)"),
                        WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.get())
                .setDefaultValue(WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.getDefault())
                .setMin(20).setMax(12000)
                .setSaveConsumer(WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS::set).build());
        kitsune.addEntry(eb.startIntField(Component.literal("野生奶恢复时长 (tick)"),
                        WildKitsuneMilkConfig.WILD_REGENERATION_TICKS.get())
                .setDefaultValue(WildKitsuneMilkConfig.WILD_REGENERATION_TICKS.getDefault())
                .setMin(20).setMax(12000)
                .setSaveConsumer(WildKitsuneMilkConfig.WILD_REGENERATION_TICKS::set).build());
        kitsune.addEntry(eb.startIntField(Component.literal("奶桶饰品耐久"),
                        WildKitsuneMilkConfig.BAUBLE_DURABILITY.get())
                .setDefaultValue(WildKitsuneMilkConfig.BAUBLE_DURABILITY.getDefault())
                .setMin(1).setMax(1000)
                .setSaveConsumer(WildKitsuneMilkConfig.BAUBLE_DURABILITY::set).build());
        kitsune.addEntry(eb.startIntField(Component.literal("野生无敌时长 (tick)"),
                        WildKitsuneMilkConfig.WILD_INVINCIBLE_TICKS.get())
                .setDefaultValue(WildKitsuneMilkConfig.WILD_INVINCIBLE_TICKS.getDefault())
                .setMin(20).setMax(12000)
                .setTooltip(Component.literal("与音乐时长一致, 默认 600 = 30s"))
                .setSaveConsumer(WildKitsuneMilkConfig.WILD_INVINCIBLE_TICKS::set).build());
        kitsune.addEntry(eb.startIntField(Component.literal("野生无敌 CD (tick)"),
                        WildKitsuneMilkConfig.WILD_CD_TICKS.get())
                .setDefaultValue(WildKitsuneMilkConfig.WILD_CD_TICKS.getDefault())
                .setMin(100).setMax(72000)
                .setTooltip(Component.literal("默认 12000 = 10 分钟"))
                .setSaveConsumer(WildKitsuneMilkConfig.WILD_CD_TICKS::set).build());
        kitsune.addEntry(eb.startDoubleField(Component.literal("野生音乐音量"),
                        WildKitsuneMilkConfig.WILD_MUSIC_VOLUME.get())
                .setDefaultValue(WildKitsuneMilkConfig.WILD_MUSIC_VOLUME.getDefault())
                .setMin(0.0).setMax(2.0)
                .setTooltip(Component.literal("默认 0.5 = 50%"))
                .setSaveConsumer(WildKitsuneMilkConfig.WILD_MUSIC_VOLUME::set).build());

        root.setSavingRunnable(() -> {
            MoreActionConfig.saveAll();
            if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) {
                ConfigSyncPacket.send();
            }
        });
        return root.build();
    }
}

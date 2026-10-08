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
 *   <li>全局分类: 规则引擎 / 调试 / 连锁采集 / 环境感知 / <b>右键交互</b> (木棍/距离直列) / <b>杂项</b> (真全局参数, 如发电皮带应力)</li>
 *   <li><b>任务自定义</b> 分类: 每个任务一个 {@link ButtonEntry} → {@link TaskSettingsScreen} 子屏 (任务自己的默认值放这里)</li>
 * </ul>
 *
 * <p>API 模式对齐 TLM {@code compat/cloth/MenuIntegration}: ConfigBuilder + entryBuilder,
 * 每项 setDefaultValue + setTooltip + setSaveConsumer, 保存回调统一 {@link MoreActionConfig#saveAll()} (v67.6)。
 *
 * <p><b>语言 (v79.78 用户裁定)</b>: 本屏**全部文案走语言键** ✓（`lma.cfg.*` ✓，zh_cn 中文 / en_us 英文 ✓），
 * <b>禁止</b>再写 `Component.literal("中文")` ✗ —— 写死则切英文也不变 ⇒ 中英混杂 ✓（守护: `arch/ScreenLangGuardTest` ✓）
 */
public final class ClothSettingsScreen {

    private ClothSettingsScreen() {}

    /** 语言键助手 (本屏专用前缀 ✓) */
    private static Component t(String key) {
        return Component.translatable("lma.cfg." + key);
    }

    /** 构建设置屏 — 返回 cloth 生成的 Screen, 由调用方 setScreen */
    public static Screen create(Screen parent) {
        ConfigBuilder root = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(t("title"))
                // ★ v79.72 修 (错题 #361): **Cloth 屏必须挂 setSavingRunnable** —— 原来只给每个条目设了
                //   `setSaveConsumer` (那只把值写进**内存 spec** ✗, 不落盘), 于是用户改完设置**重启即回默认** ✓
                // ★ v79.72 修 (错题 #361): **Cloth 屏必须挂 setSavingRunnable** —— 原来只给每个条目设了
                //   `setSaveConsumer` (那只把值写进**内存 spec** ✗, 不落盘), 于是用户改完设置**重启即回默认** ✓
                //   实测: 用户把「杂项 → 发电皮带应力」改成 100000, 当次会话日志里确实是 100000 ✓, 但
                //   `config/littlemaidmoreaction-*.toml` 里**根本没有这个键** ✗ ⇒ 重启读回 -1 ⇒ 应力永远 1024 ✗
                //   (`TaskSettingsScreen` 早就挂了 ✓ — 本屏漏了 ⇒ 凡本屏可改的设置此前都不落盘 ✗)
                //   ⚠ 并对齐 TaskSettingsScreen 的写法: 多人游戏时还要**把值同步到服务端** (否则只存本地 ✗)
                .setSavingRunnable(() -> {
                    MoreActionConfig.saveAll();
                    if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) {
                        com.github.xiaozhaoz1.littlemaidmoreaction.network.ConfigSyncPacket.send();
                    }
                });
        ConfigEntryBuilder eb = root.entryBuilder();

        // ── 调试 ──
        ConfigCategory debug = root.getOrCreateCategory(t("cat.debug"));
        debug.addEntry(eb.startBooleanToggle(t("debug.mode"),
                        MoreActionConfig.DEBUG_MODE.get())
                .setDefaultValue(MoreActionConfig.DEBUG_MODE.getDefault())
                .setTooltip(t("debug.mode.tip"))
                .setSaveConsumer(MoreActionConfig.DEBUG_MODE::set).build());

        // ── 连锁采集 ──
        ConfigCategory chain = root.getOrCreateCategory(t("cat.chain"));
        chain.addEntry(eb.startIntField(t("chain.max_blocks"),
                        ActiveTaskConfig.CHAIN_MAX_BLOCKS.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_MAX_BLOCKS.getDefault())
                .setMin(1).setMax(1024)
                .setTooltip(t("chain.max_blocks.tip"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_MAX_BLOCKS::set).build());
        chain.addEntry(eb.startBooleanToggle(t("chain.wood_nature"),
                        ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK.getDefault())
                .setTooltip(t("chain.wood_nature.tip"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_WOOD_NATURE_CHECK::set).build());
        chain.addEntry(eb.startIntField(t("chain.scan_interval"),
                        ActiveTaskConfig.CHAIN_SCAN_INTERVAL.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_SCAN_INTERVAL.getDefault())
                .setMin(20).setMax(1200)
                .setTooltip(t("chain.scan_interval.tip"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_SCAN_INTERVAL::set).build());
        chain.addEntry(eb.startIntField(t("chain.max_distance"),
                        ActiveTaskConfig.CHAIN_MAX_DISTANCE.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_MAX_DISTANCE.getDefault())
                .setMin(4).setMax(128)
                .setTooltip(t("chain.max_distance.tip"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_MAX_DISTANCE::set).build());
        // 挖矿兜底行为参数 (原行内魔法数/类内常量 → 配置)
        chain.addEntry(eb.startIntField(t("chain.dig_down_depth"),
                        ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH.getDefault())
                .setMin(1).setMax(8)
                .setTooltip(t("chain.dig_down_depth.tip"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_DIG_DOWN_DEPTH::set).build());
        // 垫柱触发高度/面前挖穿距离 GUI 删除 — 垫柱链/面前挖穿退役
        // (用户裁定 "不用垫方块了, 只要挖上下能挖到的就行了"), 桥/阶梯固定逻辑无配置
        chain.addEntry(eb.startIntField(t("chain.nav_timeout"),
                        ActiveTaskConfig.CHAIN_NAV_TIMEOUT.get())
                .setDefaultValue(ActiveTaskConfig.CHAIN_NAV_TIMEOUT.getDefault())
                .setMin(40).setMax(2400)
                .setTooltip(t("chain.nav_timeout.tip"))
                .setSaveConsumer(ActiveTaskConfig.CHAIN_NAV_TIMEOUT::set).build());

        // 跳过集有效期 GUI 删除 — 分档死值 (TLM 60t / 激进 1s, 用户裁定)
        // ── 环境感知 ──
        ConfigCategory env = root.getOrCreateCategory(t("cat.env"));
        env.addEntry(eb.startBooleanToggle(t("env.enabled"),
                        PassiveTaskConfig.ENVSENSE_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.ENVSENSE_ENABLED.getDefault())
                .setTooltip(t("env.enabled.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENVSENSE_ENABLED::set).build());
        env.addEntry(eb.startBooleanToggle(t("env.self_rescue"),
                        PassiveTaskConfig.SELF_RESCUE_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.SELF_RESCUE_ENABLED.getDefault())
                .setTooltip(t("env.self_rescue.tip"))
                .setSaveConsumer(PassiveTaskConfig.SELF_RESCUE_ENABLED::set).build());
        env.addEntry(eb.startIntField(t("env.scan_interval"),
                        PassiveTaskConfig.ENV_SCAN_INTERVAL.get())
                .setDefaultValue(PassiveTaskConfig.ENV_SCAN_INTERVAL.getDefault())
                .setMin(20).setMax(1200)
                .setTooltip(t("env.scan_interval.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_SCAN_INTERVAL::set).build());
        env.addEntry(eb.startIntField(t("env.radius"),
                        PassiveTaskConfig.ENV_DEFAULT_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_DEFAULT_RADIUS.getDefault())
                .setMin(4).setMax(64)
                .setTooltip(t("env.radius.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_DEFAULT_RADIUS::set).build());
        env.addEntry(eb.startIntField(t("env.max_hits"),
                        PassiveTaskConfig.ENV_MAX_HITS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_MAX_HITS.getDefault())
                .setMin(1).setMax(256)
                .setTooltip(t("env.max_hits.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_MAX_HITS::set).build());
        env.addEntry(eb.startIntField(t("env.player_gate_radius"),
                        PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS.getDefault())
                .setMin(0).setMax(256)
                .setTooltip(t("env.player_gate_radius.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_PLAYER_GATE_RADIUS::set).build());
        env.addEntry(eb.startIntField(t("env.darkness"),
                        PassiveTaskConfig.ENV_DARKNESS_THRESHOLD.get())
                .setDefaultValue(PassiveTaskConfig.ENV_DARKNESS_THRESHOLD.getDefault())
                .setMin(0).setMax(15)
                .setTooltip(t("env.darkness.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_DARKNESS_THRESHOLD::set).build());
        env.addEntry(eb.startBooleanToggle(t("env.structure_enabled"),
                        PassiveTaskConfig.ENV_STRUCTURE_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_ENABLED.getDefault())
                .setTooltip(t("env.structure_enabled.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_ENABLED::set).build());
        env.addEntry(eb.startIntField(t("env.structure_interval"),
                        PassiveTaskConfig.ENV_STRUCTURE_INTERVAL.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_INTERVAL.getDefault())
                .setMin(1200).setMax(168000)
                .setTooltip(t("env.structure_interval.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_INTERVAL::set).build());
        env.addEntry(eb.startIntField(t("env.structure_radius"),
                        PassiveTaskConfig.ENV_STRUCTURE_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_RADIUS.getDefault())
                .setMin(1).setMax(32)
                .setTooltip(t("env.structure_radius.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_RADIUS::set).build());
        env.addEntry(eb.startIntField(t("env.structure_signal_radius"),
                        PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS.getDefault())
                .setMin(1).setMax(64)
                .setTooltip(t("env.structure_signal_radius.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_SIGNAL_RADIUS::set).build());
        env.addEntry(eb.startStrList(t("env.structure_whitelist"),
                        new ArrayList<>(PassiveTaskConfig.ENV_STRUCTURE_WHITELIST.get()))
                .setDefaultValue(new ArrayList<>(PassiveTaskConfig.ENV_STRUCTURE_WHITELIST.getDefault()))
                .setTooltip(t("env.structure_whitelist.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_WHITELIST::set).build());
        env.addEntry(eb.startBooleanToggle(t("env.structure_nearest_only"),
                        PassiveTaskConfig.ENV_STRUCTURE_NEAREST_ONLY.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_NEAREST_ONLY.getDefault())
                .setTooltip(t("env.structure_nearest_only.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_NEAREST_ONLY::set).build());
        env.addEntry(eb.startBooleanToggle(t("env.structure_random_maid"),
                        PassiveTaskConfig.ENV_STRUCTURE_RANDOM_MAID.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_RANDOM_MAID.getDefault())
                .setTooltip(t("env.structure_random_maid.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_RANDOM_MAID::set).build());
        env.addEntry(eb.startIntField(t("env.structure_enter"),
                        PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST.getDefault())
                .setMin(1).setMax(100)
                .setTooltip(t("env.structure_enter.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_ENTER_DIST::set).build());
        env.addEntry(eb.startIntField(t("env.structure_leave"),
                        PassiveTaskConfig.ENV_STRUCTURE_LEAVE_DIST.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_LEAVE_DIST.getDefault())
                .setMin(2).setMax(256)
                .setTooltip(t("env.structure_leave.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_LEAVE_DIST::set).build());
        env.addEntry(eb.startIntField(t("env.structure_refresh_ticks"),
                        PassiveTaskConfig.ENV_STRUCTURE_REFRESH_TICKS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_REFRESH_TICKS.getDefault())
                .setMin(1200).setMax(168000)
                .setTooltip(t("env.structure_refresh_ticks.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_REFRESH_TICKS::set).build());
        env.addEntry(eb.startIntField(t("env.structure_refresh_max"),
                        PassiveTaskConfig.ENV_STRUCTURE_REFRESH_MAX.get())
                .setDefaultValue(PassiveTaskConfig.ENV_STRUCTURE_REFRESH_MAX.getDefault())
                .setMin(1).setMax(10)
                .setTooltip(t("env.structure_refresh_max.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_STRUCTURE_REFRESH_MAX::set).build());
        // v79.63.21 补缺口 (用户裁定: 感知域参数归本分类): 稀有群系通报 3 键此前**无任何 GUI 入口**
        env.addEntry(eb.startBooleanToggle(t("env.rare_biome"),
                        PassiveTaskConfig.ENV_RARE_BIOME_ENABLED.get())
                .setDefaultValue(PassiveTaskConfig.ENV_RARE_BIOME_ENABLED.getDefault())
                .setTooltip(t("env.rare_biome.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_RARE_BIOME_ENABLED::set).build());
        env.addEntry(eb.startIntField(t("env.rare_biome_interval"),
                        PassiveTaskConfig.ENV_RARE_BIOME_INTERVAL.get())
                .setDefaultValue(PassiveTaskConfig.ENV_RARE_BIOME_INTERVAL.getDefault())
                .setMin(1200).setMax(168000)
                .setTooltip(t("env.rare_biome_interval.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_RARE_BIOME_INTERVAL::set).build());
        env.addEntry(eb.startIntField(t("env.rare_biome_radius"),
                        PassiveTaskConfig.ENV_RARE_BIOME_SIGNAL_RADIUS.get())
                .setDefaultValue(PassiveTaskConfig.ENV_RARE_BIOME_SIGNAL_RADIUS.getDefault())
                .setMin(1).setMax(64)
                .setTooltip(t("env.rare_biome_radius.tip"))
                .setSaveConsumer(PassiveTaskConfig.ENV_RARE_BIOME_SIGNAL_RADIUS::set).build());

        // ── 右键交互 (全局直列) ──
        ConfigCategory bi = root.getOrCreateCategory(t("cat.bi"));
        bi.addEntry(eb.startTextField(t("bi.mark_item"),
                        ActiveTaskConfig.BI_MARK_ITEM.get())
                .setDefaultValue(ActiveTaskConfig.BI_MARK_ITEM.getDefault())
                .setTooltip(t("bi.mark_item.tip"))
                .setSaveConsumer(ActiveTaskConfig.BI_MARK_ITEM::set).build());
        bi.addEntry(eb.startTextField(t("bi.bind_item"),
                        ActiveTaskConfig.BI_BIND_ITEM.get())
                .setDefaultValue(ActiveTaskConfig.BI_BIND_ITEM.getDefault())
                .setTooltip(t("bi.bind_item.tip"))
                .setSaveConsumer(ActiveTaskConfig.BI_BIND_ITEM::set).build());
        bi.addEntry(eb.startDoubleField(t("bi.interact_distance"),
                        ActiveTaskConfig.BI_INTERACT_DISTANCE.get())
                .setDefaultValue(ActiveTaskConfig.BI_INTERACT_DISTANCE.getDefault())
                .setMin(1.0).setMax(16.0)
                .setTooltip(t("bi.interact_distance.tip"))
                .setSaveConsumer(ActiveTaskConfig.BI_INTERACT_DISTANCE::set).build());
        bi.addEntry(eb.startDoubleField(t("bi.trigger_range"),
                        ActiveTaskConfig.BI_TRIGGER_RANGE.get())
                .setDefaultValue(ActiveTaskConfig.BI_TRIGGER_RANGE.getDefault())
                .setMin(5.0).setMax(64.0)
                .setTooltip(t("bi.trigger_range.tip"))
                .setSaveConsumer(ActiveTaskConfig.BI_TRIGGER_RANGE::set).build());
        bi.addEntry(eb.startIntField(t("bi.timer_interval"),
                        ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get())
                .setDefaultValue(ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.getDefault())
                .setMin(20).setMax(12000)
                .setTooltip(t("bi.timer_interval.tip"))
                .setSaveConsumer(ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL::set).build());

        // ── 任务自定义 (每任务一个按钮 → TaskSettingsScreen 子屏) ──
        ConfigCategory tasks = root.getOrCreateCategory(t("cat.tasks"));
        var taskTypes = new ArrayList<>(TaskRegistry.taskTypes());
        taskTypes.sort(Comparator.naturalOrder());
        for (String taskType : taskTypes) {
            tasks.addEntry(new ButtonEntry(TaskSettingsScreen.title(taskType),
                    t("tasks.customize"),
                    () -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.setScreen(TaskSettingsScreen.create(mc.screen, taskType));
                    }));
        }

        // ── 女仆好感度双乘区 ──
        ConfigCategory fav = root.getOrCreateCategory(t("cat.favor"));
        fav.addEntry(eb.startBooleanToggle(t("favor.enabled"),
                        ActiveTaskConfig.MAID_FAVORABILITY_ENABLED.get())
                .setDefaultValue(true)
                .setTooltip(t("favor.enabled.tip"))
                .setSaveConsumer(ActiveTaskConfig.MAID_FAVORABILITY_ENABLED::set).build());
        fav.addEntry(eb.startBooleanToggle(t("favor.auto_repair"),
                        ActiveTaskConfig.REPAIR_AUTO_ENABLED.get())
                .setDefaultValue(true)
                .setTooltip(t("favor.auto_repair.tip"))
                .setSaveConsumer(ActiveTaskConfig.REPAIR_AUTO_ENABLED::set).build());
        fav.addEntry(eb.startDoubleField(t("favor.speed_l1"),
                        ActiveTaskConfig.FAVOR_SPEED_L1.get())
                .setDefaultValue(1.1).setMin(1.0).setMax(5.0)
                .setTooltip(t("favor.speed.tip"))
                .setSaveConsumer(ActiveTaskConfig.FAVOR_SPEED_L1::set).build());
        fav.addEntry(eb.startDoubleField(t("favor.speed_l2"),
                        ActiveTaskConfig.FAVOR_SPEED_L2.get())
                .setDefaultValue(1.25).setMin(1.0).setMax(5.0)
                .setSaveConsumer(ActiveTaskConfig.FAVOR_SPEED_L2::set).build());
        fav.addEntry(eb.startDoubleField(t("favor.speed_l3"),
                        ActiveTaskConfig.FAVOR_SPEED_L3.get())
                .setDefaultValue(1.5).setMin(1.0).setMax(5.0)
                .setSaveConsumer(ActiveTaskConfig.FAVOR_SPEED_L3::set).build());
        fav.addEntry(eb.startDoubleField(t("favor.cost_l1"),
                        ActiveTaskConfig.FAVOR_COST_L1.get())
                .setDefaultValue(0.9).setMin(0.1).setMax(1.0)
                .setTooltip(t("favor.cost.tip"))
                .setSaveConsumer(ActiveTaskConfig.FAVOR_COST_L1::set).build());
        fav.addEntry(eb.startDoubleField(t("favor.cost_l2"),
                        ActiveTaskConfig.FAVOR_COST_L2.get())
                .setDefaultValue(0.75).setMin(0.1).setMax(1.0)
                .setSaveConsumer(ActiveTaskConfig.FAVOR_COST_L2::set).build());
        fav.addEntry(eb.startDoubleField(t("favor.cost_l3"),
                        ActiveTaskConfig.FAVOR_COST_L3.get())
                .setDefaultValue(0.5).setMin(0.1).setMax(1.0)
                .setSaveConsumer(ActiveTaskConfig.FAVOR_COST_L3::set).build());

        // ── 防御塔设置 (v79.62.3, 用户裁定: Cloth = 全局默认, GUI = 每塔覆盖) ──
        ConfigCategory tower = root.getOrCreateCategory(t("cat.tower"));
        tower.addEntry(eb.startDoubleField(t("tower.damage"),
                        com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.DAMAGE.get())
                .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.DAMAGE.getDefault())
                .setMin(0.5).setMax(100.0)
                .setTooltip(t("tower.damage.tip"))
                .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.DAMAGE::set).build());
        tower.addEntry(eb.startIntField(t("tower.radius"),
                        com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.RADIUS.get())
                .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.RADIUS.getDefault())
                .setMin(4).setMax(64)
                .setTooltip(t("tower.radius.tip"))
                .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.RADIUS::set).build());
        tower.addEntry(eb.startIntField(t("tower.fire_interval"),
                        com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.FIRE_INTERVAL.get())
                .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.FIRE_INTERVAL.getDefault())
                .setMin(10).setMax(200)
                .setTooltip(t("tower.fire_interval.tip"))
                .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.FIRE_INTERVAL::set).build());

        // ── 杂项 (v79.63.21 新建, 用户裁定) ──
        // 归属判据: **真全局参数** (不属于任何单个任务的 per-task 屏) 归本分类;
        // 任务自己的默认值 (dam_fill / void_excavation / bell_ring 等) 归「任务自定义 → 该任务」子屏 ✗ 不放这里。
        ConfigCategory misc = root.getOrCreateCategory(t("cat.misc"));
        misc.addEntry(eb.startIntField(t("misc.power_belt_stress"),
                        ActiveTaskConfig.POWER_BELT_STRESS.get())
                .setDefaultValue(ActiveTaskConfig.POWER_BELT_STRESS.getDefault())
                .setMin(-1).setMax(1000000)
                .setTooltip(t("misc.power_belt_stress.tip"))
                .setSaveConsumer(ActiveTaskConfig.POWER_BELT_STRESS::set).build());

        return root.build();
    }
}

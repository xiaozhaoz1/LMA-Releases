package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.ConfigSyncPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;

/**
 * v67.2/v67.3: 任务自定义设置子屏 — 从 ClothSettingsScreen「任务自定义」按钮进入。
 *
 * <p>每个任务一个 cloth 子屏, 按 taskType 展示全局设置 (Cloth Config) +
 * per-maid 设置说明; 无设置任务显示"暂无自定义设置"。
 * v67.3: 全局黑白名单/等待时长/产物上限 均在此 (主屏「右键交互」保持全局直列)。
 *
 * <p><b>语言 (v79.78 用户裁定)</b>: 本屏**全部文案走语言键** ✓（`lma.task.*` ✓，zh_cn 中文 / en_us 英文 ✓），
 * <b>禁止</b>再写 `Component.literal("中文")` ✗（写死则切英文也不变 ⇒ 中英混杂 ✓；守护 `arch/ScreenLangGuardTest` ✓）
 */
public final class TaskSettingsScreen {

    private TaskSettingsScreen() {}

    /** 语言键助手 (本屏前缀 ✓) */
    private static Component t(String key) {
        return Component.translatable("lma.task." + key);
    }

    /** 任务中文名 — lang key: task.littlemaidmoreaction.<taskType> */
    public static Component title(String taskType) {
        return Component.translatable("task.littlemaidmoreaction." + taskType);
    }

    public static Screen create(Screen parent, String taskType) {
        ConfigBuilder root = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(title(taskType));
        ConfigEntryBuilder eb = root.entryBuilder();
        // 复用主屏的「任务自定义」分类键 ✓ (同一概念不重复建键 ✓)
        ConfigCategory cat = root.getOrCreateCategory(Component.translatable("lma.cfg.cat.tasks"));

        switch (taskType) {
            case "craft_chain" -> {
                cat.addEntry(eb.startTextField(
                                t("craft.default_product"), ActiveTaskConfig.CRAFT_DEFAULT_PRODUCT.get())
                        .setDefaultValue(ActiveTaskConfig.CRAFT_DEFAULT_PRODUCT.getDefault())
                        .setTooltip(t("craft.default_product.tip"))
                        .setSaveConsumer(ActiveTaskConfig.CRAFT_DEFAULT_PRODUCT::set).build());
                cat.addEntry(eb.startIntField(
                                t("craft.max_products"), ActiveTaskConfig.CRAFT_MAX_PRODUCTS.get())
                        .setDefaultValue(ActiveTaskConfig.CRAFT_MAX_PRODUCTS.getDefault())
                        .setMin(-1).setMax(1024)
                        .setTooltip(t("craft.max_products.tip"))
                        .setSaveConsumer(ActiveTaskConfig.CRAFT_MAX_PRODUCTS::set).build());
            }
            case "furnace" -> {
                cat.addEntry(eb.startStrList(
                                t("furnace.blacklist"),
                                new ArrayList<>(ActiveTaskConfig.FURNACE_BLACKLIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.FURNACE_BLACKLIST.getDefault()))
                        .setTooltip(t("furnace.blacklist.tip"))
                        .setSaveConsumer(ActiveTaskConfig.FURNACE_BLACKLIST::set).build());
                cat.addEntry(eb.startStrList(
                                t("furnace.whitelist"),
                                new ArrayList<>(ActiveTaskConfig.FURNACE_WHITELIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.FURNACE_WHITELIST.getDefault()))
                        .setTooltip(t("furnace.whitelist.tip"))
                        .setSaveConsumer(ActiveTaskConfig.FURNACE_WHITELIST::set).build());
            }
            case "jukebox" -> {
                cat.addEntry(eb.startIntField(
                                t("jukebox.wait_ticks"), ActiveTaskConfig.JUKEBOX_WAIT_TICKS.get())
                        .setDefaultValue(ActiveTaskConfig.JUKEBOX_WAIT_TICKS.getDefault())
                        .setMin(20).setMax(24000)
                        .setTooltip(t("jukebox.wait_ticks.tip"))
                        .setSaveConsumer(ActiveTaskConfig.JUKEBOX_WAIT_TICKS::set).build());
                cat.addEntry(eb.startStrList(
                                t("jukebox.blacklist"),
                                new ArrayList<>(ActiveTaskConfig.JUKEBOX_BLACKLIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.JUKEBOX_BLACKLIST.getDefault()))
                        .setTooltip(t("jukebox.blacklist.tip"))
                        .setSaveConsumer(ActiveTaskConfig.JUKEBOX_BLACKLIST::set).build());
                cat.addEntry(eb.startStrList(
                                t("jukebox.whitelist"),
                                new ArrayList<>(ActiveTaskConfig.JUKEBOX_WHITELIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.JUKEBOX_WHITELIST.getDefault()))
                        .setTooltip(t("jukebox.whitelist.tip"))
                        .setSaveConsumer(ActiveTaskConfig.JUKEBOX_WHITELIST::set).build());
            }
            case "arm_transfer" -> {
                cat.addEntry(eb.startStrList(
                                t("arm.blacklist"),
                                new ArrayList<>(ActiveTaskConfig.ARM_BLACKLIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.ARM_BLACKLIST.getDefault()))
                        .setTooltip(t("arm.blacklist.tip"))
                        .setSaveConsumer(ActiveTaskConfig.ARM_BLACKLIST::set).build());
                cat.addEntry(eb.startStrList(
                                t("arm.whitelist"),
                                new ArrayList<>(ActiveTaskConfig.ARM_WHITELIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.ARM_WHITELIST.getDefault()))
                        .setTooltip(t("arm.whitelist.tip"))
                        .setSaveConsumer(ActiveTaskConfig.ARM_WHITELIST::set).build());
            }
            case "collect_wood", "collect_ore" -> {
                cat.addEntry(eb.startStrList(
                                t("collect.blacklist"),
                                new ArrayList<>(ActiveTaskConfig.COLLECT_BLACKLIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.COLLECT_BLACKLIST.getDefault()))
                        .setTooltip(t("collect.blacklist.tip"))
                        .setSaveConsumer(ActiveTaskConfig.COLLECT_BLACKLIST::set).build());
                cat.addEntry(eb.startStrList(
                                t("collect.whitelist"),
                                new ArrayList<>(ActiveTaskConfig.COLLECT_WHITELIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.COLLECT_WHITELIST.getDefault()))
                        .setTooltip(t("collect.whitelist.tip"))
                        .setSaveConsumer(ActiveTaskConfig.COLLECT_WHITELIST::set).build());
            }
            case "bell_ring" -> {
                cat.addEntry(eb.startDoubleField(t("bell.volume"),
                                ActiveTaskConfig.BELL_VOLUME.get())
                        .setDefaultValue(ActiveTaskConfig.BELL_VOLUME.getDefault())
                        .setMin(0.0).setMax(2.0)
                        .setTooltip(t("bell.volume.tip"))
                        .setSaveConsumer(ActiveTaskConfig.BELL_VOLUME::set).build());
                cat.addEntry(eb.startDoubleField(t("bell.pitch"),
                                ActiveTaskConfig.BELL_PITCH.get())
                        .setDefaultValue(ActiveTaskConfig.BELL_PITCH.getDefault())
                        .setMin(0.5).setMax(2.0)
                        .setTooltip(t("bell.pitch.tip"))
                        .setSaveConsumer(ActiveTaskConfig.BELL_PITCH::set).build());
                cat.addEntry(eb.startIntField(t("bell.interval"),
                                ActiveTaskConfig.BELL_RING_INTERVAL.get())
                        .setDefaultValue(ActiveTaskConfig.BELL_RING_INTERVAL.getDefault())
                        .setMin(30).setMax(12000)
                        .setTooltip(t("bell.interval.tip"))
                        .setSaveConsumer(ActiveTaskConfig.BELL_RING_INTERVAL::set).build());
            }
            case "block_interact" -> cat.addEntry(eb.startTextDescription(
                    t("block_interact.hint")).build());
            case "ai_control" -> {
                cat.addEntry(eb.startTextField(
                                t("ai.llm"), ActiveTaskConfig.AI_LLM_PROVIDER.get())
                        .setDefaultValue(ActiveTaskConfig.AI_LLM_PROVIDER.getDefault())
                        .setTooltip(t("ai.llm.tip"))
                        .setSaveConsumer(ActiveTaskConfig.AI_LLM_PROVIDER::set).build());
                cat.addEntry(eb.startTextField(
                                t("ai.voice"), ActiveTaskConfig.AI_VOICE.get())
                        .setDefaultValue(ActiveTaskConfig.AI_VOICE.getDefault())
                        .setTooltip(t("ai.voice.tip"))
                        .setSaveConsumer(ActiveTaskConfig.AI_VOICE::set).build());
                // 假人随机台词气泡/语音 (SHELVED 假人头顶气泡 + 女仆语音包音频)
                cat.addEntry(eb.startBooleanToggle(
                                t("ai.chat_bubble"), PassiveTaskConfig.COMPANION_CHAT_ENABLED.get())
                        .setDefaultValue(PassiveTaskConfig.COMPANION_CHAT_ENABLED.getDefault())
                        .setTooltip(t("ai.chat_bubble.tip"))
                        .setSaveConsumer(PassiveTaskConfig.COMPANION_CHAT_ENABLED::set).build());
                cat.addEntry(eb.startIntSlider(
                                t("ai.chat_interval"), PassiveTaskConfig.COMPANION_CHAT_RATE.get(), 20, 24000)
                        .setDefaultValue(PassiveTaskConfig.COMPANION_CHAT_RATE.getDefault())
                        .setTooltip(t("ai.chat_interval.tip"))
                        .setSaveConsumer(PassiveTaskConfig.COMPANION_CHAT_RATE::set).build());
                cat.addEntry(eb.startBooleanToggle(
                                t("ai.random_voice"), PassiveTaskConfig.COMPANION_VOICE_ENABLED.get())
                        .setDefaultValue(PassiveTaskConfig.COMPANION_VOICE_ENABLED.getDefault())
                        .setTooltip(t("ai.random_voice.tip"))
                        .setSaveConsumer(PassiveTaskConfig.COMPANION_VOICE_ENABLED::set).build());
            }
            // v79.62.1 哈气/奶开关迁至管线子界面 (从 ClothSettingsScreen 主屏全局分类迁入)
            case "haqi" -> {
                cat.addEntry(eb.startBooleanToggle(t("haqi.enabled"),
                                PassiveTaskConfig.HAQI_ENABLED.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_ENABLED.getDefault())
                        .setTooltip(t("haqi.enabled.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_ENABLED::set).build());
                cat.addEntry(eb.startDoubleField(t("haqi.chance"),
                                PassiveTaskConfig.HAQI_CHANCE.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_CHANCE.getDefault())
                        .setMin(0.0).setMax(1.0)
                        .setTooltip(t("haqi.chance.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_CHANCE::set).build());
                cat.addEntry(eb.startIntField(t("haqi.duration"),
                                PassiveTaskConfig.HAQI_DURATION_TICKS.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_DURATION_TICKS.getDefault())
                        .setMin(20).setMax(1200)
                        .setTooltip(t("haqi.duration.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_DURATION_TICKS::set).build());
                cat.addEntry(eb.startDoubleField(t("haqi.volume"),
                                PassiveTaskConfig.HAQI_VOLUME.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_VOLUME.getDefault())
                        .setMin(0.0).setMax(2.0)
                        .setTooltip(t("haqi.volume.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_VOLUME::set).build());
                cat.addEntry(eb.startDoubleField(t("haqi.hit_chance"),
                                PassiveTaskConfig.HAQI_HIT_CHANCE.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_HIT_CHANCE.getDefault())
                        .setMin(0.0).setMax(1.0)
                        .setTooltip(t("haqi.hit_chance.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_CHANCE::set).build());
                cat.addEntry(eb.startDoubleField(t("haqi.hit_damage"),
                                PassiveTaskConfig.HAQI_HIT_DAMAGE.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_HIT_DAMAGE.getDefault())
                        .setMin(0.0).setMax(100.0)
                        .setTooltip(t("haqi.hit_damage.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_DAMAGE::set).build());
                cat.addEntry(eb.startBooleanToggle(t("haqi.owner_enabled"),
                                PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.getDefault())
                        .setTooltip(t("haqi.owner_enabled.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_ENABLED_TO_OWNER::set).build());
                cat.addEntry(eb.startDoubleField(t("haqi.owner_chance"),
                                PassiveTaskConfig.HAQI_CHANCE_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_CHANCE_TO_OWNER.getDefault())
                        .setMin(0.0).setMax(1.0)
                        .setTooltip(t("haqi.owner_chance.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_CHANCE_TO_OWNER::set).build());
                cat.addEntry(eb.startIntField(t("haqi.owner_duration"),
                                PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER.getDefault())
                        .setMin(20).setMax(1200)
                        .setTooltip(t("haqi.owner_duration.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER::set).build());
                cat.addEntry(eb.startDoubleField(t("haqi.owner_volume"),
                                PassiveTaskConfig.HAQI_VOLUME_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_VOLUME_TO_OWNER.getDefault())
                        .setMin(0.0).setMax(2.0)
                        .setSaveConsumer(PassiveTaskConfig.HAQI_VOLUME_TO_OWNER::set).build());
                cat.addEntry(eb.startDoubleField(t("haqi.owner_hit_chance"),
                                PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER.getDefault())
                        .setMin(0.0).setMax(1.0)
                        .setTooltip(t("haqi.owner_hit_chance.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER::set).build());
                cat.addEntry(eb.startDoubleField(t("haqi.owner_hit_damage"),
                                PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER.getDefault())
                        .setMin(0.0).setMax(100.0)
                        .setTooltip(t("haqi.owner_hit_damage.tip"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER::set).build());
            }
            case "void_excavation" -> {
                // v79.62.1 挖空置域: 全局默认区块数 (区域 = 起点为中心 N×N 区块)
                cat.addEntry(eb.startIntField(
                                t("void.chunks"), ActiveTaskConfig.VOID_DEFAULT_CHUNKS.get())
                        .setDefaultValue(ActiveTaskConfig.VOID_DEFAULT_CHUNKS.getDefault())
                        .setMin(1).setMax(256)
                        .setTooltip(t("void.chunks.tip"))
                        .setSaveConsumer(ActiveTaskConfig.VOID_DEFAULT_CHUNKS::set).build());
                // v79.62.1 用户裁定 (黑名单→销毁名单): 名单内物品挖出即销毁; 关寻路 (全局默认, 单女仆 TLM 可覆盖)
                cat.addEntry(eb.startStrList(
                                t("void.destroy_list"),
                                new ArrayList<>(ActiveTaskConfig.VOID_DESTROY_LIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.VOID_DESTROY_LIST.getDefault()))
                        .setTooltip(t("void.destroy_list.tip"))
                        .setSaveConsumer(ActiveTaskConfig.VOID_DESTROY_LIST::set).build());
                cat.addEntry(eb.startBooleanToggle(
                                t("void.no_pathfind"),
                                ActiveTaskConfig.VOID_NO_PATHFIND.get())
                        .setDefaultValue(ActiveTaskConfig.VOID_NO_PATHFIND.getDefault())
                        .setTooltip(t("void.no_pathfind.tip"))
                        .setSaveConsumer(ActiveTaskConfig.VOID_NO_PATHFIND::set).build());
            }
            case "dam_fill" -> {
                // v79.63.21 补缺口 (用户裁定: 任务自己的参数归本任务 cloth 子屏): 全局键一直存在但**无任何 GUI 入口**
                cat.addEntry(eb.startIntField(
                                t("dam_fill.chunks"), ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS.get())
                        .setDefaultValue(ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS.getDefault())
                        .setMin(1).setMax(256)
                        .setTooltip(t("dam_fill.chunks.tip"))
                        .setSaveConsumer(ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS::set).build());
            }
            case "jiuhu_milk" -> {
                cat.addEntry(eb.startBooleanToggle(t("jiuhu.auto_feed"),
                                PassiveTaskConfig.JIUHU_MILK_AUTO_FEED.get())
                        .setDefaultValue(PassiveTaskConfig.JIUHU_MILK_AUTO_FEED.getDefault())
                        .setTooltip(t("jiuhu.auto_feed.tip"))
                        .setSaveConsumer(PassiveTaskConfig.JIUHU_MILK_AUTO_FEED::set).build());
                cat.addEntry(eb.startBooleanToggle(t("jiuhu.toggle"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_ENABLED.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_ENABLED.getDefault())
                        .setTooltip(t("jiuhu.toggle.tip"))
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_ENABLED::set).build());
                cat.addEntry(eb.startBooleanToggle(t("jiuhu.wild_extra"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA.getDefault())
                        .setTooltip(t("jiuhu.wild_extra.tip"))
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA::set).build());
                cat.addEntry(eb.startIntField(t("jiuhu.tamed_resistance"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.getDefault())
                        .setMin(20).setMax(12000)
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS::set).build());
                cat.addEntry(eb.startIntField(t("jiuhu.tamed_regen"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.getDefault())
                        .setMin(20).setMax(12000)
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS::set).build());
                cat.addEntry(eb.startIntField(t("jiuhu.wild_regen"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.WILD_REGENERATION_TICKS.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.WILD_REGENERATION_TICKS.getDefault())
                        .setMin(20).setMax(12000)
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.WILD_REGENERATION_TICKS::set).build());
            }
            default -> cat.addEntry(eb.startTextDescription(
                    t("none")).build());
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

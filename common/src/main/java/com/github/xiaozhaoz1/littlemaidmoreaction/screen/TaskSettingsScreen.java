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
 */
public final class TaskSettingsScreen {

    private TaskSettingsScreen() {}

    /** 任务中文名 — lang key: task.littlemaidmoreaction.<taskType> */
    public static Component title(String taskType) {
        return Component.translatable("task.littlemaidmoreaction." + taskType);
    }

    public static Screen create(Screen parent, String taskType) {
        ConfigBuilder root = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(title(taskType));
        ConfigEntryBuilder eb = root.entryBuilder();
        ConfigCategory cat = root.getOrCreateCategory(Component.literal("任务自定义"));

        switch (taskType) {
            case "craft_chain" -> {
                cat.addEntry(eb.startTextField(
                                Component.literal("默认产物"), ActiveTaskConfig.CRAFT_DEFAULT_PRODUCT.get())
                        .setDefaultValue(ActiveTaskConfig.CRAFT_DEFAULT_PRODUCT.getDefault())
                        .setTooltip(Component.literal("无目标时使用该物品作为合成产物 (物品id, 如 minecraft:golden_apple)"))
                        .setSaveConsumer(ActiveTaskConfig.CRAFT_DEFAULT_PRODUCT::set).build());
                cat.addEntry(eb.startIntField(
                                Component.literal("产物数量上限"), ActiveTaskConfig.CRAFT_MAX_PRODUCTS.get())
                        .setDefaultValue(ActiveTaskConfig.CRAFT_MAX_PRODUCTS.getDefault())
                        .setMin(-1).setMax(1024)
                        .setTooltip(Component.literal("-1=无限; 女仆累计合成达到上限后停止 (TLM 任务设置可 per-maid 覆盖)"))
                        .setSaveConsumer(ActiveTaskConfig.CRAFT_MAX_PRODUCTS::set).build());
            }
            case "furnace" -> {
                cat.addEntry(eb.startStrList(
                                Component.literal("烧炼黑名单"),
                                new ArrayList<>(ActiveTaskConfig.FURNACE_BLACKLIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.FURNACE_BLACKLIST.getDefault()))
                        .setTooltip(Component.literal("女仆不烧炼这些物品 (物品id列表, 支持 modid:*)"))
                        .setSaveConsumer(ActiveTaskConfig.FURNACE_BLACKLIST::set).build());
                cat.addEntry(eb.startStrList(
                                Component.literal("烧炼白名单"),
                                new ArrayList<>(ActiveTaskConfig.FURNACE_WHITELIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.FURNACE_WHITELIST.getDefault()))
                        .setTooltip(Component.literal("非空时只烧炼名单内物品; per-maid 名单见 TLM 任务设置"))
                        .setSaveConsumer(ActiveTaskConfig.FURNACE_WHITELIST::set).build());
            }
            case "jukebox" -> {
                cat.addEntry(eb.startIntField(
                                Component.literal("播放等待时长 (tick)"), ActiveTaskConfig.JUKEBOX_WAIT_TICKS.get())
                        .setDefaultValue(ActiveTaskConfig.JUKEBOX_WAIT_TICKS.getDefault())
                        .setMin(20).setMax(24000)
                        .setTooltip(Component.literal("6000=5分钟; 播放完才换碟"))
                        .setSaveConsumer(ActiveTaskConfig.JUKEBOX_WAIT_TICKS::set).build());
                cat.addEntry(eb.startStrList(
                                Component.literal("唱片黑名单"),
                                new ArrayList<>(ActiveTaskConfig.JUKEBOX_BLACKLIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.JUKEBOX_BLACKLIST.getDefault()))
                        .setTooltip(Component.literal("女仆不播放的唱片id列表"))
                        .setSaveConsumer(ActiveTaskConfig.JUKEBOX_BLACKLIST::set).build());
                cat.addEntry(eb.startStrList(
                                Component.literal("唱片白名单"),
                                new ArrayList<>(ActiveTaskConfig.JUKEBOX_WHITELIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.JUKEBOX_WHITELIST.getDefault()))
                        .setTooltip(Component.literal("非空时只播放名单内唱片"))
                        .setSaveConsumer(ActiveTaskConfig.JUKEBOX_WHITELIST::set).build());
            }
            case "arm_transfer" -> {
                cat.addEntry(eb.startStrList(
                                Component.literal("搬运黑名单"),
                                new ArrayList<>(ActiveTaskConfig.ARM_BLACKLIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.ARM_BLACKLIST.getDefault()))
                        .setTooltip(Component.literal("女仆不搬运的物品id列表"))
                        .setSaveConsumer(ActiveTaskConfig.ARM_BLACKLIST::set).build());
                cat.addEntry(eb.startStrList(
                                Component.literal("搬运白名单"),
                                new ArrayList<>(ActiveTaskConfig.ARM_WHITELIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.ARM_WHITELIST.getDefault()))
                        .setTooltip(Component.literal("非空时只搬运名单内物品"))
                        .setSaveConsumer(ActiveTaskConfig.ARM_WHITELIST::set).build());
            }
            case "collect_wood", "collect_ore" -> {
                cat.addEntry(eb.startStrList(
                                Component.literal("采集黑名单 (方块)"),
                                new ArrayList<>(ActiveTaskConfig.COLLECT_BLACKLIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.COLLECT_BLACKLIST.getDefault()))
                        .setTooltip(Component.literal("女仆不砍/不挖的方块id列表"))
                        .setSaveConsumer(ActiveTaskConfig.COLLECT_BLACKLIST::set).build());
                cat.addEntry(eb.startStrList(
                                Component.literal("采集白名单 (方块)"),
                                new ArrayList<>(ActiveTaskConfig.COLLECT_WHITELIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.COLLECT_WHITELIST.getDefault()))
                        .setTooltip(Component.literal("非空时只砍/只挖名单内方块"))
                        .setSaveConsumer(ActiveTaskConfig.COLLECT_WHITELIST::set).build());
            }
            case "bell_ring" -> {
                cat.addEntry(eb.startDoubleField(Component.literal("音量"),
                                ActiveTaskConfig.BELL_VOLUME.get())
                        .setDefaultValue(ActiveTaskConfig.BELL_VOLUME.getDefault())
                        .setMin(0.0).setMax(2.0)
                        .setTooltip(Component.literal("敲钟音量"))
                        .setSaveConsumer(ActiveTaskConfig.BELL_VOLUME::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("音调"),
                                ActiveTaskConfig.BELL_PITCH.get())
                        .setDefaultValue(ActiveTaskConfig.BELL_PITCH.getDefault())
                        .setMin(0.5).setMax(2.0)
                        .setTooltip(Component.literal("敲钟音调 (1.0 = 原声)"))
                        .setSaveConsumer(ActiveTaskConfig.BELL_PITCH::set).build());
                cat.addEntry(eb.startIntField(Component.literal("敲钟间隔 (tick)"),
                                ActiveTaskConfig.BELL_RING_INTERVAL.get())
                        .setDefaultValue(ActiveTaskConfig.BELL_RING_INTERVAL.getDefault())
                        .setMin(30).setMax(12000)
                        .setTooltip(Component.literal("两次敲钟最小间隔, 30 = 1.5 秒; 单女仆覆盖见 TLM 任务设置标签页 (v67.13)"))
                        .setSaveConsumer(ActiveTaskConfig.BELL_RING_INTERVAL::set).build());
            }
            case "block_interact" -> cat.addEntry(eb.startTextDescription(
                    Component.literal("全局设置见主界面「右键交互」分类: 标记/绑定物品、交互距离、触发范围、定时器默认间隔")).build());
            case "ai_control" -> {
                cat.addEntry(eb.startTextField(
                                Component.literal("默认 LLM 模型"), ActiveTaskConfig.AI_LLM_PROVIDER.get())
                        .setDefaultValue(ActiveTaskConfig.AI_LLM_PROVIDER.getDefault())
                        .setTooltip(Component.literal("Numen G 面板创建的模型条目名; 空=不绑定; per-maid 覆盖见 TLM 任务设置标签页 (v74)"))
                        .setSaveConsumer(ActiveTaskConfig.AI_LLM_PROVIDER::set).build());
                cat.addEntry(eb.startTextField(
                                Component.literal("默认声线"), ActiveTaskConfig.AI_VOICE.get())
                        .setDefaultValue(ActiveTaskConfig.AI_VOICE.getDefault())
                        .setTooltip(Component.literal("Numen G 面板创建的声线条目名; 空=不绑定; per-maid 覆盖见 TLM 任务设置标签页 (v74)"))
                        .setSaveConsumer(ActiveTaskConfig.AI_VOICE::set).build());
                // 假人随机台词气泡/语音 (SHELVED 假人头顶气泡 + 女仆语音包音频)
                cat.addEntry(eb.startBooleanToggle(
                                Component.literal("随机台词气泡"), PassiveTaskConfig.COMPANION_CHAT_ENABLED.get())
                        .setDefaultValue(PassiveTaskConfig.COMPANION_CHAT_ENABLED.getDefault())
                        .setTooltip(Component.literal("假人定时随机颜文字气泡 (需 TLM 开启表情)"))
                        .setSaveConsumer(PassiveTaskConfig.COMPANION_CHAT_ENABLED::set).build());
                cat.addEntry(eb.startIntSlider(
                                Component.literal("随机台词间隔 (tick)"), PassiveTaskConfig.COMPANION_CHAT_RATE.get(), 20, 24000)
                        .setDefaultValue(PassiveTaskConfig.COMPANION_CHAT_RATE.getDefault())
                        .setTooltip(Component.literal("20 = 1 秒, 默认 1200 = 60 秒"))
                        .setSaveConsumer(PassiveTaskConfig.COMPANION_CHAT_RATE::set).build());
                cat.addEntry(eb.startBooleanToggle(
                                Component.literal("随机语音"), PassiveTaskConfig.COMPANION_VOICE_ENABLED.get())
                        .setDefaultValue(PassiveTaskConfig.COMPANION_VOICE_ENABLED.getDefault())
                        .setTooltip(Component.literal("假人随机播放女仆语音包音频 (需女仆在 TLM 配置选择语音包)"))
                        .setSaveConsumer(PassiveTaskConfig.COMPANION_VOICE_ENABLED::set).build());
            }
            // v79.62.1 哈气/奶开关迁至管线子界面 (从 ClothSettingsScreen 主屏全局分类迁入)
            case "haqi" -> {
                cat.addEntry(eb.startBooleanToggle(Component.literal("哈气任务总开关"),
                                PassiveTaskConfig.HAQI_ENABLED.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_ENABLED.getDefault())
                        .setTooltip(Component.literal("默认关闭; 开启后女仆靠近其他女仆时概率触发"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_ENABLED::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("触发概率"),
                                PassiveTaskConfig.HAQI_CHANCE.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_CHANCE.getDefault())
                        .setMin(0.0).setMax(1.0)
                        .setTooltip(Component.literal("2 格内有其他女仆时的触发概率, 默认 0.1 = 10%"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_CHANCE::set).build());
                cat.addEntry(eb.startIntField(Component.literal("基础看着时长 (tick)"),
                                PassiveTaskConfig.HAQI_DURATION_TICKS.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_DURATION_TICKS.getDefault())
                        .setMin(20).setMax(1200)
                        .setTooltip(Component.literal("60 = 3 秒; 总看着时长 = 基础 + 音频实际时长"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_DURATION_TICKS::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("音频音量"),
                                PassiveTaskConfig.HAQI_VOLUME.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_VOLUME.getDefault())
                        .setMin(0.0).setMax(2.0)
                        .setTooltip(Component.literal("哈气音频播放音量, 默认 1.0"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_VOLUME::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("挥击概率"),
                                PassiveTaskConfig.HAQI_HIT_CHANCE.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_HIT_CHANCE.getDefault())
                        .setMin(0.0).setMax(1.0)
                        .setTooltip(Component.literal("LOOK 期间概率挥击目标一下, 默认 0.3 = 30%"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_CHANCE::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("挥击伤害"),
                                PassiveTaskConfig.HAQI_HIT_DAMAGE.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_HIT_DAMAGE.getDefault())
                        .setMin(0.0).setMax(100.0)
                        .setTooltip(Component.literal("挥击伤害, 默认 1.0 = 一点血"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_DAMAGE::set).build());
                cat.addEntry(eb.startBooleanToggle(Component.literal("哈气对主人开关"),
                                PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.getDefault())
                        .setTooltip(Component.literal("默认关闭; 需哈气总开关开启; 只控制对主人哈气"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_ENABLED_TO_OWNER::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("对主人触发概率"),
                                PassiveTaskConfig.HAQI_CHANCE_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_CHANCE_TO_OWNER.getDefault())
                        .setMin(0.0).setMax(1.0)
                        .setTooltip(Component.literal("旁边无女仆时对 2 格内主人的触发概率, 默认 0.1"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_CHANCE_TO_OWNER::set).build());
                cat.addEntry(eb.startIntField(Component.literal("对主人看着时长 (tick)"),
                                PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER.getDefault())
                        .setMin(20).setMax(1200)
                        .setTooltip(Component.literal("60 = 3 秒"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("对主人音频音量"),
                                PassiveTaskConfig.HAQI_VOLUME_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_VOLUME_TO_OWNER.getDefault())
                        .setMin(0.0).setMax(2.0)
                        .setSaveConsumer(PassiveTaskConfig.HAQI_VOLUME_TO_OWNER::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("对主人挥击概率"),
                                PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER.getDefault())
                        .setMin(0.0).setMax(1.0)
                        .setTooltip(Component.literal("LOOK 期间概率拍主人一下, 默认 0.3"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER::set).build());
                cat.addEntry(eb.startDoubleField(Component.literal("对主人挥击伤害"),
                                PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER.get())
                        .setDefaultValue(PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER.getDefault())
                        .setMin(0.0).setMax(100.0)
                        .setTooltip(Component.literal("挥击伤害, 默认 1.0; 主人不反击"))
                        .setSaveConsumer(PassiveTaskConfig.HAQI_HIT_DAMAGE_TO_OWNER::set).build());
            }
            case "void_excavation" -> {
                // v79.62.1 挖空置域: 全局默认区块数 (区域 = 起点为中心 N×N 区块)
                cat.addEntry(eb.startIntField(
                                Component.literal("默认区块数"), ActiveTaskConfig.VOID_DEFAULT_CHUNKS.get())
                        .setDefaultValue(ActiveTaskConfig.VOID_DEFAULT_CHUNKS.getDefault())
                        .setMin(1).setMax(256)
                        .setTooltip(Component.literal("起点为中心 N×N 区块全挖 (垂直基岩上到世界最高); 单女仆区块数可在 TLM 任务设置覆盖"))
                        .setSaveConsumer(ActiveTaskConfig.VOID_DEFAULT_CHUNKS::set).build());
                // v79.62.1 用户裁定 (黑名单→销毁名单): 名单内物品挖出即销毁; 关寻路 (全局默认, 单女仆 TLM 可覆盖)
                cat.addEntry(eb.startStrList(
                                Component.literal("销毁名单 (物品id)"),
                                new ArrayList<>(ActiveTaskConfig.VOID_DESTROY_LIST.get()))
                        .setDefaultValue(new ArrayList<>(ActiveTaskConfig.VOID_DESTROY_LIST.getDefault()))
                        .setTooltip(Component.literal("名单内物品挖出即销毁消失 (不进背包不落地); 其余进背包→输出箱; 背包满停止"))
                        .setSaveConsumer(ActiveTaskConfig.VOID_DESTROY_LIST::set).build());
                cat.addEntry(eb.startBooleanToggle(
                                Component.literal("关闭寻路"),
                                ActiveTaskConfig.VOID_NO_PATHFIND.get())
                        .setDefaultValue(ActiveTaskConfig.VOID_NO_PATHFIND.getDefault())
                        .setTooltip(Component.literal("开启后不 BFS 寻路, 传送到区块中间直接挖 (避免寻路卡顿)"))
                        .setSaveConsumer(ActiveTaskConfig.VOID_NO_PATHFIND::set).build());
            }
            case "dam_fill" -> {
                // v79.63.21 补缺口 (用户裁定: 任务自己的参数归本任务 cloth 子屏): 全局键一直存在但**无任何 GUI 入口**
                cat.addEntry(eb.startIntField(
                                Component.literal("默认区块数"), ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS.get())
                        .setDefaultValue(ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS.getDefault())
                        .setMin(1).setMax(256)
                        .setTooltip(Component.literal("起点为中心 N×N 区块 (默认 1 = 单区块); 单女仆可在 TLM 任务设置页覆盖"))
                        .setSaveConsumer(ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS::set).build());
            }
            case "jiuhu_milk" -> {
                cat.addEntry(eb.startBooleanToggle(Component.literal("酒狐奶自动喂食"),
                                PassiveTaskConfig.JIUHU_MILK_AUTO_FEED.get())
                        .setDefaultValue(PassiveTaskConfig.JIUHU_MILK_AUTO_FEED.getDefault())
                        .setTooltip(Component.literal("主人受伤 (<70%血量) 时自动喂奶 (清负面+buff)"))
                        .setSaveConsumer(PassiveTaskConfig.JIUHU_MILK_AUTO_FEED::set).build());
                cat.addEntry(eb.startBooleanToggle(Component.literal("奶主开关"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_ENABLED.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_ENABLED.getDefault())
                        .setTooltip(Component.literal("空桶右键女仆挤奶"))
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_ENABLED::set).build());
                cat.addEntry(eb.startBooleanToggle(Component.literal("野生奶副开关"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA.getDefault())
                        .setTooltip(Component.literal("开=未驯服产野生酒狐奶, 关=未驯服也产酒狐奶桶"))
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA::set).build());
                cat.addEntry(eb.startIntField(Component.literal("奶桶抗性时长 (tick)"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.getDefault())
                        .setMin(20).setMax(12000)
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS::set).build());
                cat.addEntry(eb.startIntField(Component.literal("奶桶恢复时长 (tick)"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.getDefault())
                        .setMin(20).setMax(12000)
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS::set).build());
                cat.addEntry(eb.startIntField(Component.literal("野生奶恢复时长 (tick)"),
                                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.WILD_REGENERATION_TICKS.get())
                        .setDefaultValue(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.WILD_REGENERATION_TICKS.getDefault())
                        .setMin(20).setMax(12000)
                        .setSaveConsumer(com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.WILD_REGENERATION_TICKS::set).build());
            }
            default -> cat.addEntry(eb.startTextDescription(
                    Component.literal("该任务暂无自定义设置")).build());
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

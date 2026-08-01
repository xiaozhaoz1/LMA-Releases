package littlemaidmoreaction.littlemaidmoreaction.screen;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import littlemaidmoreaction.littlemaidmoreaction.config.ActiveTaskConfig;
import littlemaidmoreaction.littlemaidmoreaction.network.ConfigSyncPacket;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;

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

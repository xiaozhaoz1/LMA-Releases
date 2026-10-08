package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * v67.3: 通用黑白名单配置屏幕 — furnace / jukebox / arm_transfer 共用。
 *
 * <p>黑名单 + 白名单 EditBox (逗号分隔物品id, 支持 modid:* 通配),
 * 应用按钮通过 {@link TaskPipeline#ACTION_SET_LIST} 整体覆盖 per-maid 名单
 * (存各任务 pipelineConfig 的 blacklist/whitelist 键)。
 */
public class ItemListConfigScreen extends LmaTaskConfigScreen<ItemListConfigMenu> {

    private final String taskType;
    private EditBox blackBox;
    private EditBox whiteBox;
    /** 配置同步标志 — 首次收到服务端配置后不再覆盖 EditBox (防打字被重写) */
    private boolean synced;

    public ItemListConfigScreen(ItemListConfigMenu menu, Inventory playerInv, Component title, String taskType) {
        super(menu, playerInv, title);
        this.taskType = taskType;
    }

    /** MenuScreens 3 参工厂注册用 — taskType 从标题 lang key 推导 (task.littlemaidmoreaction.&lt;taskType&gt;) */
    public ItemListConfigScreen(ItemListConfigMenu menu, Inventory playerInv, Component title) {
        this(menu, playerInv, title, taskTypeFromTitle(title));
    }

    private static String taskTypeFromTitle(Component title) {
        if (title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            String key = tc.getKey();
            String prefix = "task.littlemaidmoreaction.";
            if (key.startsWith(prefix)) {
                return key.substring(prefix.length());
            }
        }
        return "";
    }

    @Override
    protected String getTaskType() {
        return taskType;
    }

    // ── initAdditionWidgets: 请求配置 + EditBox + 应用按钮 (renderBg 基类默认委托) ──

    /**
     * 布局 (v79.63.5 统一规范): 起点 y = topPos + 40 · 行距 26 · 控件高 20 · 标签左对齐 contentX()
     * 控件宽 150 右对齐 contentRight() (面板不够宽时自动缩 ✓)。熔炉/砍树共用本屏, 仅 taskType 不同 ✓
     */
    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), taskType);

        int labelX = contentX();
        int rowW = Math.min(150, Math.max(80, contentRight() - (labelX + 60)));
        int rowX = Math.max(labelX + 60, contentRight() - rowW);
        int y = rowY(0);

        blackBox = new EditBox(font, rowX, y - 2, rowW, 20,
                Component.translatable("screen.littlemaidmoreaction.item_list.black"));
        blackBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.littlemaidmoreaction.item_list.black.tip")));
        whiteBox = new EditBox(font, rowX, y + 26 - 2, rowW, 20,
                Component.translatable("screen.littlemaidmoreaction.item_list.white"));
        whiteBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.littlemaidmoreaction.item_list.white.tip")));
        addRenderableWidget(blackBox);
        addRenderableWidget(whiteBox);

        int by = rowY(2);
        int half = rowW / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.littlemaidmoreaction.item_list.apply"),
                btn -> applyLists(m)).pos(rowX, by - 2).size(half - 4, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.littlemaidmoreaction.item_list.apply.tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.littlemaidmoreaction.item_list.clear"),
                btn -> { blackBox.setValue(""); whiteBox.setValue(""); applyLists(m); })
                .pos(rowX + half, by - 2).size(rowW - half, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.littlemaidmoreaction.item_list.clear.tip"))).build());
    }
    // ── renderAddition: 从 menu.config 同步 EditBox 值 ──

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        if (!synced && !cfg.isEmpty()) {
            blackBox.setValue(join(ItemFilters.maidList(cfg, ItemFilters.KEY_BLACKLIST)));
            whiteBox.setValue(join(ItemFilters.maidList(cfg, ItemFilters.KEY_WHITELIST)));
            synced = true;
        }
        // 中文标签 (黑名单框 topPos+46, 白名单框 +74; 标签置框上方 12px)
        int cx = contentX();
        // v79.63.5 规范: 标签左列 (contentX, 行 y + 6), 与右侧控件同排 ✓ + i18n ✓
        g.drawString(font, Component.translatable("screen.littlemaidmoreaction.item_list.black"), cx, topPos + 44, 0xFFFFFF);
        g.drawString(font, Component.translatable("screen.littlemaidmoreaction.item_list.white"), cx, topPos + 70, 0xFFFFFF);
    }

    // ── 业务 ──

    private void applyLists(EntityMaid maid) {
        CompoundTag cfg = getMenu().getConfig();
        cfg.put(ItemFilters.KEY_BLACKLIST, parseList(blackBox.getValue()));
        cfg.put(ItemFilters.KEY_WHITELIST, parseList(whiteBox.getValue()));
        if (maid != null) {
            sendList(maid, ItemFilters.KEY_BLACKLIST, blackBox.getValue());
            sendList(maid, ItemFilters.KEY_WHITELIST, whiteBox.getValue());
        }
    }

    private void sendList(EntityMaid maid, String key, String value) {
        sendSetList(key, value);
    }

    /** EditBox 逗号文本 → NBT ListTag */
    private static net.minecraft.nbt.ListTag parseList(String text) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String s : text.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) list.add(net.minecraft.nbt.StringTag.valueOf(s));
        }
        return list;
    }

    /** NBT ListTag → 逗号文本 */
    private static String join(List<String> list) {
        return String.join(", ", list);
    }
}

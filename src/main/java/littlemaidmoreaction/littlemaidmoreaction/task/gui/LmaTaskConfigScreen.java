package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;

/**
 * LMA 任务配置屏幕基类 — 自绘 TLM 风格 GUI 框架。
 *
 * <p>TLM (Parchment映射) 与 LMA (Mojang映射) 映射冲突导致 {@code super.renderBg()} 编译失败；
 * 且反射调用 TLM {@code AbstractMaidContainerGui.renderBg} 在 Embeddium 0.3.31 + Oculus
 * 环境下失败 (v67 实测: 任务设置界面整屏泥土, 主界面却正常 — 反射 invoke 不可靠)。
 *
 * <p>因此本类直接 blit TLM 的 GUI 纹理资源自绘完整框架:
 * 暗背景层 + 主背景 + 女仆3D模型 + 血量/护甲/经验/好感 4 条 + 顶/底部框 +
 * 顶部 3 个切换 tab + 右侧 2 个侧栏按钮 + 日程按钮 + 任务切换框 + 侧栏贴图。
 * 坐标/纹理片段与 TLM 源码逐一核对:
 * renderBg / drawMaidCharacter / drawBaseInfoGui / MaidTabs / MaidSideTabs /
 * MaidTabButton / MaidSideTabButton / ScheduleButton / drawSideTabGui。
 * TLM 纹理由 TLM jar 提供, 无需本地资源。
 *
 * <p>与 TLM init() 添加的按钮重叠自绘 — 同坐标同纹理, 视觉一致, 双保险。
 *
 * <h3>子类覆写清单</h3>
 * <ol>
 *   <li>{@link #renderBg} — 调 {@link #drawMaidFramework} 后绘制自定义面板</li>
 *   <li>{@link #initAdditionWidgets} — 添加按钮 (TLM 标准钩子)</li>
 *   <li>{@link #renderAddition} — 渲染覆盖文字 (TLM 标准钩子)</li>
 * </ol>
 */
public abstract class LmaTaskConfigScreen<T extends TaskConfigContainer> extends MaidTaskConfigGui<T> {

    /** TLM 主 GUI 纹理 (由 TLM jar 提供, 256x256) */
    private static final ResourceLocation TLM_MAIN_BG = new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_main.png");
    /** TLM 侧栏纹理 (状态条/tab 片段) */
    private static final ResourceLocation TLM_SIDE = new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_side.png");
    /** TLM 按钮纹理 (日程/任务切换片段) */
    private static final ResourceLocation TLM_BUTTON = new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_button.png");

    public LmaTaskConfigScreen(T screenContainer, Inventory inv, Component titleIn) {
        super(screenContainer, inv, titleIn);
    }

    /**
     * 自绘 TLM 风格 GUI 框架 — 只用 blit + drawString + renderBackground(try/catch)。
     * 禁止裸 fill/fillGradient (Embeddium 顶点缓冲崩溃源)。
     */
    protected final void drawMaidFramework(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 暗背景层 (同 TLM renderBg 的 renderBackground — 平时正常, 异常降级为亮背景不崩游戏)
        try {
            renderBackground(g);
        } catch (Exception ignored) {
        }

        // 主背景 (TLM renderBg: blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight))
        g.blit(TLM_MAIN_BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        final EntityMaid maid = getMaid();
        if (maid == null) {
            return;
        }

        // 女仆 3D 模型 (TLM drawMaidCharacter: scissor 区域 + renderEntityInInventoryFollowsMouse)
        double scale = getMinecraft().getWindow().getGuiScale();
        RenderSystem.enableScissor((int) ((leftPos + 6) * scale), (int) ((topPos + 107 + 42) * scale),
                (int) (67 * scale), (int) (95 * scale));
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + 40, topPos + 100, 40,
                (leftPos + 40) - mouseX, (topPos + 70 - 20) - mouseY, maid);
        RenderSystem.disableScissor();

        // 顶部标题框 + 4 状态条 + 底部框 (TLM drawBaseInfoGui 坐标逐项核对)
        g.blit(TLM_SIDE, leftPos + 94, topPos + 7, 107, 0, 149, 21);
        double hp = maid.getHealth() / maid.getMaxHealth();
        drawBar(g, topPos + 113, hp, (int) maid.getHealth(), 0, 18);
        double armor = Math.min(maid.getAttributeValue(Attributes.ARMOR) / 20, 1.0);
        drawBar(g, topPos + 124, armor, (int) maid.getArmorValue(), 9, 23);
        int exp = maid.getExperience();
        drawBar(g, topPos + 135, (exp % 120) / 120.0, exp / 120, 18, 28);
        double favor = maid.getFavorabilityManager().getLevelPercent();
        drawBar(g, topPos + 146, favor, maid.getFavorabilityManager().getLevel(), 27, 33);
        g.blit(TLM_SIDE, leftPos + 6, topPos + 178, 0, 47, 67, 25);

        // 顶部 3 个切换 tab (MaidTabs.getTabs: 94/119/144, topPos+5; MaidTabButton 框 21/图标 47)
        drawTab(g, 107, 94);
        drawTab(g, 132, 119);
        drawTab(g, 157, 144);

        // 右侧 2 个侧栏按钮 (MaidSideTabs: leftPos+251, topPos+37, 间距25; MaidSideTabButton 框 209/图标 193)
        drawSideTab(g, 0);
        drawSideTab(g, 1);

        // 日程按钮 (ScheduleButton: BUTTON 纹理 82, 43+14*ordinal)
        g.blit(TLM_BUTTON, leftPos + 9, topPos + 187, 82, 43 + 14 * maid.getSchedule().ordinal(), 61, 13, 256, 256);

        // 任务切换框 (addTaskSwitchButton: BUTTON 0,42)
        g.blit(TLM_BUTTON, leftPos + 4, topPos + 159, 0, 42, 71, 21);

        // 侧栏底部贴图 (drawSideTabGui: SIDE 235,107)
        g.blit(TLM_SIDE, leftPos + 251 + 5, topPos + 28 + 9, 235, 107, 21, 50);
    }

    /** 顶部 tab 框+图标 — MaidTabButton: 框 (x, y, left, 21, 24, 26) + 图标 (x+4, y+6, left, 47, 16, 16) */
    private void drawTab(GuiGraphics g, int left, int xOffset) {
        int x = leftPos + xOffset;
        int y = topPos + 5;
        g.blit(TLM_SIDE, x, y, left, 21, 24, 26, 256, 256);
        g.blit(TLM_SIDE, x + 4, y + 6, left, 47, 16, 16, 256, 256);
    }

    /** 右侧侧栏按钮框+图标 — MaidSideTabButton: 框 (x+2, y, 209, top, 26, 24) + 图标 (x+6, y+4, 193, top+4, 16, 16) */
    private void drawSideTab(GuiGraphics g, int index) {
        int top = 107 + index * 25;
        int x = leftPos + 251;
        int y = topPos + 37 + index * 25;
        g.blit(TLM_SIDE, x + 2, y, 209, top, 26, 24, 256, 256);
        g.blit(TLM_SIDE, x + 6, y + 4, 193, top + 4, 16, 16, 256, 256);
    }

    /** 状态条 — TLM drawBaseInfoGui: 图标(iconU) + 背景 + 填充条(barV) + 数字(0.5 缩放) */
    private void drawBar(GuiGraphics g, int topY, double ratio, int value, int iconU, int barV) {
        g.blit(TLM_SIDE, leftPos + 53, topY, iconU, 0, 9, 9);
        g.blit(TLM_SIDE, leftPos + 5, topY, 0, 9, 47, 9);
        double r = Math.max(0, Math.min(ratio, 1));
        g.blit(TLM_SIDE, leftPos + 7, topY + 2, 2, barV, (int) (43 * r), 5);
        drawNumberScale(g, value, leftPos + 63, topY + 1);
    }

    /** 数字 (0.5 缩放) — TLM drawNumberScale 复刻 */
    private void drawNumberScale(GuiGraphics g, int value, int posX, int posY) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1);
        g.drawString(font, String.valueOf(value), posX * 2, posY * 2 + font.lineHeight / 2, 0x333333, false);
        g.pose().popPose();
    }
}

package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * TLM 界面自绘工具 (v67.9 瘦身: 配置 GUI 工厂已上提 {@link littlemaidmoreaction.littlemaidmoreaction.task.api.TaskConfigGuiFactory})。
 *
 * <p>{@link #drawMaidFramework} — 直接 blit TLM 纹理资源复刻完整女仆 GUI 框架
 * (主背景/女仆3D/4状态条/tab/侧栏/日程)。TLM renderBg 反射在 Embeddium 环境不可靠 (v67),
 * 统一在此自绘, 任何 TLM 女仆 GUI 可复用。
 */
public final class TaskConfigGui {

    /** TLM 主 GUI 纹理 (由 TLM jar 提供, 256x256) */
    private static final ResourceLocation TLM_MAIN_BG = new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_main.png");
    /** TLM 侧栏纹理 (状态条/tab 片段) */
    private static final ResourceLocation TLM_SIDE = new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_side.png");
    /** TLM 按钮纹理 (日程/任务切换片段) */
    private static final ResourceLocation TLM_BUTTON = new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_button.png");
    /** v67.15: TLM 任务列表纹理 (任务栏选择框槽位) */
    private static final ResourceLocation TLM_TASK = new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_task.png");

    private TaskConfigGui() {}

    /**
     * 自绘 TLM 风格女仆 GUI 框架: 暗背景层 + 主背景 + 女仆3D模型 + 4 状态条 +
     * 顶部 3 tab + 右侧 2 侧栏按钮 + 日程按钮 + 任务切换框 + 侧栏贴图。
     * 只用 blit + drawString + renderBackground(try/catch) — 禁止裸 fill/fillGradient
     * (Embeddium 顶点缓冲崩溃源)。
     *
     * <p>坐标/纹理片段与 TLM 源码逐一核对: renderBg / drawMaidCharacter / drawBaseInfoGui /
     * MaidTabs / MaidSideTabs / MaidTabButton / MaidSideTabButton / ScheduleButton / drawSideTabGui。
     * 与 TLM init() 添加的按钮重叠自绘 — 同坐标同纹理, 视觉一致, 双保险。
     *
     * <p>leftPos/topPos/imageWidth/imageHeight 为 AbstractContainerScreen 的 protected 字段,
     * 由调用方 (Screen 子类) 读取后传入。
     *
     * @param screen LMA 任务配置屏幕 (需 getMaid/getMinecraft/renderBackground 公开方法)
     */
    public static void drawMaidFramework(LmaTaskConfigScreen<?> screen, GuiGraphics g,
                                         int leftPos, int topPos, int imageWidth, int imageHeight,
                                         Font font, int mouseX, int mouseY, float partialTick) {
        // 暗背景层 (同 TLM renderBg 的 renderBackground — 平时正常, 异常降级为亮背景不崩游戏)
        try {
            screen.renderBackground(g);
        } catch (Exception ignored) {
        }

        // 主背景 (TLM renderBg: blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight))
        g.blit(TLM_MAIN_BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        final EntityMaid maid = screen.getMaid();
        if (maid == null) {
            return;
        }

        // 女仆 3D 模型 (TLM drawMaidCharacter: scissor 区域 + renderEntityInInventoryFollowsMouse)
        double scale = screen.getMinecraft().getWindow().getGuiScale();
        RenderSystem.enableScissor((int) ((leftPos + 6) * scale), (int) ((topPos + 107 + 42) * scale),
                (int) (67 * scale), (int) (95 * scale));
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + 40, topPos + 100, 40,
                (leftPos + 40) - mouseX, (topPos + 70 - 20) - mouseY, maid);
        RenderSystem.disableScissor();

        // 顶部标题框 + 4 状态条 + 底部框 (TLM drawBaseInfoGui 坐标逐项核对)
        g.blit(TLM_SIDE, leftPos + 94, topPos + 7, 107, 0, 149, 21);
        double hp = maid.getHealth() / maid.getMaxHealth();
        drawBar(g, leftPos, font, topPos + 113, hp, (int) maid.getHealth(), 0, 18);
        double armor = Math.min(maid.getAttributeValue(Attributes.ARMOR) / 20, 1.0);
        drawBar(g, leftPos, font, topPos + 124, armor, (int) maid.getArmorValue(), 9, 23);
        int exp = maid.getExperience();
        drawBar(g, leftPos, font, topPos + 135, (exp % 120) / 120.0, exp / 120, 18, 28);
        double favor = maid.getFavorabilityManager().getLevelPercent();
        drawBar(g, leftPos, font, topPos + 146, favor, maid.getFavorabilityManager().getLevel(), 27, 33);
        g.blit(TLM_SIDE, leftPos + 6, topPos + 178, 0, 47, 67, 25);

        // 顶部 3 个切换 tab (MaidTabs.getTabs: 94/119/144, topPos+5; MaidTabButton 框 21/图标 47)
        drawTab(g, leftPos, topPos, 107, 94);
        drawTab(g, leftPos, topPos, 132, 119);
        drawTab(g, leftPos, topPos, 157, 144);

        // 右侧 2 个侧栏按钮 (MaidSideTabs: leftPos+251, topPos+37, 间距25; MaidSideTabButton 框 209/图标 193)
        drawSideTab(g, leftPos, topPos, 0);
        drawSideTab(g, leftPos, topPos, 1);

        // 日程按钮 (ScheduleButton: BUTTON 纹理 82, 43+14*ordinal)
        g.blit(TLM_BUTTON, leftPos + 9, topPos + 187, 82, 43 + 14 * maid.getSchedule().ordinal(), 61, 13, 256, 256);

        // 任务切换框 (addTaskSwitchButton: BUTTON 0,42)
        g.blit(TLM_BUTTON, leftPos + 4, topPos + 159, 0, 42, 71, 21);

        // v67.15: 任务栏选择框 — TLM TaskButton 槽位 (addTaskListButton: leftPos-89, topPos+23+19*i, 83x19, TASK u93 v28+20*i, 每页12)
        for (int i = 0; i < 12; i++) {
            g.blit(TLM_TASK, leftPos - 89, topPos + 23 + 19 * i, 93, 28 + 20 * i, 83, 19, 256, 256);
        }

        // 侧栏底部贴图 (drawSideTabGui: SIDE 235,107)
        g.blit(TLM_SIDE, leftPos + 251 + 5, topPos + 28 + 9, 235, 107, 21, 50);
    }

    /** 顶部 tab 框+图标 — MaidTabButton: 框 (x, y, left, 21, 24, 26) + 图标 (x+4, y+6, left, 47, 16, 16) */
    private static void drawTab(GuiGraphics g, int leftPos, int topPos, int left, int xOffset) {
        int x = leftPos + xOffset;
        int y = topPos + 5;
        g.blit(TLM_SIDE, x, y, left, 21, 24, 26, 256, 256);
        g.blit(TLM_SIDE, x + 4, y + 6, left, 47, 16, 16, 256, 256);
    }

    /** 右侧侧栏按钮框+图标 — MaidSideTabButton: 框 (x+2, y, 209, top, 26, 24) + 图标 (x+6, y+4, 193, top+4, 16, 16) */
    private static void drawSideTab(GuiGraphics g, int leftPos, int topPos, int index) {
        int top = 107 + index * 25;
        int x = leftPos + 251;
        int y = topPos + 37 + index * 25;
        g.blit(TLM_SIDE, x + 2, y, 209, top, 26, 24, 256, 256);
        g.blit(TLM_SIDE, x + 6, y + 4, 193, top + 4, 16, 16, 256, 256);
    }

    /** 状态条 — TLM drawBaseInfoGui: 图标(iconU) + 背景 + 填充条(barV) + 数字(0.5 缩放) */
    private static void drawBar(GuiGraphics g, int leftPos, Font font, int topY,
                                double ratio, int value, int iconU, int barV) {
        g.blit(TLM_SIDE, leftPos + 53, topY, iconU, 0, 9, 9);
        g.blit(TLM_SIDE, leftPos + 5, topY, 0, 9, 47, 9);
        double r = Math.max(0, Math.min(ratio, 1));
        g.blit(TLM_SIDE, leftPos + 7, topY + 2, 2, barV, (int) (43 * r), 5);
        drawNumberScale(g, font, value, leftPos + 63, topY + 1);
    }

    /** 数字 (0.5 缩放) — TLM drawNumberScale 复刻 */
    private static void drawNumberScale(GuiGraphics g, Font font, int value, int posX, int posY) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1);
        g.drawString(font, String.valueOf(value), posX * 2, posY * 2 + font.lineHeight / 2, 0x333333, false);
        g.pose().popPose();
    }
}

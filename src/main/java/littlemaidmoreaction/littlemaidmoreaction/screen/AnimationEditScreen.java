package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.core.model.LmaAnimationDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.LmaAnimationStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 动画参数编辑 — 单动画参数的独立编辑器。
 *
 * <p>从 ActionEditScreen 的 [编辑] 按钮打开。
 * 数据存储在 config/littlemaidmoreaction/animationsetup/<name>.json。
 * 不存在时自动创建默认 LmaAnimationDef。
 */
public final class AnimationEditScreen extends Screen {
    private final Screen parent;
    private final String animName;
    private LmaAnimationDef def;  // 工作副本

    private EditBox priorityInput;
    private CycleButton<Boolean> lockMoveBtn, freezeAIBtn, interruptBtn;

    private static final int CX = 180, FW = 150;

    public AnimationEditScreen(Screen parent, String animName) {
        super(Component.literal("编辑动画: " + animName));
        this.parent = parent;
        this.animName = animName;
        this.def = LmaAnimationStorage.get(animName)
                .orElse(LmaAnimationDef.fallback(animName));
    }

    @Override
    protected void init() {
        int cx = this.width / 2 - CX, y = 38;

        // ── 优先级 ──
        priorityInput = new EditBox(font, cx + 100, y, FW, 20,
                Component.literal("优先级"));
        priorityInput.setValue(String.valueOf(def.priority()));
        addRenderableWidget(priorityInput);
        y += 28;

        // ── 锁定移动 ──
        lockMoveBtn = CycleButton.booleanBuilder(
                Component.literal("是"), Component.literal("否"))
                .create(cx + 100, y, FW, 20, Component.literal("锁定移动"),
                        (b, v) -> def = def.withLockMove(v));
        lockMoveBtn.setValue(def.lockMovement());
        addRenderableWidget(lockMoveBtn);
        y += 28;

        // ── 冻结 AI ──
        freezeAIBtn = CycleButton.booleanBuilder(
                Component.literal("是"), Component.literal("否"))
                .create(cx + 100, y, FW, 20, Component.literal("冻结 AI"),
                        (b, v) -> def = def.withFreezeAI(v));
        freezeAIBtn.setValue(def.freezeAI());
        addRenderableWidget(freezeAIBtn);
        y += 28;

        // ── 可被打断 ──
        interruptBtn = CycleButton.booleanBuilder(
                Component.literal("是"), Component.literal("否"))
                .create(cx + 100, y, FW, 20, Component.literal("可被打断"),
                        (b, v) -> def = def.withInterruptible(v));
        interruptBtn.setValue(def.interruptible());
        addRenderableWidget(interruptBtn);
        y += 28;

        // ── 底部按钮 ──
        y = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .pos(cx, y).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .pos(cx + 90, y).size(80, 20).build());
    }

    private void save() {
        // 从控件读取
        try { def = def.withPriority(
                Integer.parseInt(priorityInput.getValue())); }
        catch (NumberFormatException ignored) {}

        // 钳制范围
        def = def.withPriority(Math.max(1, Math.min(999, def.priority())));

        // 确保 name 一致
        def = def.withName(animName);

        // 写入 config/littlemaidmoreaction/animationsetup/<name>.json
        LmaAnimationStorage.put(def);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, getTitle(), this.width / 2, 10, 0xFFD700);

        int cx = this.width / 2 - CX;
        int y = 42;

        // 标签 (动画名已在上方标题栏显示)
        g.drawString(font, "优先级:",   cx, y, 0xFFAAAAAA);
        g.drawString(font, "锁定移动:", cx, y + 28, 0xFFAAAAAA);
        g.drawString(font, "冻结 AI:",  cx, y + 56, 0xFFAAAAAA);
        g.drawString(font, "可被打断:", cx, y + 84, 0xFFAAAAAA);

        // 范围提示
        g.drawString(font, "(1~999)", cx + 100 + FW + 8, y + 2, 0xFF555555);

        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}

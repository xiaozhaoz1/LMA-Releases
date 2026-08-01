package littlemaidmoreaction.littlemaidmoreaction.screen;

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * v67.2: Cloth Config 自定义按钮条目 — 任务自定义入口。
 *
 * <p>cloth 11.1.136 无原生按钮条目; {@link TooltipListEntry} 无抽象方法 (javap 验证),
 * 继承并覆写 render/getValue 等最小集即可。左文字右按钮布局, 与 cloth 其他条目对齐。
 */
public final class ButtonEntry extends TooltipListEntry<Boolean> {

    private final Component fieldName;
    private final Button button;

    public ButtonEntry(Component fieldName, Component buttonLabel, Runnable onClick) {
        super(fieldName, () -> Optional.empty());
        this.fieldName = fieldName;
        this.button = Button.builder(buttonLabel, b -> onClick.run())
                .bounds(0, 0, 130, 20).build();
    }

    @Override
    public void render(GuiGraphics g, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(g, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
        if (fieldName != null) {
            g.drawString(Minecraft.getInstance().font, fieldName, x + 8, y + 7, 0xFFFFFF);
        }
        button.setX(x + entryWidth - 140);
        button.setY(y + 2);
        button.render(g, mouseX, mouseY, delta);
    }

    @Override public int getItemHeight() { return 24; }
    @Override public int getInitialReferenceOffset() { return 0; }
    @Override public Boolean getValue() { return true; }
    @Override public Optional<Boolean> getDefaultValue() { return Optional.of(true); }
    @Override public boolean isEdited() { return false; }
    @Override public void save() {}
    @Override public boolean isRequiresRestart() { return false; }
    @Override public void setRequiresRestart(boolean requiresRestart) {}
    @Override public Component getFieldName() { return fieldName; }

    @Override
    public List<? extends GuiEventListener> children() { return List.of(button); }

    @Override
    public List<? extends NarratableEntry> narratables() { return List.of(button); }
}

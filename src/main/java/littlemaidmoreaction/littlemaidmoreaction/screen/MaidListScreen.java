package littlemaidmoreaction.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** v34: 女仆列表 + 3D 预览 */
public final class MaidListScreen extends Screen {
    private static final int LIST_W = 180;
    private static final int PREVIEW_X_OFFSET = 200;
    private final Screen parent;
    private MaidList maidList;
    private EntityMaid selectedMaid;
    private float previewRotY, previewRotX;
    private int prevMouseX, prevMouseY;

    public MaidListScreen(Screen parent) {
        super(Component.translatable("gui.lma.maid_editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2, rightX = width - 60;
        maidList = new MaidList(minecraft, LIST_W, height, 24, height - 32, 20);
        maidList.setLeftPos(10);
        addRenderableWidget(maidList);

        addRenderableWidget(Button.builder(Component.translatable("gui.lma.edit"),
                b -> { if (selectedMaid != null) minecraft.setScreen(new MaidEditorScreen(this, selectedMaid)); })
                .pos(rightX - 80, height - 26).size(70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> minecraft.setScreen(parent))
                .pos(rightX, height - 26).size(50, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 4, 8, 0xFFFFFF);
        super.render(g, mx, my, pt);

        // 3D preview right side
        if (selectedMaid != null) {
            renderEntityPreview(g, selectedMaid, width - 120, height / 2 + 20, 50);
        }
    }

    private void renderEntityPreview(GuiGraphics g, Entity entity, int x, int y, int scale) {
        // 3D preview: render entity name as placeholder
        int w = 120, h = 180;
        int sx = x - w/2, sy = y - h/2;
        g.fill(sx, sy, sx + w, sy + h, 0x33_000000);
        g.renderOutline(sx, sy, w, h, 0xFF_888888);
        String name = entity.getName().getString();
        g.drawCenteredString(font, name, x, sy + h/2 - 10, 0xFFFFFF);
        g.drawCenteredString(font, "(3D preview)", x, sy + h/2 + 10, 0x888888);

        // rotate on drag
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.distanceToSqr(entity) < 256) {
            g.drawCenteredString(font, "距离: " + String.format("%.1f", Math.sqrt(mc.player.distanceToSqr(entity))) + "m",
                    x, sy + h - 16, 0x666666);
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (mx > width * 0.55) { // right side preview area
            previewRotY += dx * 0.01F;
            previewRotX += dy * 0.01F;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scroll) {
        // scroll in preview area = zoom? or just pass to list
        return super.mouseScrolled(mx, my, scroll);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    // -- inner list --
    private final class MaidList extends ObjectSelectionList<MaidList.Entry> {
        MaidList(Minecraft mc, int w, int h, int top, int bot, int itemH) {
            super(mc, w, h, top, bot, itemH);
            Player p = mc.player;
            if (p == null) return;
            var aabb = new net.minecraft.world.phys.AABB(p.blockPosition()).inflate(128);
            List<EntityMaid> maids = p.level().getEntitiesOfClass(EntityMaid.class,
                    aabb, m -> m.getOwnerUUID() != null && m.getOwnerUUID().equals(p.getUUID()));
            for (EntityMaid m : maids) {
                addEntry(new Entry(m));
            }
        }

        @Override
        public void setSelected(Entry entry) {
            super.setSelected(entry);
            selectedMaid = entry.maid;
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final EntityMaid maid;
            Entry(EntityMaid m) { this.maid = m; }

            @Override
            public Component getNarration() { return maid.getName(); }

            @Override
            public void render(GuiGraphics g, int idx, int top, int left, int w, int h,
                               int mx, int my, boolean hover, float pt) {
                String txt = maid.getName().getString()
                        + "  Lv." + maid.getFavorabilityManager().getLevel()
                        + "  " + (int) maid.getHealth() + "❤";
                g.drawString(Minecraft.getInstance().font, txt, left + 4, top + 4, 0xFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int btn) {
                maidList.setSelected(this);
                return true;
            }
        }
    }
}

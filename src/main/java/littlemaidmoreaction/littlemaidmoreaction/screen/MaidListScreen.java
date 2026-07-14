package littlemaidmoreaction.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.model.MaidModelGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Objects;

/** v34.1: 女仆列表 + 静态3D预览 */
public final class MaidListScreen extends Screen {
    private static final int LIST_W = 240;
    private final Screen parent;
    private MaidList maidList;
    private EntityMaid selectedMaid;
    private EntityMaid previewMaid;

    public MaidListScreen(Screen parent) {
        super(Component.literal("女仆编辑器"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        maidList = new MaidList(minecraft, LIST_W, height, 24, height - 32, 20);
        maidList.setLeftPos(10);
        addRenderableWidget(maidList);

        addRenderableWidget(Button.builder(Component.literal("编辑"),
                b -> { if (selectedMaid != null) minecraft.setScreen(new MaidEditorScreen(this, selectedMaid)); })
                .pos(LIST_W + 20, 30).size(50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"),
                b -> onClose())
                .pos(LIST_W + 80, 30).size(50, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, LIST_W / 2 + 10, 8, 0xFFFFFF);
        super.render(g, mx, my, pt);

        if (selectedMaid != null) {
            int px = LIST_W + 40;
            int py = height / 2 + 40;
            renderEntityPreview(g, selectedMaid, px, py);
        } else {
            g.drawCenteredString(font, "选择女仆查看预览", LIST_W + 80, height / 2, 0x666666);
        }
    }

    private void renderEntityPreview(GuiGraphics g, EntityMaid maid, int x, int y) {
        // 同步外观到预览实体
        if (previewMaid == null) {
            previewMaid = createPreviewMaid();
        }
        if (previewMaid != null) {
            previewMaid.setModelId(maid.getModelId());
            previewMaid.setYsmModel(maid.getYsmModelId(), maid.getYsmModelTexture(), maid.getYsmModelName());
            previewMaid.setIsYsmModel(maid.isYsmModel());
            // 静态渲染: mouseX=mouseY=0 → 不跟随鼠标
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, y, 35, 0, 0, previewMaid);
        }

        // 信息
        String info = maid.getName().getString()
                + "  Lv." + maid.getFavorabilityManager().getLevel()
                + "  " + (int) maid.getHealth() + "/" + (int) maid.getMaxHealth() + "❤";
        g.drawCenteredString(font, info, x, y + 110, 0xFFFFFF);
    }

    private EntityMaid createPreviewMaid() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        try {
            Entity e = EntityCacheUtil.ENTITY_CACHE.get(EntityMaid.TYPE,
                    () -> Objects.requireNonNullElseGet(EntityMaid.TYPE.create(mc.level), () -> new EntityMaid(mc.level)));
            EntityMaid m = (EntityMaid) e;
            m.setOnGround(true);
            m.setIsYsmModel(false);
            return m;
        } catch (Exception ex) { return null; }
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private final class MaidList extends ObjectSelectionList<MaidList.Entry> {
        MaidList(Minecraft mc, int w, int h, int top, int bot, int itemH) {
            super(mc, w, h, top, bot, itemH);
            Player p = mc.player;
            if (p == null) return;
            var aabb = new AABB(p.blockPosition()).inflate(128);
            List<EntityMaid> maids = p.level().getEntitiesOfClass(EntityMaid.class,
                    aabb, m -> m.getOwnerUUID() != null && m.getOwnerUUID().equals(p.getUUID()));
            for (EntityMaid m : maids) addEntry(new Entry(m));
        }

        @Override
        public void setSelected(Entry entry) {
            super.setSelected(entry);
            selectedMaid = entry.maid;
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final EntityMaid maid;
            Entry(EntityMaid m) { this.maid = m; }
            @Override public Component getNarration() { return maid.getName(); }

            @Override
            public void render(GuiGraphics g, int idx, int top, int left, int w, int h,
                               int mx, int my, boolean hover, float pt) {
                String txt = maid.getName().getString()
                        + "  Lv." + maid.getFavorabilityManager().getLevel()
                        + "  " + (int) maid.getHealth() + "❤";
                g.drawString(font, txt, left + 4, top + 4, 0xFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int btn) {
                maidList.setSelected(this);
                return true;
            }
        }
    }
}

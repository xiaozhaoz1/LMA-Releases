package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.FarmContainerBindPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 统一容器绑定菜单 (v79.62) — 木棍右键容器时打开, 4 角色选择:
 * 种子源 / 收获目标 (farm) + 取出源 / 放入目标 (arm_transfer).
 * 选择 → C2S {@link FarmContainerBindPacket} → 服务端写木棍 NBT → 右键女仆交付.
 */
public final class FarmContainerScreen extends Screen {

    private final BlockPos pos;

    public FarmContainerScreen(BlockPos pos) {
        super(Component.translatable("screen.littlemaidmoreaction.farm_container"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int w = 200, h = 20, gap = 6;
        // ★ v79.63.1 修回 (用户实机反馈: "我的四个按钮呢"): **四个角色按钮常驻** —
        //   "只列当前任务需要的"当初被实现成"隐藏其余按钮" ⇒ 大多数任务下菜单变成空屏 ✗
        //   现改为: 按钮全在 (坐标与历史版本完全一致), 需要的角色由 render 的**一行提示**指出 ✓
        int y = cy - 2 * (h + gap);
        addButton(cx - w / 2, y, "screen.littlemaidmoreaction.farm_container.seed", "seed");
        y += h + gap;
        addButton(cx - w / 2, y, "screen.littlemaidmoreaction.farm_container.harvest", "harvest");
        y += h + gap;
        addButton(cx - w / 2, y, "screen.littlemaidmoreaction.farm_container.take", "take");
        y += h + gap;
        addButton(cx - w / 2, y, "screen.littlemaidmoreaction.farm_container.deposit", "deposit");
        y += h + gap + 8;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(null))
                .bounds(cx - w / 2, y, w, h).build());
    }

    private void addButton(int x, int y, String key, String role) {
        this.addRenderableWidget(Button.builder(
                Component.translatable(key),
                btn -> {
                    FarmContainerBindPacket.sendToServer(role, pos);
                    Minecraft.getInstance().setScreen(null);
                })
                .bounds(x, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        g.drawCenteredString(font, Component.translatable("screen.littlemaidmoreaction.farm_container.title")
                .copy().append(" §7" + pos.toShortString()), this.width / 2, this.height / 2 - 70, 0xFFFFE8D9);
        // v79.63.1: "当前任务需要哪些角色"只做**提示**, 不隐藏按钮 (四按钮常驻 — 用户裁定)
        g.drawCenteredString(font, Component.literal("§7" + neededHint(MarkTarget.get())),
                this.width / 2, this.height / 2 - 68, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

//? if 1.20.1 {
    @Override
    public void renderBackground(GuiGraphics g) {
        PanoramaBackground.render(g);
    }
//?} else {
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        this.renderPanorama(g, pt);
    }
//?}

    /**
     * 任务 → 该任务需要的容器角色 (用户裁定 2026-09-13: 菜单只列需要的;
     * **输入箱/输出箱共用 take/deposit** — 挖空置域 take=放工具, deposit=放挖出的方块)。
     *
     * <p>返回空 = 该任务不需要容器标记 (不改菜单按钮, 由渲染层提示一行)。未知任务回退全 4 角色。
     */
    private static java.util.List<String> rolesFor(String task) {
        if (task == null || task.isEmpty()) return java.util.List.of("seed", "harvest", "take", "deposit");
        return switch (task) {
            case "arm_transfer" -> java.util.List.of("take", "deposit");
            case "void_excavation" -> java.util.List.of("take", "deposit");   // 输入箱(工具) / 输出箱(方块)
            case "dam_fill" -> java.util.List.of("take");                     // 输入箱(沙)
            case "farm" -> java.util.List.of("seed", "harvest");
            default -> java.util.List.of();                                   // 不需要容器标记
        };
    }

    /**
     * **当前任务需要哪些角色** — 只作为一行**提示**显示 (不隐藏任何按钮, 用户裁定 2026-09-13)。
     * 角色名与 FarmContainerBindPacket.roleName 的服务端回执一致。
     */
    private static String neededHint(String task) {
        if (task == null || task.isEmpty()) return "先右键女仆选定交付对象 (四个按钮都可自由标记)";
        java.util.List<String> roles = rolesFor(task);
        if (roles.isEmpty()) return "当前任务 (" + task + ") 不需要容器标记 — 按钮仍可用作备用标记";
        StringBuilder sb = new StringBuilder("当前任务 (" + task + ") 需要: ");
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(switch (roles.get(i)) {
                case "seed" -> "种子源箱";
                case "harvest" -> "收获目标箱";
                case "take" -> "取出源箱";
                case "deposit" -> "放入目标箱";
                default -> roles.get(i);
            });
        }
        return sb.toString();
    }
}

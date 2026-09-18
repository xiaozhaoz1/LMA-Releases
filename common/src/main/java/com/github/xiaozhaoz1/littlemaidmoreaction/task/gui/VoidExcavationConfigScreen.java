package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.VoidExcavationPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * v79.62.1 挖空置域配置屏幕 — 自绘销毁名单框 (只要销毁名单不要白名单, 空置域专名 destroy_list)
 * + 关闭寻路 per-maid toggle (per-maid 优先, 回退全局 Cloth)。
 *
 * <p>销毁名单存 pipelineConfig "destroy_list" (ACTION_SET_LIST); 名单内物品挖出即销毁消失。
 * 关寻路 "no_pathfind" 经 ACTION_TOGGLE, per-maid 优先回退全局 VOID_NO_PATHFIND。
 * 区域区块数 (size) v79.62.1 裁定不再在此屏编辑 — 只读显示, 走全局 VOID_DEFAULT_CHUNKS。
 */

public class VoidExcavationConfigScreen extends LmaTaskConfigScreen<VoidExcavationConfigMenu> {

    public VoidExcavationConfigScreen(VoidExcavationConfigMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected String getTaskType() {
        return "void_excavation";
    }

    /** 销毁名单输入框 (物品 id 逗号分隔) */
    private net.minecraft.client.gui.components.EditBox destroyListBox;
    /** 关闭寻路按钮 (renderAddition 按当前配置同步文案) */
    private net.minecraft.client.gui.components.Button noPathfindBtn;
    /** v79.62.2 自定义最低高度输入框 (空 = 自动检测基岩层) */
    private net.minecraft.client.gui.components.EditBox minYBox;
    /** 最低高度是否已预填 */
    private boolean minYSynced;
    /** 销毁名单是否已从配置预填到输入框 (ReplyTaskConfigPacket 到达后一次) */
    private boolean listSynced;

    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int rowW = contentRight() - contentX();   // v79.63.22 (用户): 控件占满整行 ✓
        int rowX = contentX();

        // v79.62.1 用户裁定: 删 size 步进 + 删销毁开关 → 销毁名单输入 + 关闭寻路切换.
        // 布局 (TLM 单女仆界面窄, 控件宽度 ≤100 防超界):
        //  行1: 销毁名单输入框; 行2: 保存名单; 行3: 关闭寻路.
        // 销毁名单输入框 (物品 id, 逗号分隔; 名单内物品挖出即销毁消失)
        destroyListBox = tipBox(rowX, rowY(0) - 2, rowW,
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.void.destroy_list"),
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.void.destroy_list.tip"));
        destroyListBox.setMaxLength(256);
        addRenderableWidget(destroyListBox);

        // 保存名单 — 下一栏 (不与输入框并排, 防超界; 空值=清空名单, 回退全局)
        addRenderableWidget(Button.builder(Component.literal("保存名单"),
                btn -> {
                    String val = destroyListBox.getValue() == null ? "" : destroyListBox.getValue();
                    sendSetList(VoidExcavationPipeline.KEY_DESTROY_LIST, val);   // 基类助手 (README §二 契约) ✓
                    if (m != null) {
                        // 本地同步为 ListTag (与服务端 ACTION_SET_LIST 一致)
                        getMenu().getConfig().put(VoidExcavationPipeline.KEY_DESTROY_LIST, csvToList(val));
                    }
                }).pos(rowX, rowY(1) - 2).size(rowW, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.void.save_list.tip"))).build());

        // v79.62.2 导航开关 — 下一栏 (noPathfind=true=关闭导航=传送区块中间开挖; false=开启导航=TLM 走)
        boolean npCur = getMenu().getConfig().getBoolean(VoidExcavationPipeline.KEY_NO_PATHFIND);
        noPathfindBtn = addRenderableWidget(Button.builder(
                Component.literal(npCur ? "关闭导航" : "开启导航"),
                btn -> {
                    sendToggle(VoidExcavationPipeline.KEY_NO_PATHFIND);   // 基类助手 ✓
                    boolean cur = getMenu().getConfig().getBoolean(VoidExcavationPipeline.KEY_NO_PATHFIND);
                    getMenu().getConfig().putBoolean(VoidExcavationPipeline.KEY_NO_PATHFIND, !cur);
                    btn.setMessage(Component.literal(!cur ? "关闭导航" : "开启导航"));
                }).pos(rowX, rowY(2) - 2).size(rowW, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.void.nav.tip"))).build());

        // v79.62.2 自定义最低高度 — 下一栏 (空 = 自动检测基岩层; 设定 = 挖到该层停)
        minYBox = tipBox(rowX, rowY(3) - 2, rowW,
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.void.min_y"),
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.void.min_y.tip"));
        minYBox.setMaxLength(6);
        addRenderableWidget(minYBox);
        // 保存最低高度
        addRenderableWidget(Button.builder(Component.literal("保存高度"),
                btn -> {
                    String val = minYBox.getValue() == null ? "" : minYBox.getValue().trim();
                    if (val.isEmpty()) {
                        // 空 = 移除键 ⇒ 回落"自动检测基岩层" (README §二: 用 ACTION_REMOVE 而非写默认值 ✓)
                        sendRemove(VoidExcavationPipeline.KEY_MIN_Y);
                        if (m != null) getMenu().getConfig().remove(VoidExcavationPipeline.KEY_MIN_Y);
                    } else {
                        try {
                            int yv = Integer.parseInt(val);
                            // ★ v79.66.1 校验 (与管线同口径): min_y 必须 < 标记层 start.y — 否则"无可挖层" ⇒
                            //   管线首 tick 就会把区块判"已挖完"(旧实现还会删 start) ✗ 故在此直接拒绝保存 + 提示 ✓
                            net.minecraft.core.BlockPos startPos = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs
                                    .readBlockPos(getMenu().getConfig(),
                                            com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.CFG_START);
                            if (startPos != null && yv >= startPos.getY()) {
                                var pl = net.minecraft.client.Minecraft.getInstance().player;
                                if (pl != null) {
                                    pl.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                            "screen.littlemaidmoreaction.void.min_y.invalid", yv, startPos.getY()), false);
                                }
                                return;   // 不保存
                            }
                            // ★ v79.64.4 修「保存后 Y 变 0」(用户实测): 原**绕过基类助手**手写 payload —
                            //   用 {@code putString("value", val)} 配 ACTION_SET_INT, 而服务端是
                            //   {@code cfg.putInt(key, payload.getInt("value"))} ⇒ 对 StringTag 取 int = **0** ✗
                            //   (min_y=0 会被管线当作"挖到 y=0"= 世界底部, 覆盖自动检测) ⇒ 必须走 sendSetInt ✓
                            sendSetInt(VoidExcavationPipeline.KEY_MIN_Y, yv);
                            if (m != null) getMenu().getConfig().putInt(VoidExcavationPipeline.KEY_MIN_Y, yv);
                        } catch (NumberFormatException ex) { /* 非法数字忽略 */ }
                    }
                }).pos(rowX, rowY(4) - 2).size(rowW, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.void.save_min_y.tip"))).build());
    }

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        // v79.63.10: 与管线同源 — 只读全局 (忽略旧版残留的 per-maid size ✗, 用户实测显示与实挖不符)
        // v79.63.16: 显示与管线同链条 (单女仆 → 全局 ✓); 用户可在下方输入框改/清 ✓
        int size = cfg.contains(VoidExcavationPipeline.KEY_SIZE)
                ? cfg.getInt(VoidExcavationPipeline.KEY_SIZE)
                : ActiveTaskConfig.VOID_DEFAULT_CHUNKS.get();
        // v79.62.2 文字放「关闭寻路开关下面」— 关寻路按钮 pos(cx, contentY+48) 高 20, 底 = topPos+102;
        // 原 topPos+112 与 TLM 任务栏 3D 预览重合 (用户实测), 上移紧贴按钮下方.
        String text = "区域 (区块): " + size + "×" + size;
        g.drawString(font, Component.literal(text), leftPos + 8, topPos + 106, 0xFFFFFF);
        // 销毁名单提示 (在"区域"下方, 不重叠)
        String dl = listToCsv(cfg, VoidExcavationPipeline.KEY_DESTROY_LIST);
        if (!dl.isEmpty()) {
            g.drawString(font, Component.literal("销毁名单: " + dl), leftPos + 8, topPos + 118, 0xFFFF55);
        }
        // 配置到达后一次性预填销毁名单输入框 (ReplyTaskConfigPacket 到达前为空; 空名单不预填)
        if (!listSynced && !cfg.isEmpty()) {
            if (destroyListBox != null) destroyListBox.setValue(listToCsv(cfg, VoidExcavationPipeline.KEY_DESTROY_LIST));
            listSynced = true;
        }
        // toggle 文案按当前配置同步 (ReplyTaskConfigPacket 到达前初始为默认, 到达后自纠)
        if (noPathfindBtn != null) {
            boolean n = cfg.getBoolean(VoidExcavationPipeline.KEY_NO_PATHFIND);
            String label = n ? "关闭导航" : "开启导航";
            if (!noPathfindBtn.getMessage().getString().equals(label)) noPathfindBtn.setMessage(Component.literal(label));
        }
        // v79.62.2 最低高度预填 (配置到达后一次)
        if (!minYSynced && !cfg.isEmpty()) {
            if (minYBox != null) {
                minYBox.setValue(cfg.contains(VoidExcavationPipeline.KEY_MIN_Y)
                        ? String.valueOf(cfg.getInt(VoidExcavationPipeline.KEY_MIN_Y)) : "");
            }
            minYSynced = true;
        }
    }

    /** 读名单 (ListTag; 兼容旧 String) → 逗号分隔字符串 */
    private static String listToCsv(CompoundTag cfg, String key) {
        net.minecraft.nbt.Tag t = cfg.get(key);
        if (t instanceof net.minecraft.nbt.ListTag lt) {
            StringBuilder sb = new StringBuilder();
            for (net.minecraft.nbt.Tag e : lt) {
                if (sb.length() > 0) sb.append(",");
                sb.append(e.getAsString());
            }
            return sb.toString();
        }
        return cfg.getString(key);
    }

    /** 逗号分隔 → ListTag (与服务端 ACTION_SET_LIST 解析一致) */
    private static net.minecraft.nbt.ListTag csvToList(String val) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        if (val != null) {
            for (String s : val.split(",")) {
                s = s.trim();
                if (!s.isEmpty()) list.add(net.minecraft.nbt.StringTag.valueOf(s));
            }
        }
        return list;
    }

}

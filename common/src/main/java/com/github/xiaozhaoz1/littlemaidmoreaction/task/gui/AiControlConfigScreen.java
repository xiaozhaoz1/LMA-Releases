package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * AI 操控配置屏幕 (v74) — LLM 模型/声线名称 (Numen G 面板创建的条目名)。
 *
 * <p>值存 maid pipelineConfig (lma_cfg_ai_control): "llm_provider"/"voice",
 * 引擎通用动作 ACTION_SET_STRING 写入; 空串 = 回退全局默认
 * ({@link ActiveTaskConfig#AI_LLM_PROVIDER} / {@link ActiveTaskConfig#AI_VOICE})。
 */
public class AiControlConfigScreen extends LmaTaskConfigScreen<AiControlConfigMenu> {

    private EditBox providerBox;
    private EditBox voiceBox;

    public AiControlConfigScreen(AiControlConfigMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected String getTaskType() {
        return "ai_control";
    }

    // ── initAdditionWidgets: 请求配置 + 文本框 + 保存按钮 (TLM 标准钩子; renderBg 基类默认委托) ──

    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int cx = contentX();
        int y = contentY();

        providerBox = new EditBox(font, cx, y, 120, 18, Component.literal("LLM 模型名称"));
        providerBox.setMaxLength(64);
        providerBox.setValue(getMenu().getConfig().getString(AiControlPipeline.KEY_PROVIDER));
        addRenderableWidget(providerBox);
        y += 22;

        voiceBox = new EditBox(font, cx, y, 120, 18, Component.literal("声线名称"));
        voiceBox.setMaxLength(64);
        voiceBox.setValue(getMenu().getConfig().getString(AiControlPipeline.KEY_VOICE));
        addRenderableWidget(voiceBox);
        y += 26;

        addRenderableWidget(Button.builder(Component.literal("保存设置"), b -> save())
                .pos(cx, y).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复全局默认"), b -> resetToGlobal())
                .pos(cx + 84, y).size(90, 20).build());
        y += 24;

        // 变成假人 — 任务运行中点击 → 生成假人 + 自动设模型/同步状态 + 女仆收石板
        addRenderableWidget(Button.builder(Component.literal("变成假人 (AI 操控)"), b -> transformToFake())
                .pos(cx, y).size(120, 20).build());
    }

    // ── renderAddition: 当前生效值 (per-maid 或全局回退) ──

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        int cx = contentX();
        int y = topPos + 136;   // 下移避开按钮区 (编辑框 34/56, 按钮 80/104)
        String p = cfg.getString(AiControlPipeline.KEY_PROVIDER);
        if (p.isEmpty()) p = ActiveTaskConfig.AI_LLM_PROVIDER.get();
        String v = cfg.getString(AiControlPipeline.KEY_VOICE);
        if (v.isEmpty()) v = ActiveTaskConfig.AI_VOICE.get();
        g.drawString(font, Component.literal("模型: " + (p.isEmpty() ? "(未设置)" : p)), cx, y, 0xFFFFFF);
        y += 14;
        g.drawString(font, Component.literal("声线: " + (v.isEmpty() ? "(未设置)" : v)), cx, y, 0xFFFFFF);
        y += 14;
        g.drawString(font, Component.literal("名称 = Numen 面板 (G键) 创建的模型/声线条目名, 空 = 全局默认"), cx, y, 0xAAAAAA);
        y += 16;
        g.drawString(font, Component.literal("变成假人: 女仆收石板, 假人自动继承模型/血量"), cx, y, 0xFFD700);
    }

    // ── 业务 ──

    private void save() {
        EntityMaid m = getMaid();
        if (m == null) return;
        sendString(m, AiControlPipeline.KEY_PROVIDER, providerBox.getValue().trim());
        sendString(m, AiControlPipeline.KEY_VOICE, voiceBox.getValue().trim());
    }

    private void resetToGlobal() {
        providerBox.setValue(ActiveTaskConfig.AI_LLM_PROVIDER.get());
        voiceBox.setValue(ActiveTaskConfig.AI_VOICE.get());
        save();
    }

    private void sendString(EntityMaid m, String key, String value) {
        CompoundTag cfg = getMenu().getConfig();
        cfg.putString(key, value);
        sendSetString(key, value);
    }

    /** 变成假人按钮 — 服务端校验任务运行中 (gate on) 后执行变身 */
    private void transformToFake() {
        sendAction(AiControlPipeline.ACTION_TRANSFORM, new CompoundTag());
    }
}

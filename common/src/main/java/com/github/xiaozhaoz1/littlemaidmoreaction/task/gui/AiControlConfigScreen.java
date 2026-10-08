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

    /** 布局 (规范 v79.63.8): 行0 模型名 · 行1 声线 · 行2 保存/恢复 · 行3 变假人 (各 26t 间距, 控件 20 高) */
    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int labelX = contentX();
        int rowW = contentRight() - contentX();   // v79.63.22 (用户): 控件占满整行 ✓
        int rowX = contentX();

        providerBox = tipBox(rowX, rowY(0) - 2, rowW,
                Component.translatable("screen.littlemaidmoreaction.ai.provider"),
                Component.translatable("screen.littlemaidmoreaction.ai.provider.tip"));
        providerBox.setMaxLength(64);
        providerBox.setValue(getMenu().getConfig().getString(AiControlPipeline.KEY_PROVIDER));
        addRenderableWidget(providerBox);

        voiceBox = tipBox(rowX, rowY(1) - 2, rowW,
                Component.translatable("screen.littlemaidmoreaction.ai.voice"),
                Component.translatable("screen.littlemaidmoreaction.ai.voice.tip"));
        voiceBox.setMaxLength(64);
        voiceBox.setValue(getMenu().getConfig().getString(AiControlPipeline.KEY_VOICE));
        addRenderableWidget(voiceBox);

        int half = rowW / 2;
        addRenderableWidget(tipBtn(Component.translatable("screen.littlemaidmoreaction.ai.save"),
                rowX, rowY(2) - 2, half - 4, this::save,
                Component.translatable("screen.littlemaidmoreaction.ai.save.tip")));
        addRenderableWidget(tipBtn(Component.translatable("screen.littlemaidmoreaction.ai.reset"),
                rowX + half, rowY(2) - 2, rowW - half, this::resetToGlobal,
                Component.translatable("screen.littlemaidmoreaction.ai.reset.tip")));

        addRenderableWidget(tipBtn(Component.translatable("screen.littlemaidmoreaction.ai.transform"),
                rowX, rowY(3) - 2, rowW, this::transformToFake,
                Component.translatable("screen.littlemaidmoreaction.ai.transform.tip")));
    }

    /** 当前值以标签位显示 (左列, 行0/行1) */
    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        String pv = cfg.getString(AiControlPipeline.KEY_PROVIDER);
        if (pv.isEmpty()) pv = ActiveTaskConfig.AI_LLM_PROVIDER.get();
        String vv = cfg.getString(AiControlPipeline.KEY_VOICE);
        if (vv.isEmpty()) vv = ActiveTaskConfig.AI_VOICE.get();
        boolean perMaid = cfg.contains(AiControlPipeline.KEY_PROVIDER) || cfg.contains(AiControlPipeline.KEY_VOICE);
        drawLabel(g, Component.translatable("screen.littlemaidmoreaction.ai.provider_label",
                pv.isEmpty() ? "—" : pv, Component.translatable(perMaid
                        ? "screen.littlemaidmoreaction.scope.per_maid"
                        : "screen.littlemaidmoreaction.scope.global")), 0);
        drawLabel(g, Component.translatable("screen.littlemaidmoreaction.ai.voice_label",
                vv.isEmpty() ? "—" : vv), 1);
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

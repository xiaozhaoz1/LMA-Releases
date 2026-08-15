package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import net.minecraft.core.BlockPos;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;

/**
 * AI 操控任务 (v73) — 权限开关型主动任务。
 *
 * <p>玩家在 TLM 任务栏选择 "AI 操控" → 首次 tick 写 {@link AiControlGate} 标记
 * (女仆 AI 对话获得世界操作权限: 移动/挖掘/战斗/切任务等工具暴露);
 * 取消任务 → onCleanup 删标记 (闭环, 权限收回)。
 *
 * <p>无实质动作 — 开关语义由任务生命周期表达 (v74.1 修正:
 * isLongRunning = 启用超时看门狗 + GMPM 心跳续命 (GMPM L101-103/L133 两者均仅
 * isLongRunning 生效 — 2026-08-11c 统一口径: 非长任务自终结无兜底);
 * 无目标任务永不走 Brain 执行路径, 必须由 GMPM 心跳分支续命)。
 * v79.45: 修正 v79.32 回归 — enable 原在 execute (被 Brain doExecute needsGameTick 挡,
 * 生产环境从不执行) → 移入 tick (GMPM 每 tick 驱动, 幂等写)。
 * v75: 开启 = 假人桥自动收起女仆进石板 (NumenMaidBridge.start → shelveMaid:
 * 搬空物品给假人 + cancel 本任务 → 石板存 idle, 防放女仆恢复 gate 循环)。
 * v75.1: 变身改由设置 GUI "变成假人" 按钮触发 (ACTION_TRANSFORM → TRANSFORM_ACTIVATOR 委托)。
 *
 * <p>v74: 任务设置 GUI — LLM 模型/声线名称 ({@link #KEY_PROVIDER}/{@link #KEY_VOICE},
 * 存 pipelineConfig lma_cfg_ai_control, 供 Numen 假人桥读取广播绑定)。
 */
public final class AiControlPipeline implements TaskPipeline, TaskConfigurable {

    /** pipelineConfig (lma_cfg_ai_control) 键: LLM 模型名称 (Numen G 面板条目名, 空=全局默认) */
    public static final String KEY_PROVIDER = "llm_provider";
    /** pipelineConfig 键: 声线名称 (空=全局默认) */
    public static final String KEY_VOICE = "voice";

    /** 设置 GUI "变成假人" 按钮 → 自定义配置动作 */
    public static final byte ACTION_TRANSFORM = 16;
    /** 变身委托 (neoforge 侧注册: NumenMaidBridge.transform; Numen 未装为 null) — common 不引平台代码 */
    public static volatile java.util.function.Consumer<EntityMaid> TRANSFORM_ACTIVATOR;

    @Override public String taskType() { return "ai_control"; }

    @Override public boolean isLongRunning() { return true; }

    /** 首次 tick 写权限标记 (幂等 — 键重复写无害; 原 execute 路径被双驱动补丁挡 = 回归) */
    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        AiControlGate.enable(maid);
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        // 注册级门控 (TaskRegistry — 未装 Numen 不注册), validate 冗余防御
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.compat.NumenCompat.isInstalled()) {
            return PipelineResult.failed("AI 操控需要 Numen (AI 对话来源) — 请安装 Numen");
        }
        return PipelineResult.ok("AI 操控已就绪 — 开启后女仆 AI 对话可指挥世界操作");
    }

    @Override
    public void onCleanup(EntityMaid maid) {
        AiControlGate.disable(maid);   // 任务取消 → 权限收回 (键删除闭环)
        clearPipelineData(maid);
    }

    /** 设置 GUI "变成假人" 动作 (任务须运行中 — gate on 由桥侧校验) */
    @Override
    public boolean handleConfigAction(EntityMaid maid, byte action, CompoundTag payload) {
        if (action == ACTION_TRANSFORM) {
            if (TRANSFORM_ACTIVATOR != null) {
                TRANSFORM_ACTIVATOR.accept(maid);
            } else {
                // 前置提示 — Numen (假人) / YSM (OpenYSM 或混淆版均可 — 混淆版走命令通道)
                String missing;
                if (!com.github.xiaozhaoz1.littlemaidmoreaction.compat.NumenCompat.isInstalled()) {
                    missing = "Numen";
                } else if (!com.github.xiaozhaoz1.littlemaidmoreaction.compat.YsmCompat.isInstalled()) {
                    missing = "YSM / OpenYSM";
                } else {
                    missing = "Numen/YSM";
                }
                MaidChatBubbleApi.showFail(maid, "缺少前置: " + missing + " — 无法变身");
            }
            return true;
        }
        // 委托默认实现 (原复制 switch — CraftChain 同款正确做法)
        return TaskConfigurable.super.handleConfigAction(maid, action, payload);
    }

    /** 任务设置 GUI — LLM 模型/声线名称 (TLM 任务设置标签页入口) */
    @Override
    public MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.createMenuProvider(maid, Component.literal("AI 操控设置"),
                (cid, inv, maidId) -> new AiControlConfigMenu(cid, inv, maidId));
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.NumenCompat;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool.*;

/**
 * LMA AI 工具注册 (v73) — 经 TLM 官方扩展点 {@code ILittleMaid.registerAITool}
 * (ToolRegister.init 构造期遍历 EXTENSIONS, 注册表随后冻结)。
 *
 * <p>10 个世界操作工具: TLM AI 环驱动 (对话/配置/key 全复用), 实现委托 LMA IO 原语
 * + fakeplayer; 世界操作工具经 {@link AiControlGate} 门控 (ai_control 任务开启才暴露)。
 * 工具设计参考 Numen 28 工具模式 (已吸收, 见 MIGRATION-STATE v73)。
 */
public final class AiToolRegistration {

    public static void registerAll(ToolRegister register) {
        register.register(new MoveToTool());
        register.register(new MineBlockTool());
        register.register(new CollectItemsTool());
        register.register(new InteractBlockTool());
        register.register(new InteractEntityTool());
        register.register(new MeleeAttackTool());
        register.register(new GetSelfStatusTool());
        register.register(new SwitchLmaTaskTool());
        register.register(new ScanBlocksTool());
        register.register(new WaitTicksTool());
        // Numen 感知工具 (语义网格/实体清单/世界状态/蓝图)
        register.register(new LookAroundTool());
        register.register(new ScanNearbyEntitiesTool());
        register.register(new GetWorldInfoTool());
        register.register(new ReadBlueprintTool());
        // Numen 共存声明 (检测 + 日志; 无运行时依赖)
        NumenCompat.isInstalled();
    }

    private AiToolRegistration() {}
}

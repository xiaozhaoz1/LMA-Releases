package littlemaidmoreaction.littlemaidmoreaction.compat.kubejs;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;

/**
 * KubeJS 兼容插件 — 仿 TLM {@code RegisterKubeJSEvent} 模式 (v64).
 *
 * <h3>实现计划</h3>
 * <ol>
 *   <li>注册 LMA 规则触发/完成/失败事件到 KubeJS EventBus</li>
 *   <li>提供 LMA 绑定 (LMA.playAnim, LMA.startTask 等 JS API)</li>
 *   <li>LMA 规则可通过 KubeJS 脚本动态创建/修改</li>
 * </ol>
 *
 * <h3>注册方式</h3>
 * 在 {@code kubejs.plugins.txt} 添加:
 * <pre>
 * littlemaidmoreaction.littlemaidmoreaction.compat.kubejs.LmaKubeJSPlugin
 * </pre>
 *
 * <p>当前为骨架实现 — 待后续阶段填充具体事件和绑定。
 */
public class LmaKubeJSPlugin {
    // KubeJS 通过 kubejs.plugins.txt 发现此类
    // 需实现 dev.latvian.mods.kubejs.KubeJSPlugin

    private static final boolean ENABLED = false;

    static {
        if (ENABLED) {
            LittleMaidMoreAction.LOGGER.info("[LMA] KubeJS Plugin loaded");
        }
    }
}

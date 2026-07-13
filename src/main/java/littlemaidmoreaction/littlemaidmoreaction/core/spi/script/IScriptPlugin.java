package littlemaidmoreaction.littlemaidmoreaction.core.spi.script;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;

import java.util.Map;

/**
 * 脚本引擎插件 SPI 接口 — 支持多语言脚本引擎的热插拔。
 *
 * <p>脚本插件通过 {@code @ScriptPlugin} 注解标记，由 ClassScanner 在编译期扫描发现，
 * {@link ScriptPluginRegistry} 记录 class 名后在运行时懒加载。</p>
 *
 * <p>每个插件实例对应一种脚本语言（JavaScript、Lua、Python 等），
 * 提供统一的执行入口和生命周期管理。</p>
 *
 * @see ScriptPluginRegistry
 * @see littlemaidmoreaction.littlemaidmoreaction.core.annotation.ScriptPlugin
 */
public interface IScriptPlugin {

    /** 引擎名称，如 "js"、"lua"、"python"。用于标识和日志。 */
    String engineName();

    /** 文件扩展名，如 ".js"、".lua"、".py"。用于脚本类型检测。 */
    String fileExtension();

    /** 初始化脚本引擎（创建 GraalVM Context 或 ScriptEngine 实例）。 */
    void initialize();

    /**
     * 执行脚本。
     *
     * <p>返回值用于流程控制：
     * <ul>
     *   <li>{@code "true"} / {@code "false"} — 布尔结果，供条件求值使用</li>
     *   <li>{@code "skip:N"} — 跳过后续 N 步规则</li>
     *   <li>{@code "cancel"} — 取消当前事件</li>
     *   <li>其他字符串 — 存储在 RuleContext 中供 {@code $script_result} 引用</li>
     * </ul>
     * </p>
     *
     * @param script 脚本源代码
     * @param ctx    规则执行上下文
     * @param params 执行参数（原始字符串映射）
     * @return 流程控制结果字符串
     */
    String execute(String script, RuleContext ctx, Map<String, String> params);

    /** 脚本引擎是否可用（依赖库存在、初始化成功等）。 */
    boolean isAvailable();
}

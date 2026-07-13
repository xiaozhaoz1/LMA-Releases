package littlemaidmoreaction.littlemaidmoreaction.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 脚本插件注解 — 标记 {@code IScriptPlugin} 实现类以启用脚本引擎自动发现。
 *
 * <p>运行时由 {@code ClassScanner} 扫描，被此注解标记的类会被收集到
 * {@code ScriptEngineManager} 中进行注册，使脚本引擎名称与此注解的
 * {@link #engine()} 值匹配的规则引擎能够加载对应的脚本插件。</p>
 *
 * <p>{@link #engine()} 属性对应 {@code IScriptPlugin.engineName()}，
 * 用于指定该插件所属的脚本引擎名称（如 {@code "js"}、{@code "nashorn"}）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @ScriptPlugin(engine = "js")
 * public class JsScriptPlugin implements IScriptPlugin {
 *     // ...
 * }
 * }</pre>
 *
 * <p>被标记的类<b>必须</b>实现 {@code IScriptPlugin} 接口，并拥有无参构造函数
 * （ClassScanner 通过 {@code Class.forName()} 反射创建实例）。</p>
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface ScriptPlugin {

    /**
     * @return 脚本引擎名称，与 {@code IScriptPlugin.engineName()} 对应。
     * 默认值为 {@code "js"}（Nashorn / GraalVM JavaScript 引擎）。
     */
    String engine() default "js";
}

package littlemaidmoreaction.littlemaidmoreaction.core.annotation;

import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 动作注册注解 — 标记 {@link IAction} 实现类以启用类路径自动发现。
 *
 * <p>运行时由 {@code ClassScanner} 扫描，被此注解标记的类会被实例化
 * 并自动调用 {@link ActionRegistry#register(IAction)} 完成注册，
 * 替代旧架构中手动在 {@code static {}} 块中调用的方式。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @RuleAction
 * public class DealDamageAction implements IAction {
 *     // ...
 * }
 * }</pre>
 *
 * <p>被标记的类<b>必须</b>实现 {@link IAction} 接口，并拥有无参构造函数
 * （ClassScanner 通过 {@code Class.forName()} 反射创建实例）。</p>
 *
 * @see IAction
 * @see ActionRegistry
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface RuleAction {
}

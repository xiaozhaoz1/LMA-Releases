package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam.TypedParamVisitor;

/**
 * 参数控件工厂 — 访问者模式实现。
 *
 * <p>实现 TypedParamVisitor，编译器强制覆盖所有 5 种子类型。
 * 新增 TypedParam 子类型时，此文件编译报错 → 强制开发者更新。
 *
 * <p>位于 adapter/gui 模块，core 模块无 Minecraft GUI 依赖。
 * GUI 控件由编辑器屏幕（RuleEditScreen 等）根据返回的控件类型进一步配置。
 */
public final class ParamWidgetFactory implements TypedParamVisitor<ParamWidgetFactory.ParamWidget> {

    /** 参数控件封装 — 编辑器读取此信息动态生成输入控件 */
    public record ParamWidget(
        String paramName,             // 参数键名
        String displayName,           // GUI 显示名
        WidgetType widgetType,        // 控件类型
        String defaultValue,          // 默认值（字符串形式）
        String currentValue,          // 当前值
        java.util.List<String> options  // 仅 SelectParam 使用，其他为 null
    ) {}

    /** GUI 控件类型枚举 */
    public enum WidgetType {
        INT_INPUT,          // 整数输入框
        DOUBLE_INPUT,       // 浮点输入框
        TOGGLE,             // 布尔开关
        TEXT_INPUT,         // 文本输入框
        DROPDOWN            // 下拉选择框
    }

    private final String currentValue;

    /** @param currentValue 当前已保存的值（编辑现有规则时提供） */
    public ParamWidgetFactory(String currentValue) {
        this.currentValue = currentValue != null ? currentValue : "";
    }

    @Override
    public ParamWidget visit(TypedParam.IntParam p) {
        String val = !currentValue.isEmpty() ? currentValue : String.valueOf(p.defaultValue());
        return new ParamWidget(p.name(), p.displayName(), WidgetType.INT_INPUT,
            String.valueOf(p.defaultValue()), val, null);
    }

    @Override
    public ParamWidget visit(TypedParam.DoubleParam p) {
        String val = !currentValue.isEmpty() ? currentValue : String.valueOf(p.defaultValue());
        return new ParamWidget(p.name(), p.displayName(), WidgetType.DOUBLE_INPUT,
            String.valueOf(p.defaultValue()), val, null);
    }

    @Override
    public ParamWidget visit(TypedParam.BoolParam p) {
        String val = !currentValue.isEmpty() ? currentValue : String.valueOf(p.defaultValue());
        return new ParamWidget(p.name(), p.displayName(), WidgetType.TOGGLE,
            String.valueOf(p.defaultValue()), val, null);
    }

    @Override
    public ParamWidget visit(TypedParam.StringParam p) {
        String val = !currentValue.isEmpty() ? currentValue : p.defaultValue();
        return new ParamWidget(p.name(), p.displayName(), WidgetType.TEXT_INPUT,
            p.defaultValue(), val, null);
    }

    @Override
    public ParamWidget visit(TypedParam.SelectParam p) {
        String val = !currentValue.isEmpty() ? currentValue : p.defaultValue();
        return new ParamWidget(p.name(), p.displayName(), WidgetType.DROPDOWN,
            p.defaultValue(), val, p.options());
    }

    // ——— 便捷静态方法 ———

    /**
     * 为给定参数创建控件元数据。
     *
     * @param param        参数定义
     * @param currentValue 当前已保存的值（可为空字符串）
     * @return 控件元数据，编辑器据此渲染输入控件
     */
    public static ParamWidget create(TypedParam<?> param, String currentValue) {
        return param.accept(new ParamWidgetFactory(currentValue));
    }
}

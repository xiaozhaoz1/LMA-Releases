package littlemaidmoreaction.littlemaidmoreaction.core.spi.param;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TypedParam} 密封接口及 5 个 record 子类型的单元测试。
 */
class TypedParamTest {

    // ---- 1. IntParam ----

    @Test
    @DisplayName("IntParam 解析有效整数")
    void intParam_parsesValidInt() {
        var param = new TypedParam.IntParam("count", "次数", 1);
        assertEquals(42, param.parse(Map.of("count", "42")));
    }

    @Test
    @DisplayName("IntParam 缺失键时返回默认值")
    void intParam_returnsDefaultForMissing() {
        var param = new TypedParam.IntParam("count", "次数", 1);
        assertEquals(1, param.parse(Map.of()));
    }

    @Test
    @DisplayName("IntParam 无效格式时返回默认值")
    void intParam_returnsDefaultForInvalid() {
        var param = new TypedParam.IntParam("count", "次数", 1);
        assertEquals(1, param.parse(Map.of("count", "not_a_number")));
    }

    // ---- 2. DoubleParam ----

    @Test
    @DisplayName("DoubleParam 解析有效浮点数")
    void doubleParam_parsesValidDouble() {
        var param = new TypedParam.DoubleParam("amount", "数量", 0.0);
        assertEquals(3.14, param.parse(Map.of("amount", "3.14")), 1e-9);
    }

    @Test
    @DisplayName("DoubleParam 缺失键时返回默认值")
    void doubleParam_returnsDefaultForMissing() {
        var param = new TypedParam.DoubleParam("amount", "数量", 5.5);
        assertEquals(5.5, param.parse(Map.of()), 1e-9);
    }

    @Test
    @DisplayName("DoubleParam 无效格式时返回默认值")
    void doubleParam_returnsDefaultForInvalid() {
        var param = new TypedParam.DoubleParam("amount", "数量", 5.5);
        assertEquals(5.5, param.parse(Map.of("amount", "not_a_double")), 1e-9);
    }

    // ---- 3. BoolParam ----

    @Test
    @DisplayName("BoolParam 解析 'true' 为 true")
    void boolParam_parsesTrue() {
        var param = new TypedParam.BoolParam("flag", "标志", false);
        assertTrue(param.parse(Map.of("flag", "true")));
    }

    @Test
    @DisplayName("BoolParam 解析 'false' 为 false")
    void boolParam_parsesFalse() {
        var param = new TypedParam.BoolParam("flag", "标志", true);
        assertFalse(param.parse(Map.of("flag", "false")));
    }

    @Test
    @DisplayName("BoolParam 缺失键时返回默认值")
    void boolParam_returnsDefaultForMissing() {
        var param = new TypedParam.BoolParam("flag", "标志", true);
        assertTrue(param.parse(Map.of()));
    }

    // ---- 4. StringParam ----

    @Test
    @DisplayName("StringParam 返回原始字符串")
    void stringParam_returnsRawValue() {
        var param = new TypedParam.StringParam("msg", "消息", "hello");
        assertEquals("world", param.parse(Map.of("msg", "world")));
    }

    @Test
    @DisplayName("StringParam 缺失键时返回默认值")
    void stringParam_returnsDefaultForMissing() {
        var param = new TypedParam.StringParam("msg", "消息", "hello");
        assertEquals("hello", param.parse(Map.of()));
    }

    // ---- 5. SelectParam ----

    @Test
    @DisplayName("SelectParam 返回有效选项")
    void selectParam_returnsValidOption() {
        var param = new TypedParam.SelectParam("color", "颜色", "red", List.of("red", "green", "blue"));
        assertEquals("green", param.parse(Map.of("color", "green")));
    }

    @Test
    @DisplayName("SelectParam 无效选项回退到默认值")
    void selectParam_fallsBackToDefaultForInvalid() {
        var param = new TypedParam.SelectParam("color", "颜色", "red", List.of("red", "green", "blue"));
        assertEquals("red", param.parse(Map.of("color", "yellow")));
    }

    @Test
    @DisplayName("SelectParam 缺失键时返回默认值")
    void selectParam_returnsDefaultForMissing() {
        var param = new TypedParam.SelectParam("color", "颜色", "red", List.of("red", "green", "blue"));
        assertEquals("red", param.parse(Map.of()));
    }

    // ---- 6. Visitor 正确分发至各子类型 ----

    @Test
    @DisplayName("Visitor 正确分发至各子类型")
    void visitor_dispatchesCorrectly() {
        // 使用访问者收集类型标签
        var visitor = new TypedParam.TypedParamVisitor<String>() {
            @Override
            public String visit(TypedParam.IntParam param) {
                return "IntParam";
            }

            @Override
            public String visit(TypedParam.DoubleParam param) {
                return "DoubleParam";
            }

            @Override
            public String visit(TypedParam.BoolParam param) {
                return "BoolParam";
            }

            @Override
            public String visit(TypedParam.StringParam param) {
                return "StringParam";
            }

            @Override
            public String visit(TypedParam.SelectParam param) {
                return "SelectParam";
            }
        };

        assertEquals("IntParam", new TypedParam.IntParam("a", "A", 0).accept(visitor));
        assertEquals("DoubleParam", new TypedParam.DoubleParam("b", "B", 0.0).accept(visitor));
        assertEquals("BoolParam", new TypedParam.BoolParam("c", "C", false).accept(visitor));
        assertEquals("StringParam", new TypedParam.StringParam("d", "D", "").accept(visitor));
        assertEquals("SelectParam", new TypedParam.SelectParam("e", "E", "", List.of()).accept(visitor));
    }

    // ---- 7. name() 和 displayName() 返回构造参数 ----

    @Test
    @DisplayName("name() 和 displayName() 返回构造参数值")
    void nameAndDisplayNameReturnConstructorValues() {
        var intParam = new TypedParam.IntParam("int_key", "整数", 10);
        assertEquals("int_key", intParam.name());
        assertEquals("整数", intParam.displayName());

        var doubleParam = new TypedParam.DoubleParam("double_key", "浮点数", 1.5);
        assertEquals("double_key", doubleParam.name());
        assertEquals("浮点数", doubleParam.displayName());

        var boolParam = new TypedParam.BoolParam("bool_key", "布尔", true);
        assertEquals("bool_key", boolParam.name());
        assertEquals("布尔", boolParam.displayName());

        var stringParam = new TypedParam.StringParam("string_key", "字符串", "default");
        assertEquals("string_key", stringParam.name());
        assertEquals("字符串", stringParam.displayName());

        var selectParam = new TypedParam.SelectParam("select_key", "选择", "opt1", List.of("opt1", "opt2"));
        assertEquals("select_key", selectParam.name());
        assertEquals("选择", selectParam.displayName());
    }
}

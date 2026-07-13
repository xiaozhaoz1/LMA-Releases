package littlemaidmoreaction.littlemaidmoreaction.core.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * 条件比较操作符枚举。
 * <p>
 * 每条条件表达式格式为 key:op:value，本枚举定义了 op 部分的所有合法值。
 * 每个枚举值自带比较函数 {@link #test(String, String)}。
 * </p>
 *
 * <p>选择操作符指南：</p>
 * <ul>
 *   <li>数值比较 → :=: :&lt;: :&gt;: :&gt;=: :&lt;=: :!=:</li>
 *   <li>字符串匹配 → :contains: :regex: :in:</li>
 *   <li>布尔判断 → :=:（"true"/"false"）</li>
 * </ul>
 */
public enum ConditionOperator {

    /**
     * 等于 — 智能类型比较。
     * <p>
     * 先尝试将两边解析为 double 做数值近似比较（容差 1e-9），
     * 任一边非数字则退化为字符串 equals。
     * </p>
     *
     * <p>适用场景：布尔值、精确数值、精确字符串。</p>
     */
    EQ(":=", (a, b) -> {
        try { return Math.abs(Double.parseDouble(a) - Double.parseDouble(b)) < 1e-9; }
        catch (NumberFormatException e) { return a.equals(b); }
    }),

    /**
     * 小于 — 纯数值比较。
     * <p>两边必须都是有效数字，否则返回 false。</p>
     */
    LT(":<:", (a, b) -> { try { return Double.parseDouble(a) < Double.parseDouble(b); } catch (NumberFormatException e) { return false; } }),

    /**
     * 大于 — 纯数值比较。
     * <p>两边必须都是有效数字，否则返回 false。</p>
     */
    GT(":>:", (a, b) -> { try { return Double.parseDouble(a) > Double.parseDouble(b); } catch (NumberFormatException e) { return false; } }),

    /**
     * 不等于 — 纯字符串比较。
     * <p>使用 String.equals 做精确匹配，不区分数字/字符串。</p>
     */
    NEQ(":!=:", (a, b) -> !a.equals(b)),

    /**
     * 大于等于 — 纯数值比较。
     * <p>两边必须都是有效数字，否则返回 false。</p>
     */
    GTE(":>=:", (a, b) -> { try { return Double.parseDouble(a) >= Double.parseDouble(b); } catch (NumberFormatException e) { return false; } }),

    /**
     * 小于等于 — 纯数值比较。
     * <p>两边必须都是有效数字，否则返回 false。</p>
     */
    LTE(":<=:", (a, b) -> { try { return Double.parseDouble(a) <= Double.parseDouble(b); } catch (NumberFormatException e) { return false; } }),

    /**
     * 包含子串 — 字符串部分匹配。
     * <p>检查 actual 中是否包含 expected 子串（区分大小写）。</p>
     */
    CONTAINS(":contains:", String::contains),

    /**
     * 正则表达式 — {@link java.util.regex.Pattern#matcher} matcher.find() 部分匹配。
     * <p>Pattern 编译结果会被 PatternCache 缓存，重复使用不消耗性能。</p>
     */
    REGEX(":regex:", (v, p) -> { try { return PatternCache.get(p).matcher(v).find(); } catch (java.util.regex.PatternSyntaxException e) { return false; } }),

    /**
     * 列表匹配 — 逗号分隔的白名单。
     * <p>expected 用逗号分隔多个值，actual 与其中任意一项完全匹配即通过。</p>
     */
    IN(":in:", (v, list) -> {
        for (String item : list.split(",")) {
            if (item.trim().equals(v.trim())) return true;
        }
        return false;
    });

    private final String token;
    private final BiPredicate<String, String> comparator;

    ConditionOperator(String token, BiPredicate<String, String> comparator) {
        this.token = token;
        this.comparator = comparator;
    }

    /**
     * @return 操作符在 JSON 中的 token 字符串，如 ":<:"
     */
    public String getToken() { return token; }

    /**
     * 比较两个字符串值。
     *
     * @param actual   从游戏状态中读取的实际值
     * @param expected 条件中写的期望值
     * @return 比较结果
     */
    public boolean test(String actual, String expected) {
        return comparator.test(actual, expected);
    }

    /**
     * 根据 token 字符串查找操作符。
     *
     * <p>兼容 {@code :=} (历史) 和 {@code :=:} (符合 {@code :op:} 约定)。</p>
     *
     * @param token JSON 中的 op 字段值
     * @return 对应枚举，未匹配时默认返回 {@link #EQ}
     */
    public static ConditionOperator fromToken(String token) {
        for (ConditionOperator op : values()) {
            if (op.token.equals(token)) return op;
        }
        // 向后兼容：":=:" 映射到 EQ
        if (":=:".equals(token)) return EQ;
        return EQ;
    }

    /**
     * 检查 token 是否是一个合法的操作符。
     *
     * <p>区别于 {@link #fromToken}（未匹配返回默认值），
     * 此方法用于启动校验时准确判断 token 是否有效。
     * 兼容 {@code :=} 和 {@code :=:} 两种写法。</p>
     */
    public static boolean isValidToken(String token) {
        for (ConditionOperator op : values()) {
            if (op.token.equals(token)) return true;
        }
        // 向后兼容：":=:" 也是合法操作符（等同于 EQ）
        if (":=:".equals(token)) return true;
        return false;
    }

    /**
     * 正则 Pattern 缓存 — 避免每次 evaluate 都重新编译正则表达式。
     * <p>使用 ConcurrentHashMap 保证线程安全 + 懒加载。</p>
     */
    private static final class PatternCache {
        private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>();
        static Pattern get(String regex) {
            return CACHE.computeIfAbsent(regex, Pattern::compile);
        }
    }
}

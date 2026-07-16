package littlemaidmoreaction.littlemaidmoreaction.core.model;

import java.util.Map;

/** 材料缺口计算结果 */
public record MaterialReport<T>(boolean sufficient, Map<T, Integer> missing) {
    public static final MaterialReport<?> SUFFICIENT = new MaterialReport<>(true, Map.of());

    @SuppressWarnings("unchecked")
    public static <T> MaterialReport<T> ofSufficient() {
        return (MaterialReport<T>) SUFFICIENT;
    }

    public static <T> MaterialReport<T> ofMissing(Map<T, Integer> missing) {
        return new MaterialReport<>(false, Map.copyOf(missing));
    }
}
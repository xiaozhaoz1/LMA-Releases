package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能方块槽位布局 — Builder 模式注册。每个方块定义自己的槽位角色。
 * 替代硬编码的 SlotLayout 常量和熔炉专属的 FurnaceSlotMapping。
 *
 * <pre>{@code
 * // 原版熔炉
 * SlotLayout.FURNACE.slot("input")  → 0
 * SlotLayout.FURNACE.slot("output") → 2
 *
 * // 自定义4槽模组熔炉
 * SlotLayout custom = SlotLayout.builder()
 *     .role("input1", 0).role("input2", 1)
 *     .role("fuel", 2).role("output", 3).build();
 * }</pre>
 */
public record SlotLayout(Map<String, Integer> roles) {
    public int slot(String role) {
        return roles.getOrDefault(role, -1);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<String, Integer> map = new LinkedHashMap<>();
        public Builder role(String name, int index) { map.put(name, index); return this; }
        public SlotLayout build() { return new SlotLayout(Map.copyOf(map)); }
    }

    public static final SlotLayout FURNACE = builder()
        .role("input", 0).role("fuel", 1).role("output", 2).build();
}

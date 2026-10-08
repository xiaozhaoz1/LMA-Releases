package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import net.minecraft.network.chat.Component;

/** v79.62.1 收获方式枚举 (区域详情下拉框用) */
public enum HarvestMode {
    LEFT("left", "左键收"), RIGHT("right", "右键收");

    private final String id;
    private final String label;

    HarvestMode(String id, String label) { this.id = id; this.label = label; }

    public String id() { return id; }

    public Component label() { return Component.literal(label); }

    public static HarvestMode from(String id) { return "right".equals(id) ? RIGHT : LEFT; }
}

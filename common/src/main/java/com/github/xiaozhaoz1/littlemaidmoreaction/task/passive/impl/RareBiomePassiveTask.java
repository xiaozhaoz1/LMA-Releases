package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveTask;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Set;

/**
 * 稀有群系通报被动 (v79.62.1, 纯触发型) — RARE_BIOME 信号 → 气泡 + 主人聊天通报.
 *
 * <p>stateless 广播 (Broadcaster 每轮查当前 biome 稀有即 emit) 配合 per-maid 群系去重:
 * PD 存上次通报的 biomeId, 同群系静默, 换群系再报 (天然覆盖闭环).
 *
 * <p>群系名: biomeId → 中文名走 lang key {@code biome.littlemaidmoreaction.<path>}
 * (zh_cn/en_us 双语; 未翻译回退短名 registry path).
 */
public final class RareBiomePassiveTask implements PassiveTask {

    /** 去重键: 上次通报群系 (biomeId string) — 每群系一次 */
    static final String LAST_BIOME_KEY = TaskKeys.RARE_BIOME_LAST;

    /** 稀有群系列表 (registry id, 用户裁定 5 个) — 显示名经 lang key 映射 */
    private static final Set<String> RARE_BIOMES = Set.of(
            "minecraft:mushroom_fields",
            "minecraft:mushroom_field_shore",
            "minecraft:deep_dark",
            "minecraft:dripstone_caves",
            "minecraft:lush_caves"
    );

    /** lang key 前缀 (biome.littlemaidmoreaction.<path>) */
    private static final String LANG_PREFIX = "biome.littlemaidmoreaction.";

    /** 稀有群系判定 (public: 测试跨包引用) */
    public static boolean isRareBiome(String biomeId) {
        return biomeId != null && RARE_BIOMES.contains(biomeId);
    }

    @Override public String taskType() { return "rare_biome"; }

    @Override public int cooldown() { return 0; }

    @Override
    public Set<String> needsSignals() {
        return Set.of(Signals.ENV_RARE_BIOME);
    }

    @Override
    public void trigger(ServerLevel level, EntityMaid maid, String signalId) {
        if (!Signals.ENV_RARE_BIOME.equals(signalId)) return;
        String biomeId = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader
                .getBiome(level, maid.blockPosition());
        if (!isRareBiome(biomeId)) return;   // 群系不稀有 (信号误发兜底)

        // 每群系去重: 上次通报同群系 → 静默
        String last = maid.getPersistentData().getString(LAST_BIOME_KEY);
        if (biomeId.equals(last)) return;

        String name = displayName(biomeId);
        String msg = "发现稀有群系：" + name + "！";
        // 气泡 (Component 版 — 走翻译)
        MaidChatBubbleApi.showTrigger(maid, Component.literal("§e⚠ ").append(
                Component.translatable("lma.rare_biome.found", name)));
        // 主人聊天 (不只气泡)
        var owner = maid.getOwner();
        if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("msg.littlemaidmoreaction.maid_prefix")
                    .append(Component.translatable("lma.rare_biome.found", name)));
        }
        // 记录去重
        maid.getPersistentData().putString(LAST_BIOME_KEY, biomeId);
    }

    /** 群系中文名 — lang key 翻译, 未翻译回退短名 (registry path) */
    static String displayName(String biomeId) {
        String path = biomeId.contains(":") ? biomeId.substring(biomeId.indexOf(':') + 1) : biomeId;
        return Component.translatable(LANG_PREFIX + path).getString();
    }
}

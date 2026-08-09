package com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.chatbubble.EntityGraphics;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.chatbubble.IChatBubbleRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 女仆表情渲染器 (v79.20, 通用) — 仿 TLM {@code EmojiChatBubbleRenderer}, 但按 {@link MaidEmojiType}
 * 子集过滤后随机选一个表情 (TLM 原生全量随机, 无法指定子集)。
 *
 * <p>TLM 的 {@code EmojiReloadListener.EMOJI_RESOURCES} 为 private 且无过滤 API,
 * 故直接用 {@link Minecraft#getResourceManager()} 查询同一资源目录
 * ({@link MaidEmojiType#EMOJI_PATH}), 按文件名精确匹配子集。
 * 无匹配资源时回退 {@code emoji_0.png} (TLM 同款兜底路径)。
 */
@OnlyIn(Dist.CLIENT)
public class MaidEmojiChatBubbleRenderer implements IChatBubbleRenderer {
    private final int width;
    private final int height;
    private final ResourceLocation emoji;

    public MaidEmojiChatBubbleRenderer(MaidEmojiType type) {
        ResourceLocation picked = pickEmoji(type);
        if (picked != null) {
            this.emoji = picked;
            this.width = 24;
            this.height = 24;
        } else {
            // 没有匹配资源 → TLM 同款默认空白兜底
            this.emoji =
//? if 1.20.1 {
                    new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/chat_bubble/maid_emoji/emoji_0.png");
//?} else {
                    ResourceLocation.fromNamespaceAndPath(TouhouLittleMaid.MOD_ID, "textures/chat_bubble/maid_emoji/emoji_0.png");
//?}
            this.width = 24;
            this.height = 24;
        }
    }

    /** 从类型子集随机选一个已加载表情资源 (子集固定 24x24 — 用户指定的 6 个文件均 24x24) */
    private static ResourceLocation pickEmoji(MaidEmojiType type) {
        var resources = Minecraft.getInstance().getResourceManager()
                .listResources(MaidEmojiType.EMOJI_PATH, key -> type.matches(key.getPath()));
        if (resources.isEmpty()) {
            return null;
        }
        List<ResourceLocation> keys = new ArrayList<>(resources.keySet());
        return keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public void render(EntityMaidRenderer renderer, EntityGraphics graphics) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(this.emoji, 0, 0, 0, 0, this.width, this.height, this.width, this.height);
    }

    @Override
    public ResourceLocation getBackgroundTexture() {
        return IChatBubbleData.TYPE_2;
    }
}

package com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.chatbubble.IChatBubbleRenderer;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

/**
 * 女仆表情气泡数据 (v79.20, 通用) — 仿 TLM {@code EmojiChatBubbleData} 模式:
 * 只序列化表情类型 (byte), 客户端 {@link #getRenderer} 懒创建渲染器按类型选表情。
 *
 * <p>自定义类型注册走 {@code ChatBubbleRegister} 扩展点
 * ({@code ILittleMaid.registerChatBubble}) — LMA 扩展入口
 * {@code LittleMaidMoreActionExtension.registerChatBubble}。
 */
public class MaidEmojiBubbleData implements IChatBubbleData {
    public static final ResourceLocation ID =
//? if 1.20.1 {
            new ResourceLocation(LittleMaidMoreAction.MOD_ID, "maid_emoji");
//?} else {
            ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "maid_emoji");
//?}

    private final MaidEmojiType type;
    @OnlyIn(Dist.CLIENT)
    private IChatBubbleRenderer renderer;

    public MaidEmojiBubbleData(MaidEmojiType type) {
        this.type = type;
    }

    public static MaidEmojiBubbleData create(MaidEmojiType type) {
        return new MaidEmojiBubbleData(type);
    }

    public MaidEmojiType type() {
        return type;
    }

    @Override
    public int existTick() {
        return DEFAULT_EXIST_TICK;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public IChatBubbleRenderer getRenderer(IChatBubbleRenderer.Position position) {
        if (this.renderer == null) {
            this.renderer = new MaidEmojiChatBubbleRenderer(this.type);
        }
        return this.renderer;
    }

    public static class MaidEmojiChatSerializer implements IChatBubbleData.ChatSerializer {
        @Override
        public IChatBubbleData readFromBuff(FriendlyByteBuf buf) {
            byte typeId = buf.readByte();
            return new MaidEmojiBubbleData(MaidEmojiType.byId(typeId));
        }

        @Override
        public void writeToBuff(FriendlyByteBuf buf, IChatBubbleData data) {
            MaidEmojiBubbleData emoji = (MaidEmojiBubbleData) data;
            buf.writeByte(emoji.type.id());
        }
    }
}

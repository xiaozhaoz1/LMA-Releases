package com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 女仆表情气泡类型 (v79.20, 通用) — 区分"对女仆"和"对主人"的表情子集。
 *
 * <p>表情资源位于 TLM 的 {@code textures/chat_bubble/maid_emoji/} 目录 (EmojiReloadListener 扫描)。
 * TLM 原生 {@code EmojiChatBubbleRenderer} 从全量表情中随机 — 无法指定子集, 故 LMA 自建
 * 按文件名字集过滤 + 随机 (MaidEmojiChatBubbleRenderer)。
 *
 * <p>表情随机与语音随机相互独立 (两个独立随机调用点)。
 */
public enum MaidEmojiType {
    /** 对女仆 — 用户指定 3 个表情 */
    MAID((byte) 0, "emoji_10.png", "emoji_05.png", "emoji_09.png"),
    /** 对主人 — 用户指定 3 个表情 */
    OWNER((byte) 1, "emoji_01.png", "emoji_02.png", "emoji_20-24x24.png");

    /** TLM 表情资源目录 (与 EmojiReloadListener.EMOJI_PATH 一致) */
    public static final String EMOJI_PATH = "textures/chat_bubble/maid_emoji";

    private final byte id;
    private final List<String> files;

    MaidEmojiType(byte id, String... files) {
        this.id = id;
        this.files = List.of(files);
    }

    /** 该类型包含的所有表情文件名 (如 emoji_10.png) */
    public List<String> files() {
        return files;
    }

    /** 资源路径是否属于本类型的子集 (精确匹配文件名, 不用正则 — emoji_20-24x24.png 无歧义) */
    public boolean matches(String path) {
        for (String file : files) {
            if (path.endsWith("/" + file) || path.equals(file)) {
                return true;
            }
        }
        return false;
    }

    /** 从子集随机选一个文件名 — 表情随机与语音随机独立 (单独随机源) */
    public String randomFileName() {
        return files.get(ThreadLocalRandom.current().nextInt(files.size()));
    }

    /** 序列化 id (网络包通道) */
    public byte id() {
        return id;
    }

    /** 反序列化 — 未知 id 回退 MAID (向后兼容) */
    public static MaidEmojiType byId(byte id) {
        return id == OWNER.id ? OWNER : MAID;
    }
}

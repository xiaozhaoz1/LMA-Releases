/**
 * 聊天气泡 — 通用气泡 API 落点 (v79.21 完整化)。
 *
 * <h3>已实现</h3>
 * <ul>
 *   <li>{@link MaidChatBubbleApi} — 通用门面 (v79.21): 信息/完成/失败/触发/进度气泡,
 *       只用 TLM 内置 {@code TextChatBubbleData}/{@code ProgressChatBubbleData}
 *       (用户裁定不加自定义类型); § 码文字色 (TLM 无绿色纹理);
 *       失败 600t / 触发 100t 节流; 进度气泡替换式防堆积; 服务端直调自动同步</li>
 *   <li>表情气泡 (v79.20) — HaqiEmojiApi + HaqiEmojiBubbleData +
 *       HaqiEmojiChatBubbleRenderer (自定义类型注册, 表情包专用)</li>
 *   <li>任务语义映射 (友好名/状态中文) 在 adapter 层 {@code LmaTaskProgressDisplay} —
 *       本包禁 import task/adapter 包 (防循环依赖)</li>
 * </ul>
 */
package com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble;

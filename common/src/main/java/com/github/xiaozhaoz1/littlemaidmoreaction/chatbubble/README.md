# chatbubble — 气泡/表情层

**作用**: 女仆头顶气泡与表情的统一门面——信息/失败/完成/进度气泡 + 表情弹窗, 内置节流。

**判据 (什么进这里)**: 一切「对玩家可见的短提示」。调用方只调 Api, 不管渲染。

**依赖方向**: network (同步包) + client 渲染; 被 task/pipeline、vanilla/execute 等调用。

**代表**: MaidChatBubbleApi (节流内置) / MaidEmojiApi / MaidEmojiType

**修改注意**: 节流在 API 内 (600t/100t) — 调用方别再包一层; 高频气泡变更是 TLM 断线竞态根因 (40t 下限教训)。

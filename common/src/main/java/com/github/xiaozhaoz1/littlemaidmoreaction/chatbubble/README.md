# chatbubble — 女仆气泡/表情 (聊天反馈)

**作用**: 女仆头顶**气泡** (文本反馈) 与**表情气泡** (emoji) 的 API + 数据 + 客户端渲染。
**依赖方向**: 原版 + TLM 客户端 API + task/data (读状态); 服务端发数据、客户端渲染。

## 一、类明细 (6 类)
| 类 | 行数 | 职责 |
|---|---|---|
`MaidChatBubbleApi` | 178 | **对外 API**: `showFail/show...` 发送气泡 (⚠ 内置**节流**: 失败气泡 30s 冷却, 防无限重试刷屏) |
`MaidEmojiBubbleData` | 80 | 表情气泡数据 (类型 + 时长 + 文本) |
`MaidEmojiType` | 61 | 表情类型枚举 (与资源对应) |
`MaidEmojiChatBubbleRenderer` | 89 | 客户端渲染 (头顶表情气泡) |
`MaidEmojiApi` | 46 | 表情 API (播放指定表情) |
`package-info` | 16 | 包说明 |

## 二、连接链
```
服务端: pipeline/service → MaidChatBubbleApi.showFail(...)  (节流后) → 数据下发客户端
客户端: MaidEmojiBubbleRenderer 渲染 (MaidEmojiBubbleData + MaidEmojiType)
```
**典型调用方**: 各管线 (失败反馈: `WorkStationPipeline.tick` 的 FAILED 分支 · furnace 无料 1200t 冷却气泡 · EngineGuard 隔离提示)。

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **气泡必须节流** | 服务端每 tick 判失败会刷屏 ⇒ 用 `ThrottleUtil` (如 furnace 的 1200t) 或 API 内置节流; 新增"每拍失败"分支必须自带冷却。|
**B** | **服务端发 / 客户端渲染** | 数据在服务端产生, 渲染只在客户端; 不要在服务端碰渲染类 (dedicated server 会崩)。|
**C** | **文案走 lang 键** | 面向用户的气泡用 `Component.translatable` (键进 `zh_cn/en_us`), 不要硬编码中文 (i18n 一致性)。|

# network — 网络层 (包定义 + 双平台注册)

**作用**: 所有客户端↔服务端通信。**清单驱动注册**: `PacketRegistry.DEFS` 是**唯一事实源**, 平台 registrar 循环消费 (forge/neoforge 各一个)。

## 一、基建 (4 类)
| 类 | 职责 |
|---|---|
`PacketDef` | 单个包定义 (id / 编码器 / 解码器 / 处理器) |
`PacketRegistry` | **包清单** (`DEFS`) + 注册入口 — 新增包**只改这里**, 平台侧不逐个写 |
`SimpleChannelSender` / `PacketCodecs` | 发送辅助 + 编解码工具 (统一写法) |
`C2SThrottle` | **客户端→服务端限流** (防刷) |

## 二、包清单 (按用途)
| 域 | 包 |
|---|---|
**任务配置** | `TaskConfigActionPacket` (per-task 配置写: setInt/toggle/remove/setList) · `RequestTaskConfigPacket` / `ReplyTaskConfigPacket` · `ConfigSyncPacket` |
**农田** | `FarmRegionBindPacket` · `FarmRegionEditPacket` · `FarmRegionSyncPacket` · `FarmContainerBindPacket` |
**交互/按键** | `InteractTriggerPacket` · `KeyTriggerRegistry` (键位→触发注册表) |
**女仆列表/图鉴** | `MaidListQueryPacket` / `MaidListResponsePacket` · `MaidCodexScreenPacket` |
**动画** | `AnimFileSyncPacket` (大包, 分片) · `LmaAnimSyncMessage` |
**其它** | `MaidGomokuStatePacket`/`MaidGomokuTogglePacket` · `MaidEnvSenseTogglePacket` · `HaqiOwnerVoicePacket` · `PatPatReactionPacket` · `MaidChatBubblePacket` |

### 客户端 handler (`network/client/`, 5 类 — 2026-09-21 审计补录)
> **维护约定**: 本子包**无独立 README**, 明细集中在本节 (同 `compat/` 的子包做法 ✓)。全部只在**客户端**加载 (屏/UI/声音),
> 入口统一是 `PacketRegistry.DEFS` 里对应包的 `ctx.enqueueWork` 回调 ⇒ **新增/改签名必须同步 `PacketRegistry` 与平台 registrar** ✓

| 类 | 入口签名 | 干什么 |
|---|---|---|
`AnimFileSyncClientHandler` | `handle(String fileName, byte[] content)` + `flushPending()` | 接**分片**动画文件 → 重组落缓存 (配套 `AnimFileSyncPacket` 大包分片; 另被 `compat/ysm/YsmReloadListener` 消费做热合并 ✓) |
`AnimSyncClientHandler` | `apply(int maidId, CompoundTag animData)` | 女仆动画状态同步 (客户端应用动画数据) |
`HaqiVoiceClientHandler` | `play(int maidId, float volume)` | 播放「哈气」音效 (配 `HaqiOwnerVoicePacket`) |
`MaidBubbleClientHandler` | `apply(int maidId, byte emojiType)` | 头顶表情气泡 (配 `MaidChatBubblePacket`) |
`TaskConfigReplyClientHandler` | `apply(int maidId, CompoundTag config)` | per-task 配置回包 → 刷新配置屏 (配 `ReplyTaskConfigPacket`) |

## 三、连接链
```
发送: 客户端/服务端 → SimpleChannelSender → 平台 channel (PacketRegistry 注册的包)
处理: 解码 → ctx.enqueueWork → 服务端 handler (写数据/调 Dispatcher) 或客户端 handler (开屏/更新 UI)
注册: PacketRegistry.DEFS (单一事实源) → ForgePacketRegistrar / NeoForge 侧 registrar 循环消费
配置包闭环: Screen.sendSetInt → TaskConfigActionPacket → TaskConfigurable.handleConfigAction
             → MaidData.cfgOrCreate(...)   (参 task/gui/README.md §二)
```

## 四、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **包 id 是协议契约** | 增删包时 id 不可复用/重排 (老客户端会解错) ⇒ **只在 `DEFS` 尾部追加**。|
**B** | **双平台注册必须都验** | forge 通过 ≠ neoforge 通过 (channel API 不同); 新增包后**双节点编译 + 实机连一次**。|
**C** | **侧别处理别搞反** | 服务端 handler 里不要碰客户端类 (Screen/Minecraft), 反之亦然; 跨侧统一走 `ctx.enqueueWork`。|
**D** | **大包要分片/限流** | 资源类同步 (动画文件) 参考 `AnimFileSyncPacket` 的分片; 客户端发起的重复包走 `C2SThrottle`。|
**E** | **写数据的包必须幂等** | 可能重发/乱序 ⇒ 以"最终状态"为单位写, 不要写"增量" (或用版本号/序号)。|
**F** | **每个 C2S 解码点 = 信任边界** (错题 **#194**) | `decode` 里的 `readNbt()` / `readUtf()` 等反序列化入口**必须判空/校验** —— 损坏包或恶意包可构造任意字节 ⇒ 空 NBT 直传 handler = **崩服** ✗。既有修法: `readNbt()==null → new CompoundTag()` 兜底 + handler 侧二次判空 (双保险)。<br>跨版本: 两版 `FriendlyByteBuf` **形态相同** (`readNbt()` + `readNbt(NbtAccounter)` 重载并存) —— [1.20.1](https://lexxie.dev/forge/1.20.1/net/minecraft/network/FriendlyByteBuf.html) · [1.21.1](https://lexxie.dev/neoforge/1.21.1/net/minecraft/network/FriendlyByteBuf.html); 本项目 **#194 实证的是 1.20.1 `readNbt()` 可返回 `null`** (空 NBT) ⇒ **两版都判空** (防御性, 不依赖版本行为) ✓ |

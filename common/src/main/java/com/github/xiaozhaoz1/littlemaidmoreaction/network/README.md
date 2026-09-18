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

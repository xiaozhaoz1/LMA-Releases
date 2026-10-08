# client — 客户端专用 (仅客户端加载)

**作用**: **只在客户端加载**的逻辑: 按键触发、声音播放、客户端资源子集加载。
**依赖方向**: 原版客户端 + TLM 客户端 API + network (发包); **dedicated server 不加载本包**。

## 一、类明细 (5 类)
| 类 | 行数 | 职责 |
|---|---|---|
`PecoHaqiSubsetLoader` | 153 | **哈气音效子集加载** (按需加载音频, 控制内存/启动成本) |
`GuiScreenBindings` | **客户端菜单↔屏绑定表** (2026-09-21): `DEFS` = 屏侧**唯一事实源** (8 对); 两平台 ClientEntry 循环消费 (`screenOf` + 各家 register API ✓) |
`MaidKeyTriggerClient` | 100 | **客户端按键触发**: 按键 → 发 `InteractTriggerPacket` (服务端执行) |
`PecoHaqiSoundPlayer` | 97 | 哈气音效播放 (客户端侧) |
`LmaHaqiVoiceSoundEvent` | 48 | 哈气语音声音事件 (驱动播放) |
`AnimationResourceRegistrar` | 96 | **动画资源注册器** (2026-09-21 由 `api/` 迁入): 自定义动画扫描 (`scanCustomAnimations`) · TLM/Gecko 注册 (`registerCustomAnimations`) · 缓存 ISS AnimationFile 引用 + 热重合并 (`remergeAll`, S2C 动画文件同步后调用) — 全类方法 `@OnlyIn(CLIENT)` 且引 `Minecraft` ⇒ 客户端专用, 不该住「对外契约层」 |

## 二、连接链
```
按键: MaidKeyTriggerClient (客户端监听) → InteractTriggerPacket → 服务端 KeyTriggerRegistry 触发任务
声音: LmaHaqiVoiceSoundEvent → PecoHaqiSoundPlayer (播放; 素材来自 PecoHaqiSubsetLoader 的子集)
动画: 平台客户端入口 (构造器手动注册 DefaultGeckoAnimationEvent) → AnimationResourceRegistrar.registerCustomAnimations
      + 资源重载/收包后: network/client.AnimFileSyncClientHandler → AnimationResourceRegistrar.remergeAll (热合并 ISS)
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **绝不能进服务端路径** | 本包类引用客户端类 (Minecraft/ClientLevel); 服务端代码路径不得触及 (dedicated server 崩) ⇒ 只能从平台**客户端入口**调用。|
**B** | **按键只发意图, 不做判定** | 客户端只上报"按了什么" (`InteractTriggerPacket`), 授权/条件判定在服务端 (`KeyTriggerRegistry`) — 防作弊与状态不一致。|
**C** | **资源加载要有子集意识** | 音效/贴图按需加载 (`PecoHaqiSubsetLoader` 先例); 全量加载会拖启动。|

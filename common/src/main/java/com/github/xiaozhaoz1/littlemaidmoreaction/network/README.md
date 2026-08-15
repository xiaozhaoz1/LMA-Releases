# network — 网络层

**作用**: 全部数据包 (C2S/S2C) + 注册表 + 节流——任务配置/动画同步/气泡/女仆列表/按键/图鉴。

**判据 (什么进这里)**: 包定义/编解码/注册/节流 (PacketDef/PacketRegistry 单一事实源)。

**依赖方向**: task/data + config; 被业务层发送/客户端接收。

**代表**: PacketDef + PacketRegistry (DEFS 16 条) / C2SThrottle / PacketCodecs / ForgePacketRegistrar (forge) / NeoNetworkHandler (neoforge)

**修改注意**:
1. C2S 三件套: 鉴权 (owner+距离) + 判空 + 节流 (错题 #194); S2C 四件套: 上界/判空/跳过不抛/文件名校验 (错题 #198)
2. decode 异常路径日志用 System.Logger (纯 JVM 测试铁律 #174)
3. 包清单漂移启动即炸 (NetworkPacketManifestTest)

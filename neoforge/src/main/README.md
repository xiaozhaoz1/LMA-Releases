# neoforge/src/main — 平台专属代码 (1.21.1)

**作用**: 仅 NeoForge 1.21.1 的代码——Numen 假人桥 + NeoForge 网络注册 (payload) + 客户端入口。

**判据**: 平台 API 不同的代码 (payload 网络/neo 事件); 能进 common 的不放这里。

**代表**: compat/numen/NumenMaidBridge / network/NeoNetworkHandler / LmaNeoForgeEntry + LmaNeoForgeClientEntry

**修改注意**: NeoForge 禁止监听抽象事件类 (必须子类); 同 TYPE payload 只能注册一次 (双向需双 TYPE); copyResourcesToClasses 资源重打 (jar 内验证)。

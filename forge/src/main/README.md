# forge/src/main — 平台专属代码 (1.20.1)

**作用**: 仅 Forge 1.20.1 的代码——CBC 速射炮任务 + Forge 网络注册 + 客户端入口。

**判据**: 平台 API 不同的代码 (SimpleChannel 网络/forge 事件); 能进 common 的不放这里。

**代表**: compat/createbigcannons/task/* (CannonLoadPipeline) / network/ForgePacketRegistrar / LmaForgeClientEntry

**修改注意**: 条件化以 stonecutter //? 为主, 本目录只放「1.21 没有对应物」的类; 资源改动后 jar 内验证 (unzip 验 lang 教训)。

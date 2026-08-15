# init — 注册初始化层

**作用**: Mod 资源注册点——物品/方块/方块实体/音效 + 统一注册链 (LmaRegistrar)。

**判据 (什么进这里)**: DeferredRegister 与注册入口; 注册后的使用在业务层。

**代表**: LmaItems / LmaBlocks / LmaSounds / LmaBlockEntityTypes / LmaRegistrar / MaidCodexItem

**修改注意**: 双平台注册 (DeferredRegister 条件化); 主类字节码禁客户端类 (错题 #168) — 注册面不含 Screen。

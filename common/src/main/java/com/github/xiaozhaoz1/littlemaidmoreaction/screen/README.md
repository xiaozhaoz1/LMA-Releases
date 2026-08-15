# screen — GUI 层

**作用**: 全部客户端界面——模组主界面/女仆列表/属性/任务树/配置/兼容开关/图鉴 + 面板注册。

**判据 (什么进这里)**: Screen/按钮/渲染助手; 数据组装在服务端 (网络包)。

**依赖方向**: network + config; 被客户端入口注册 (ScreenRegistry 单一事实源)。

**代表**: LMAConfigScreen / MaidListScreen / MaidAttributeScreen / TaskTreeScreen / CompatConfigScreen / MaidGuiRegistry (TLM GUI 注入) / PanoramaBackground

**修改注意**: 1.20/1.21 渲染签名不同 (renderPanorama 仅 1.21); 主类字节码禁 Screen (经 opener 注入, 错题 #168); 屏样式回归 (全景/面板) 看 changelog v79.26 系列。

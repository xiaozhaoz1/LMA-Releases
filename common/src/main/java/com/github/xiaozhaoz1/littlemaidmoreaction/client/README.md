# client — 客户端侧

**作用**: 仅客户端加载的代码——按键映射 + 哈气声音播放 (peco 包)。

**判据 (什么进这里)**: 客户端专属 (声音/按键), 主类字节码禁止引用 (错题 #168)。

**依赖方向**: 被 LmaForgeClientEntry/LmaNeoForgeClientEntry 注入。

**代表**: MaidKeyTriggerClient (按键) / PecoHaqiSoundPlayer + PecoHaqiSubsetLoader + LmaHaqiVoiceSoundEvent (哈气音)

**修改注意**: 声音域 (哈气音三件套) 与按键混包 — 新增音频优先考虑 resource/storage 域; 客户端类严禁被 common 主类 import。

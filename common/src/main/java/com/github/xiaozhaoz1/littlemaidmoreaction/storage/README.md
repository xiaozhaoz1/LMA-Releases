# storage — 存档加载层

**作用**: 启动/存档期资源加载——启动器/节日表/动画存储。

**代表**: StartupLoader / FestivalLoader / LmaAnimationStorage

**修改注意**: 启动路径是性能敏感 (日志风暴/磁盘 IO 教训 v79.26 卡顿修复); 节日走现实日期口径 (农历库)。

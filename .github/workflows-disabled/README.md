# CI 已停用（2026-09-18，用户裁定）

## 为什么停
`.github/workflows/build.yml` 的 6 个 job **从未能通过** ✗ —— 根因：干净检出里**没有**：
- `libs-maven/`（71 MB，TLM 等**本地 maven 坐标** ⇒ 依赖解析失败 ✗）
- `libs/`（238 MB，Create/Ponder/FLIB/CBC/TruePOWER/PatPat 的 `compileOnly files(...)` ⇒ `compat/create/*` 编译找不到类 ✗）

两者都在 `.gitignore`（第 22/23 行）⇒ CI 的 `just build` 必然倒在**编译**（已用 `git archive` 干净树复现 ✓）。

## 现在怎么做验证
**本地门禁**（等价且更严）：
```
just gate          # 双平台 compile + 单测（全量 XML 解析）
build-logs/deploy-lma.sh <版本>   # 打包 + 单测门禁 + 构建/交付/mods 三方 md5 比对（红了拒绝部署 ✓）
```

## 想恢复 CI 时
1. 把 `build.yml` 移回 `.github/workflows/` ✓
2. 先解决依赖来源（推荐：把 `libs/` + `libs-maven/` 打成 `libs-deps.zip` 传到 **LMA-Releases 的 Release 附件** ✓，
   在 workflow 里加 `curl -L … -o deps.zip && unzip -q deps.zip` ✓ —— 代码库保持干净 ✓）

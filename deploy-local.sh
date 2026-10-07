#!/usr/bin/env bash
# LMA 双平台部署脚本 (v79.78 固化) — 专治"静默失败" ✗
# 规则:
#  ① 先建后换: build/libs 里两端 jar 都存在才动手 ✓ (否则原样退出 ✓)
#  ② mods 里的旧包先 **改名**(mv ✓ Windows 允许重命名被占用的文件 ✓) 再复制新包 ✓
#  ③ **复制后必须逐端比对 md5** ✓ (构建 == 交付 == mods ✓) — 不一致就报错退出 ✓
#  ④ 幂等: mods 已是同一 md5 ⇒ 跳过复制 ✓ (游戏开着也安全 ✓)
#  ⑤ **单测闸门**: 解析 forge 节点测试 XML ⇒ failures/errors 非 0 一律拒绝部署 ✓
#     (纪律 6b: 红了不部署 ✓ — 数字来源 = forge/versions/1.20.1/build/test-results/test/*.xml;
#      单测只在 forge 节点跑, neoforge 节点 test = NO-SOURCE ⇒ 本闸门仅以 forge 为准)
set -u
# ★ 版本号: 优先取参数; 缺省时**从版本真相源读取** ✓ (严禁写死旧版本 ✗ —— 曾因此误降级, 见错题 #367)
if [ $# -ge 1 ] && [ -n "${1:-}" ]; then VER="$1"; else
  VER=$(grep -h -m1 '^project.version=' versions/1.20.1/gradle.properties 2>/dev/null | sed 's/.*=//; s/+.*//')
fi
[ -n "$VER" ] || { echo "[FAIL] 无法确定版本号 (请传参 或 检查 versions/1.20.1/gradle.properties)"; exit 1; }
# ★ 四文件一致性校验 (gradle.properties x2 + mods.toml x2) —— 版本号漂移一律拒绝 ✓
for f in versions/1.20.1/gradle.properties versions/1.21.1/gradle.properties; do
  v=$(grep -h -m1 '^project.version=' "$f" | sed 's/.*=//; s/+.*//')
  [ "$v" = "$VER" ] || { echo "[FAIL] 版本不一致: $f=$v 但目标=$VER (先统一版本号再部署 ✓)"; exit 1; }
done
for f in forge/src/main/resources/META-INF/mods.toml neoforge/src/main/resources/META-INF/neoforge.mods.toml; do
  v=$(grep -m1 -E '^[[:space:]]*version[[:space:]]*=' "$f" | sed -E 's/^[^=]*=[[:space:]]*"//; s/".*$//')
  [ "$v" = "$VER" ] || { echo "[FAIL] 版本不一致: $f=$v 但目标=$VER"; exit 1; }
done
echo "[gate] 版本号一致 = $VER ✓ (真相源 versions/*/gradle.properties)"
ROOT="$(cd "$(dirname "$0")" && pwd)"
REL=/d/claudecode/release-$VER
F="$ROOT/forge/versions/1.20.1/build/libs/littlemaidmoreaction-forge-$VER+1.20.1.jar"
N="$ROOT/neoforge/versions/1.21.1/build/libs/littlemaidmoreaction-neoforge-$VER+1.21.1.jar"
FM="F:/mc/.minecraft/versions/1.20.1-Forge_47.4.13/mods"
NM="F:/mc/.minecraft/versions/1.21.1-NeoForge_21.1.247/mods"

# ── 单测闸门 (先于任何复制/部署动作) ──────────────────────────────────────────
TR="$ROOT/forge/versions/1.20.1/build/test-results/test"
_xml=$(find "$TR" -maxdepth 1 -name '*.xml' 2>/dev/null | head -1)
if [ -z "$_xml" ]; then
  echo "[FAIL] 未找到测试 XML ($TR/*.xml) ⇒ 先跑单测:"
  echo "       ./gradlew.bat :forge:1.20.1:test --rerun-tasks"
  exit 1
fi
if grep -qE 'failures="[1-9]|errors="[1-9]' "$TR"/*.xml 2>/dev/null; then
  echo "[FAIL] 单测有失败/错误 ⇒ 拒绝部署 (纪律 6b: 红了不部署)"
  grep -hoE 'tests="[0-9]+" skipped="[0-9]+" failures="[0-9]+" errors="[0-9]+"' "$TR"/*.xml 2>/dev/null \
    | awk '{t+=$1+0; s+=$2+0} END {print "       汇总: tests="t" skipped="s}' 2>/dev/null || true
  exit 1
fi
_cnt=$(grep -ho "tests=\"[0-9]*\"" "$TR"/*.xml 2>/dev/null | sed 's/[^0-9]//g' | awk '{n+=$1} END {print n+0}')
echo "  [gate] 单测闸门通过 (tests=$_cnt failures=0 errors=0)"

[ -f "$F" ] || { echo "[FAIL] 缺 forge 构建产物: $F"; exit 1; }
[ -f "$N" ] || { echo "[FAIL] 缺 neo 构建产物: $N"; exit 1; }
mkdir -p "$REL"
cp -f "$F" "$REL/" && cp -f "$N" "$REL/" || { echo "[FAIL] 复制到交付目录失败"; exit 1; }
for pair in "$F|$FM/$(basename "$F")|$FM" "$N|$NM/$(basename "$N")|$NM"; do
  src="${pair%%|*}"; rest="${pair#*|}"; dst="${rest%%|*}"; dir="${rest#*|}"
  want=$(md5sum "$src" | cut -d' ' -f1)
  if [ -f "$dst" ] && [ "$(md5sum "$dst" | cut -d' ' -f1)" = "$want" ]; then
    echo "  [skip] $(basename "$dst") 已是同一版本 (md5 ok)"
  else
    # ★ 修正 (v79.78, #366 轮): 把该目录下**所有**非目标版本的活跃 jar 一并隔离 ✗→✓
    #   否则会出现"两个 LMA jar 同时激活" ⇒ 双 mod ⇒ 游戏崩 ✗ (实测: 0.9.74 + 0.9.78 并存 ✗)
    for other in "$dir"/littlemaidmoreaction-*.jar; do
      case "$other" in
        *.bak-*|*.removed-*) : ;;
        "$dst") : ;;
        *) mv -f "$other" "$other.bak-wrongver-$(date +%m%d%H%M)" && echo "  [isolate] $(basename "$other") (非目标版本)" ;;
      esac
    done
    [ -f "$dst" ] && mv -f "$dst" "$dst.bak-$(date +%m%d%H%M)"     # 目标同名旧包改名 (被占用也能改名 ✓)
    cp -f "$src" "$dst" || { echo "[FAIL] 复制失败 (目标被锁? 请先关游戏): $dst"; exit 1; }
    echo "  [copy] $(basename "$dst")"
  fi
  got=$(md5sum "$dst" 2>/dev/null | cut -d' ' -f1)
  if [ "$got" != "$want" ]; then echo "[FAIL] md5 不一致 → mods=$got 构建=$want ($dst)"; exit 1; fi
  rel=$(md5sum "$REL/$(basename "$dst")" | cut -d' ' -f1)
  [ "$rel" = "$want" ] || { echo "[FAIL] 交付目录 md5 不一致"; exit 1; }
  echo "  [ok]   $(basename "$dst")  md5=${want:0:12} (构建=交付=mods ✓)"
done
echo "[DONE] $VER 双平台就绪 ✓ (游戏需重启才会加载新包 ✓)"

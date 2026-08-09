use utf8;
binmode(STDIN, ':encoding(UTF-8)');
binmode(STDOUT, ':encoding(UTF-8)');
my $f = 'D:/claudecode/LMA-MAIN/changelog.md';
open(my $fh, '<:encoding(UTF-8)', $f) or die "open fail: $!";
my $s = do { local $/; <$fh> };
close $fh;
my $old = "## 0.9.21 (2026-08-07) — v79.19p 工具判断 API (泥土→铲子, 矿→镐, 树→斧)";
my $new = <<'EOF';
### v79.20.2 Changed (2026-08-07, 用户裁定: "你的A*肯定不适合女仆, 你应该直接规划最短线路... 最短线路会直接挖开挡路的方块并搭路")

- **直线路径规划优先, A* 兜底 (★架构裁定)**: 新增 `LinePathPlanner` (O(路径长) 无搜索) — 从起点向目标画直线逐格步进, 挡路可挖→挖 (非 sacred), 沟→搭桥 (isPlaceableSupport 排除 src 背面), 台阶→纯跳 ASCEND, 绕不过→侧移绕行 (对角推进优先防振荡), 垫块跳最后手段; 目标在上→ASCEND (执行端挖头+放依托+跳), 在下→挖脚下逐层下/悬空下落。失败 (无 bound/绕不过) → `PathingApi.findPath` A* 兜底。`PathExecutor.plan()` 先直线后 A*
- **死循环冻结修复 (错题 #119)**: A* MAX_NODES 100k→**15k** (女仆低频慢速, 搜索预算必须小) + 跳过集 SKIP_TTL 20→**600** (30 秒, 原 1 秒 = 每心跳重试 → 反复重搜主线程冻结) + 出队后 bound 剪枝 (boundH=12/boundV=8)
- **单测**: LinePathPlannerTest 17/17 (平地/挖穿/安全不挖改绕/侧移/搭桥/垫块跳/垂直/sacred/回退); 双节点编译 + 全量单测 + gametest 7/7 ×2 全绿

## 0.9.21 (2026-08-07) — v79.19p 工具判断 API (泥土→铲子, 矿→镐, 树→斧)
EOF
index($s, $old) >= 0 or die "anchor not found";
$s =~ s/\Q$old\E/$new/;
open(my $out, '>:encoding(UTF-8)', $f) or die "write fail: $!";
print $out $s;
close $out;
print "CHG-OK\n";

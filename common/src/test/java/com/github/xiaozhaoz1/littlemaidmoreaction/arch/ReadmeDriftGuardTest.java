package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目录 README **防漂移守护** (v79.63) — 防止"代码改了/注释没了, README 里的功能与连接关系一起丢失"。
 *
 * <p><b>背景</b> (用户裁定 2026-09-13): 每个包目录的 README 必须写清"有哪些界面、干什么、怎么连接",
 * 本轮实证的两次事故正是"信息只在代码注释里, 改代码就没了":
 * <ul>
 *   <li>工作站任务不走路 (搜索/validate/半径三处, 无任何文档面可查)</li>
 *   <li>per-task 配置写不落盘 (`cfg` vs `cfgOrCreate`, 8 月修过一次却漏了统一入口)</li>
 * </ul>
 *
 * <p><b>本测试守护</b>:
 * <ol>
 *   <li>`task/pipeline/README.md` 必须提到**每一个**管线类名 (新增管线忘了写 README → 红);</li>
 *   <li>`task/pipeline/README.md` 必须含关键章节 (明细表 / 连接链路 / 陷阱);</li>
 *   <li>`task/gui/README.md` 必须提到**每一个** ConfigScreen 类名 + 必须写明配置写入链路的两条铁律
 *       (`cfgOrCreate`、`ACTION_REMOVE` 回落全局)。</li>
 * </ol>
 *
 * <p><b>刻意的宽松</b>: 只校验"名字出现过 + 章节存在", 不校验内容正确性 (那需要人)。
 * 目的是**让遗忘变红**, 而不是替代思考。
 */
class ReadmeDriftGuardTest {

    private static final Path P = Path.of("task", "pipeline");
    private static final Path G = Path.of("task", "gui");

    /** 某目录的**直属** java 文件 (不递归) — 子包有自己的 README, 由各自用例守护 */
    private static List<Path> directJavaFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        try (var s = java.nio.file.Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(java.nio.file.Files::isRegularFile)
                    .sorted()
                    .forEach(out::add);
        } catch (java.io.IOException e) {
            throw new AssertionError("列目录失败: " + dir, e);
        }
        return out;
    }

    /** 从源码目录收集某后缀的类名 (只看文件名, 不解析内容) */
    private static List<String> classNames(Path dir, String suffix) {
        List<String> out = new ArrayList<>();
        for (Path f : ArchSource.javaFiles(dir)) {
            String n = f.getFileName().toString();
            if (n.endsWith(suffix + ".java")) out.add(n.substring(0, n.length() - 5));
        }
        return out;
    }

    @Test
    void pipelineReadmeCoversEveryPipelineClass() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");

        Path readme = root.resolve(P).resolve("README.md");
        assertTrue(readme.toFile().isFile(), "task/pipeline/README.md 不存在 — 该目录必须有明细 README");
        String doc = ArchSource.read(readme);

        List<String> missing = new ArrayList<>();
        int checked = 0;
        // 只查**直属**子类 — task/pipeline/sense/ 有自己的 README (由 senseReadmeCoversItsOwnClasses 守护)
        for (Path f : directJavaFiles(root.resolve(P))) {
            String cls = f.getFileName().toString().replace(".java", "");
            String src = ArchSource.read(f);
            // 只要求"具体管线类"(排除抽象基类/接口): 能实例化 = 有 taskType() 字面量或 extends 状态机
            boolean concrete = src.contains("taskType()") && !src.contains("abstract class ")
                    && !src.contains("interface ");
            if (!concrete) continue;
            checked++;
            if (!doc.contains(cls)) missing.add(cls);
        }
        assertTrue(checked >= 12, "扫描到的具体管线数异常 (" + checked + ", 期望 ≥12) — 本用例只数**直属**子类 (sense/ 另有用例)");
        assertTrue(missing.isEmpty(),
                "以下管线**没写进 task/pipeline/README.md** (新增管线必须补明细行: 职责/界面/配置键/测试): " + missing);
    }

    @Test
    void pipelineReadmeHasRequiredSections() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");
        String doc = ArchSource.read(root.resolve(P).resolve("README.md"));
        for (String section : new String[]{"怎么连接", "已知陷阱", "配置键", "界面", "gametest"}) {
            assertTrue(doc.contains(section), "task/pipeline/README.md 缺关键章节/字段: " + section);
        }
    }

    @Test
    void senseReadmeCoversItsOwnClasses() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");

        Path senseDir = root.resolve(P).resolve("sense");
        Path readme = senseDir.resolve("README.md");
        assertTrue(readme.toFile().isFile(), "task/pipeline/sense/README.md 不存在 — 子包必须有 README");
        String doc = ArchSource.read(readme);

        List<String> missing = new ArrayList<>();
        List<Path> files = directJavaFiles(senseDir);
        assertTrue(files.size() >= 5, "sense 包类数异常 (" + files.size() + ", 期望 ≥5)");
        for (Path f : files) {
            String cls = f.getFileName().toString().replace(".java", "");
            if (!doc.contains(cls)) missing.add(cls);
        }
        assertTrue(missing.isEmpty(),
                "以下 sense 类没写进 task/pipeline/sense/README.md (被动管线/触发器必须登记): " + missing);
    }

    @Test
    void guiReadmeCoversEveryConfigScreenAndWriteRules() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");

        Path readme = root.resolve(G).resolve("README.md");
        assertTrue(readme.toFile().isFile(), "task/gui/README.md 不存在 — 配置屏目录必须有 README");
        String doc = ArchSource.read(readme);

        List<String> missing = new ArrayList<>();
        List<String> screens = classNames(root.resolve(G), "Screen");
        assertTrue(screens.size() >= 8, "扫描到的配置屏数异常 (" + screens.size() + ", 期望 ≥8)");
        for (String s : screens) {
            if (!"LmaTaskConfigScreen".equals(s) && !doc.contains(s)) missing.add(s);
        }
        assertTrue(missing.isEmpty(), "以下配置屏没写进 task/gui/README.md: " + missing);

        // 配置写入两条铁律必须在文档里 (它们是本轮两次事故的直接教训)
        assertTrue(doc.contains("cfgOrCreate"),
                "task/gui/README.md 必须写明: 写入配置必须用 MaidData.cfgOrCreate (cfg 是只读临时 tag → 静默丢写)");
        assertTrue(doc.contains("ACTION_REMOVE"),
                "task/gui/README.md 必须写明: 清除配置用 ACTION_REMOVE 才能回落全局 (写默认值 ≠ 回落)");
    }

    /**
     * **全目录 README 覆盖守护** (v79.63, 用户裁定: 每个包 README 必须记录本包每个类干什么/怎么连接)。
     *
     * <p>规则: 列出的每个目录, 其 README.md 必须**提到本目录直属的每一个类名**。
     * 新增类而忘写 README → 本用例变红 (防止"代码改了、注释没了、文档也没跟上")。
     * 子包各自负责 (如 task/pipeline/sense、task/service/harvest 各有一份 README)。
     */
    @Test
    void folderReadmesCoverTheirOwnClasses() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");

        // [目录, 中文名, 可选 "R" = 递归, 可选 第4位 = README 所在目录 (默认 = 目录本身;
        //  用于"子包无独立 README, 明细写在父 README"的情况)]
        String[][] pairs = {
                {"task/service", "服务"},
                {"adapter", "TLM 桥接"},
                {"defense", "防御塔"},
                {"config", "全局配置"},
                {"screen", "客户端屏"},
                {"task/data", "任务数据"},
                {"task/runtime", "任务运行态"},
                {"event", "游戏事件"},
                {"network", "网络包"},
                {"init", "注册与初始化"},
                {"api", "对外契约", "R"},
                {"task/api", "管线接口契约"},
                {"compat", "兼容层"},
                {"vanilla", "能力层"},
                {"storage", "数据表族"},
                {"compat/create", "Create 家族", "R"},
                {"vanilla/input", "io 读侧", "R"},
                {"vanilla/output", "io 写侧", "R"},
                // 子包明细写在父 README (无独立 README) ⇒ 用第 4 位指定 README 目录
                {"compat/ai", "AI 工具面", "R", "compat"},
                {"compat/ysm", "YSM 适配", "R", "compat"},
                {"compat/patpat", "PatPat 适配", "R", "compat"},
                {"compat/createbigcannons", "CBC 火炮", "R", "compat"},
                {"vanilla/cache", "区块缓存族", "R", "vanilla"},
                {"vanilla/execute", "跨 tick 协调器", "R", "vanilla"},
                {"vanilla/fakeplayer", "假玩家", "R", "vanilla"},
                // 最后一批 (小包 + 顶层)
                {"chatbubble", "气泡与表情"},
                {"ai", "AI 上下文", "R"},
                {"bauble", "饰品与牛奶", "R"},
                {"client", "客户端专用"},
                {"resource", "动态资源"},
                {"commands", "命令"},
                {"gametest", "游戏内测试"},
                {"task/sense", "环境感知层"},
                {"task/passive", "纯触发被动", "R"},
                {"task/quest", "任务/成就树"},
                {".", "根包"},
        };
        List<String> problems = new ArrayList<>();
        for (String[] pair : pairs) {
            Path dir = root.resolve(pair[0]);
            // README 位置: 默认 = 目录本身; 第 4 位可指定"明细写在父 README"
            Path readmeDir = pair.length > 3 ? root.resolve(pair[3]) : dir;
            Path readme = readmeDir.resolve("README.md");
            if (!readme.toFile().isFile()) {
                problems.add(pair[0] + "/README.md 不存在 (" + pair[1] + " 层必须有 README)");
                continue;
            }
            String doc = ArchSource.read(readme);
            List<Path> files = ("R".equals(pair.length > 2 ? pair[2] : ""))
                    ? ArchSource.javaFiles(dir)      // 递归: 子包无独立 README (如 compat/create, vanilla/input|output)
                    : directJavaFiles(dir);          // 直属: 子包各有自己的 README
            assertTrue(!files.isEmpty(), pair[0] + " 下无 java 文件 — 路径写错?");
            List<String> missing = new ArrayList<>();
            for (Path f : files) {
                String clsName = f.getFileName().toString().replace(".java", "");
                if ("package-info".equals(clsName)) continue;   // 包说明文件不需要逐条登记
                if (!doc.contains(clsName)) missing.add(clsName);
            }
            if (!missing.isEmpty()) problems.add(pair[0] + " 漏记: " + missing);

            // 铁律 §2.9 "改代码前必读该包 README" 的**可见性守护**:
            // 每份受守护 README 必须含"陷阱/注意/铁律"章节 — 否则"改前必读"无从谈起
            if (!(doc.contains("陷阱") || doc.contains("注意") || doc.contains("铁律"))) {
                problems.add(pair[0] + "/README.md 缺「已知陷阱/注意」章节 (铁律 §2.9: 改前必读需要可读的陷阱表)");
            }
        }
        assertTrue(problems.isEmpty(), "以下目录 README 未覆盖本目录类 (请补明细行: 职责/输入输出/连接/陷阱):\n  "
                + String.join("\n  ", problems));
    }
}

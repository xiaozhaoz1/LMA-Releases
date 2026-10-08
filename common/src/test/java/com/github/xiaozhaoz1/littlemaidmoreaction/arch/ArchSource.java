package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 架构守护测试基建 (v79.63 B2/B3) — 让"契约"从口头约定变成 CI 可见的红。
 *
 * <p><b>为什么用源码扫描而不是反射/运行时</b>: ① 守护的对象正是"源码里的 import 与字面量"本身;
 * ② 反射加载 {@code TaskRegistryManifest} 会连带初始化全部管线类 (重 MC 依赖, 违反"纯 JVM 单测"纪律);
 * ③ 扫描不依赖运行时状态, 与 gametest 互补。
 *
 * <p><b>源码根解析</b>: Gradle 测试工作目录 = 节点工程目录 (如 {@code forge/versions/1.20.1}),
 * 逐级向上找 {@code common/src/main/java}; 找不到时调用方用 {@code Assumptions} 跳过 (不外层炸)。
 */
final class ArchSource {

    /** 源码根候选 (从测试工作目录向上探) */
    static Path findMainRoot() {
        Path p = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && p != null; i++) {
            Path c = p.resolve("common/src/main/java");
            if (Files.isDirectory(c)) return c;
            p = p.getParent();
        }
        return null;
    }

    /** 主源码根下的包根 (com/github/xiaozhaoz1/littlemaidmoreaction) */
    static Path findPackageRoot() {
        Path main = findMainRoot();
        return main == null ? null : main.resolve("com/github/xiaozhaoz1/littlemaidmoreaction");
    }

    /** 递归收集 .java */
    static List<Path> javaFiles(Path root) {
        List<Path> out = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return out;
        try (var stream = Files.walk(root)) {
            stream.filter(f -> f.toString().endsWith(".java")).forEach(out::add);
        } catch (Exception e) {
            throw new IllegalStateException("扫描源码失败: " + root, e);
        }
        return out;
    }

    static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (Exception e) {
            throw new IllegalStateException("读源码失败: " + p, e);
        }
    }

    /** 相对包根的路径 (统一用 / 分隔, 便于断言输出可读) */
    static String rel(Path packageRoot, Path file) {
        return packageRoot.relativize(file).toString().replace('\\', '/');
    }

    /** 抓取某文件里的全部 import 全限定名 */
    static List<String> imports(String source) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("^import\\s+(?:static\\s+)?([A-Za-z0-9_.$]+)\\s*;", Pattern.MULTILINE).matcher(source);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** 抓取某文件里全部 {@code case "x" ->} 的字面量 */
    static List<String> switchCaseLiterals(String source) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("case\\s+\"([A-Za-z0-9_]+)\"\\s*->").matcher(source);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** 抓取某文件里 {@code new TaskSpec("x"…)} 的任务类型字面量 (去重保序) */
    static List<String> taskSpecLiterals(String source) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("new\\s+TaskSpec\\(\\s*\"([A-Za-z0-9_]+)\"").matcher(source);
        while (m.find()) if (!out.contains(m.group(1))) out.add(m.group(1));
        return out;
    }

    /**
     * 按方法名切出方法体 (首个 `{` 到配平 `}`) — 避免同名 switch 混入 (如 friendlyName vs stateName)。
     * 找不到方法返回空串 (调用方断言会以"缺全部"的形式报出来, 而不是静默通过)。
     */
    /**
     * 按方法名切出**方法体** — 用"参数表闭括号后紧跟 `{`"识别声明, 从而跳过同名调用点
     * (如 {@code friendlyName(taskType)} 在别的 public 方法里被调用, 且出现在声明之前)。
     * 找不到声明返回空串 — 调用方会以"全部缺项"报出来, 不会静默通过。
     */
    static String methodBody(String source, String methodName) {
        String needle = methodName + "(";
        int from = 0;
        while (true) {
            int sig = source.indexOf(needle, from);
            if (sig < 0) return "";
            from = sig + needle.length();
            // 跳过参数表 (含嵌套泛型括号)
            int depth = 1;
            int i = from;
            for (; i < source.length() && depth > 0; i++) {
                char c = source.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') depth--;
            }
            if (depth != 0) return "";
            int j = i;
            while (j < source.length() && Character.isWhitespace(source.charAt(j))) j++;
            if (j < source.length() && source.charAt(j) == '{') {   // 声明 (调用点后是 ; 或 ) )
                return sliceBraces(source, j);
            }
        }
    }

    /** 从 `{` 起做花括号配平切片 */
    private static String sliceBraces(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(open, i + 1);
            }
        }
        return source.substring(open);
    }
    private ArchSource() {}
}

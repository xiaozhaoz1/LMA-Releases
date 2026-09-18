package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管线步骤声明守护 (v79.63) — **方案 B: 只做展示面自身校验**。
 *
 * <p><b>为什么不校验"steps ⊆ 相位枚举"</b>: 错题 #291 已用证据推翻该方案 —
 * {@code steps()} 是**用户可见的粗粒度语义** ("装填弹药"), 管线状态枚举是**内部细粒度状态**
 * (含 {@code IDLE}/{@code EAT_RESET} 等非工作态); 二者**不同层**, 且本就多对一
 * (CannonLoad 6 态 → 4 步; MaidAssembly 5 态 → 1 步)。强行统一会把内部状态暴露给用户。
 *
 * <p><b>本测试保证</b>:
 * <ul>
 *   <li>声明了 {@code TaskStep} 的管线, 其 {@code steps()} 体必须可解析出至少一个步骤</li>
 *   <li>每个步骤 id / label **非空**</li>
 *   <li>(id, label) 对**唯一** (同一 id 出现在不同 Mode 分支是允许的 —— 如 ChainHarvest 的
 *       {@code search/寻找矿石} 与 {@code search/寻找树木}, 但完全相同的对 = 复制粘贴错)</li>
 * </ul>
 *
 * <p>同步义务写在每个 {@code steps()} 的注释里 ("改相位/状态时必须同步本步骤声明") —
 * 这是方案 B 的约定面; 机械校验只覆盖展示面自身。
 */
class PipelineStepsGuardTest {

    /** 步骤声明: new TaskStep("id", "label", ...) */
    private static final Pattern STEP = Pattern.compile(
            "new\\s+TaskStep\\(\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"");

    @Test
    void stepsDeclarationsAreWellFormed() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "找不到 common/src/main/java 包根 (非源码树环境)");

        List<String> problems = new ArrayList<>();
        int pipelines = 0, stepCount = 0;

        for (Path f : ArchSource.javaFiles(root)) {
            String src = ArchSource.read(f);
            if (!src.contains("new TaskStep(")) continue;      // 只关心真正声明步骤的文件
            String rel = ArchSource.rel(root, f);
            pipelines++;

            String body = ArchSource.methodBody(src, "steps");
            if (body == null || body.isBlank()) {
                problems.add(rel + ": 有 TaskStep 但取不到 steps() 方法体 — 声明形态变了?");
                continue;
            }
            Set<String> pairs = new LinkedHashSet<>();
            Matcher m = STEP.matcher(body);
            boolean any = false;
            while (m.find()) {
                any = true;
                stepCount++;
                String id = m.group(1), label = m.group(2);
                if (id.isBlank()) problems.add(rel + ": 步骤 id 为空");
                if (label.isBlank()) problems.add(rel + ": 步骤 label 为空 (id=" + id + ")");
                if (!pairs.add(id + '\u0000' + label)) {
                    problems.add(rel + ": 步骤 (id,label) 重复: (" + id + ", " + label + ")");
                }
            }
            if (!any) problems.add(rel + ": steps() 体内无可解析的 TaskStep(\"id\", \"label\", …)");
        }

        // 防"守护腐化": 扫描面若失效 (文件走法/正则失配), 用下限断言立刻暴露
        assertTrue(pipelines >= 12, "扫描到的管线数异常 (" + pipelines + ", 期望 ≥12) — ArchSource/正则失配?");
        assertTrue(stepCount >= 20, "扫描到的步骤数异常 (" + stepCount + ", 期望 ≥20)");
        assertTrue(problems.isEmpty(), "步骤声明问题:\n  " + String.join("\n  ", problems));
    }
}

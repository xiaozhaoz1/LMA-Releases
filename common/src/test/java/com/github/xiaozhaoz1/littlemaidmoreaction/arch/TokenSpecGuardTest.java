package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token 规格守护 (v79.73 — 用户需求固化, 防止后续改动把数值/配方改歪 ✓)。
 *
 * <p>守护三件事:
 * <ol>
 *   <li><b>效果数值</b>: 饱食 +2 · 回血 5 · 缓慢恢复 I 600t ✓ (用户裁定; 常量是编译期常量 ⇒ 本测试安全 ✓)</li>
 *   <li><b>配方</b>: 8 个**青金石**围一圈 (中间空) ⇒ 产出 **16** ✓; 且**双平台目录/格式都写对** ✓
 *       (1.20.1 = `data/.../recipes/` + `result.item` · 1.21.1 = `data/.../recipe/` + `result.id+count` ✓ —
 *       这正是本项目蛋糕配方踩过的"目录单复数 + result 格式"坑 ✓)</li>
 *   <li><b>资源三件套</b>: 贴图 / 模型 / 语言 key 都在位 ✓ (缺一个就是"紫黑方块"或"raw key" ✗)</li>
 * </ol>
 */
class TokenSpecGuardTest {

    private static Path repoRoot() {
        Path p = ArchSource.findMainRoot();
        for (int i = 0; i < 8 && p != null; i++) {
            if (Files.isRegularFile(p.resolve("docs").resolve("ARCHITECTURE.md"))) return p;
            p = p.getParent();
        }
        return null;
    }

    @Test
    @DisplayName("Token 效果数值 = 用户规格 (饱食+2 / 回血5 / 恢复I 600t)")
    void effectValuesMatchSpec() {
        assertEquals(2, com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenItem.HUNGER);
        assertEquals(5.0f, com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenItem.HEAL_AMOUNT);
        assertEquals(600, com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenItem.REGEN_TICKS);
    }

    @Test
    @DisplayName("配方: 8 青金石围一圈 ⇒ 16 个 token (双平台目录/格式都对)")
    void recipesAreShapedRingOfLapisWithCount16() throws IOException {
        Path repo = repoRoot();
        assertTrue(repo != null, "仓库根定位失败");
        // 平台各自的路径与 result 格式 (1.21 单数 + id/count; 1.20 复数 + item/count) ✓
        Path forge = repo.resolve("forge/src/main/resources/data/littlemaidmoreaction/recipes/token.json");
        Path neo = repo.resolve("neoforge/src/main/resources/data/littlemaidmoreaction/recipe/token.json");
        assertTrue(Files.isRegularFile(forge), "1.20.1 配方缺失 (recipes/ 复数): " + forge);
        assertTrue(Files.isRegularFile(neo), "1.21.1 配方缺失 (recipe/ 单数): " + neo);

        assertRecipe(forge, true);
        assertRecipe(neo, false);
    }

    private static void assertRecipe(Path file, boolean legacyFormat) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("minecraft:crafting_shaped", root.get("type").getAsString(), file.getFileName() + " type");
        // 图样: 8 个占位 + 1 个空 (3×3 中间空) ✓
        var pattern = root.getAsJsonArray("pattern");
        StringBuilder flat = new StringBuilder();
        for (var row : pattern) flat.append(row.getAsString());
        assertEquals(9, flat.length(), "图样必须 3×3 (9 格)");
        long hashCount = flat.chars().filter(c -> c == '#').count();
        assertEquals(8, hashCount, "青金石必须 8 个 (围一圈, 中间空)");
        assertEquals(' ', flat.charAt(4), "正中间必须是空的 (用户规格: 中间空)");
        // 材料 = 青金石 (1.20 是 {"item": ...}; 1.21 是纯字符串 ✓ 两种都接)
        JsonObject key = root.getAsJsonObject("key");
        String ingredient = key.getAsJsonObject("#") != null
                ? key.getAsJsonObject("#").get("item").getAsString()
                : key.get("#").getAsString();
        assertEquals("minecraft:lapis_lazuli", ingredient, "材料必须是青金石");
        // 产出 = 16 ✓
        JsonObject result = root.getAsJsonObject("result");
        String id = legacyFormat ? result.get("item").getAsString() : result.get("id").getAsString();
        assertEquals("littlemaidmoreaction:token", id, "产出必须是 token");
        assertEquals(16, result.get("count").getAsInt(), "每次合成必须产出 16 个 (用户规格)");
    }

    @Test
    @DisplayName("资源三件套 (贴图/模型/中英语言) 都在位")
    void assetsExist() {
        Path repo = repoRoot();
        assertTrue(repo != null, "仓库根定位失败");
        Path assets = repo.resolve("common/src/main/resources/assets/littlemaidmoreaction");
        assertTrue(Files.isRegularFile(assets.resolve("textures/item/token.png")), "缺贴图 token.png");
        assertTrue(Files.isRegularFile(assets.resolve("models/item/token.json")), "缺模型 token.json");
        for (String lang : new String[]{"zh_cn.json", "en_us.json"}) {
            Path f = assets.resolve("lang").resolve(lang);
            assertTrue(Files.isRegularFile(f), "缺语言文件 " + lang);
            try {
                String txt = Files.readString(f);
                assertTrue(txt.contains("item.littlemaidmoreaction.token"), lang + " 缺物品名 key");
                assertTrue(txt.contains("bubble.littlemaidmoreaction.token_feed"), lang + " 缺喂食气泡 key");
            } catch (IOException e) {
                throw new AssertionError(lang + " 读取失败", e);
            }
        }
    }
}

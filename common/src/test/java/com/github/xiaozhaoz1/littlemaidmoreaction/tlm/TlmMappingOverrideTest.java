package com.github.xiaozhaoz1.littlemaidmoreaction.tlm;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TLM 映射一致性冒烟测试 — LMA-MAIN 与 TLM 同 named 映射, 测试可直接覆写 TLM 类方法。
 *
 * <p>复刻 v67 错题集映射冲突场景 (Parchment/Mojang 不一致导致 stillValid/renderBg
 * 编译失败, 见 memory lma-v67-taskconfiggui-abstract #4/#8):
 * <ol>
 *   <li>继承 TLM {@link TaskConfigContainer} 并覆写 {@code stillValid} (错题 #4)</li>
 *   <li>实现 TLM 任务接口并覆写默认方法 (错题 #8 同族)</li>
 * </ol>
 * 编译通过即证明映射一致, 测试可直接继承/覆盖 TLM 类。
 *
 * <p><b>测试性质说明 (NEW-H2)</b>: 本测试的真实价值在<b>编译期</b> — 映射不一致时
 * 编译直接失败 (v67 错题 #4/#8 场景)。运行期断言仅做防退化守卫, 不重复编译期结论:
 * 旧版 {@code assertNotNull(getDeclaredMethods())} 恒真 (JLS 保证 getDeclaredMethods
 * 永非 null), 制造假绿 — 已改写为具体覆写方法存在性检查。
 */
class TlmMappingOverrideTest {

    /** 错题 #4 场景: 继承 TLM 容器类 + 覆写 stillValid — 映射不一致时编译器找不到 Parchment 编译的实现 */
    @SuppressWarnings("unused")
    static class TlmChildContainer extends TaskConfigContainer {
        TlmChildContainer(int id, Player player) {
            super(null, id, player.getInventory(), -1);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /** 错题 #8 同族: 实现 TLM 接口覆写默认方法 (IMaidTask.getUid 等 named 方法) */
    static class TlmTaskImpl implements com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask {
        @Override
        public net.minecraft.resources.ResourceLocation getUid() {
            return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("lma_test", "tlm_mapping");
        }

        @Override
        public net.minecraft.world.item.ItemStack getIcon() {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public net.minecraft.sounds.SoundEvent getAmbientSound(
                com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
            return null;
        }

        @Override
        public java.util.List<com.mojang.datafixers.util.Pair<Integer,
                net.minecraft.world.entity.ai.behavior.BehaviorControl<? super
                        com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid>>>
        createBrainTasks(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
            return com.google.common.collect.Lists.newArrayList();
        }
    }

    @Test
    @DisplayName("容器覆写编译冒烟: 覆写方法 stillValid(Player) 仍保留 (防删改假绿)")
    void tlmContainerOverrideCompiles() {
        // NEW-H2: 删恒真 assertNotNull(getDeclaredMethods()) — 改写为具体方法存在性检查
        // (编译期冒烟价值在类加载本身, 运行期断言只防"覆写被删/改名后仍绿"的假象)
        java.lang.reflect.Method[] methods = TlmChildContainer.class.getDeclaredMethods();
        boolean hasStillValidOverride = java.util.Arrays.stream(methods)
                .anyMatch(m -> "stillValid".equals(m.getName()) && m.getParameterCount() == 1);
        assertTrue(hasStillValidOverride, "TlmChildContainer 应保留覆写方法 stillValid(Player)");
    }

    @Test
    @DisplayName("任务接口覆写冒烟: getUid 返回固定 ID (named 映射运行期可用)")
    void tlmTaskOverrideCompiles() {
        TlmTaskImpl task = new TlmTaskImpl();
        assertEquals("tlm_mapping", task.getUid().getPath());
    }
}

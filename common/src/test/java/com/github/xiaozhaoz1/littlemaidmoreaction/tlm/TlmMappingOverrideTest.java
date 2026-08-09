package com.github.xiaozhaoz1.littlemaidmoreaction.tlm;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.world.entity.player.Player;
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
    void tlmContainerOverrideCompiles() {
        assertNotNull(TlmChildContainer.class.getDeclaredMethods());
    }

    @Test
    void tlmTaskOverrideCompiles() {
        TlmTaskImpl task = new TlmTaskImpl();
        assertEquals("tlm_mapping", task.getUid().getPath());
    }
}

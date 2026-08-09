package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaFlowCoordinationBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 便携装配 IMaidTask.
 * <p>WorkEatBehavior / NearbyCollectBehavior 由 {@code DefaultBehaviorBrain} 自动注入.
 */
public final class MaidAssemblyTask implements IMaidTask {

    public static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "task/maid_assembly");

    private static final MaidAssemblyTask INSTANCE = new MaidAssemblyTask();
    public static MaidAssemblyTask get() { return INSTANCE; }
    private MaidAssemblyTask() {}

    @Override public ResourceLocation getUid() { return UID; }
    @Override public ItemStack getIcon() { return new ItemStack(Items.CRAFTING_TABLE); }
    @Nullable @Override public SoundEvent getAmbientSound(EntityMaid maid) { return null; }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return new ArrayList<>(List.of(Pair.of(5, new LmaFlowCoordinationBehavior())));
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return new ArrayList<>(List.of(Pair.of(5, new LmaFlowCoordinationBehavior())));
    }

    @Override public boolean enableLookAndRandomWalk(EntityMaid maid) { return false; }
    @Override public boolean enablePanic(EntityMaid maid) { return false; }
    @Override public boolean enableEating(EntityMaid maid) { return true; }
    @Override public boolean isEnable(EntityMaid maid) { return isCreateLoaded(); }

    /** v75.1: create 运行时门控双平台 */
    private static boolean isCreateLoaded() {
//? if 1.20.1 {
        return net.minecraftforge.fml.ModList.get().isLoaded("create");
//?} else {
        return net.neoforged.fml.ModList.get().isLoaded("create");
//?}
    }
    @Override public boolean isHidden(EntityMaid maid) { return false; }
    @Override public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) { return FunctionCallSwitchResult.OK; }
    @Override public String getMaidActionSummary() { return "执行便携装配任务"; }

    /** v67.16: UID 路径带 "task/" 前缀, 默认 getName 生成 key 不匹配 lang — 显式覆写 */
    @Override
    public net.minecraft.network.chat.MutableComponent getName() {
        return net.minecraft.network.chat.Component.translatable(
                "task." + LittleMaidMoreAction.MOD_ID + ".maid_assembly");
    }

    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        // v67.12: 按任务类型直查 — 避免任务刚选中 lma_flow_task 未初始化时误回退默认屏
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory.forTask(maid, "maid_assembly");
    }
}
